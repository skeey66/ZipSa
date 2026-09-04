import axios from 'axios'

/**
 * 모든 API 호출은 이 클라이언트를 거칩니다. 컴포넌트에서 axios 를 직접 부르지 마세요.
 * 서버 응답은 항상 { success, data, error } 봉투로 옵니다(REST API 명세서 v4 §1.1).
 * 인터셉터가 봉투를 벗겨서 data 만 돌려주므로, 호출하는 쪽은 봉투를 몰라도 됩니다.
 */
/**
 * 재발급 전용 인스턴스. 인터셉터를 달지 않아 401 → 재발급 → 401 무한루프를 원천 차단하고,
 * client.js 와 auth.js 사이의 순환 의존도 생기지 않습니다.
 */
const refreshClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 15000,
})

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 15000,
})

let accessToken = null
let onUnauthorized = null

export function setAccessToken(token) {
  accessToken = token
}

/** 메모리에 access token 이 있는지. 새로고침하면 사라지므로 라우터 가드가 이걸로 판단한다. */
export function hasAccessToken() {
  return Boolean(accessToken)
}

/** 재발급까지 실패했을 때 호출할 콜백(보통 로그아웃 처리) */
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

client.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

client.interceptors.response.use(
  // 204(No Content)는 본문이 없어 response.data 가 빈 문자열로 옵니다.
  (response) => (response.data ? response.data.data : null),
  async (error) => {
    const res = error.response

    // 401 이면 재발급을 한 번만 시도하고 원래 요청을 재시도한다.
    // errorCode 로 구분하지 않는 이유: 토큰을 아예 안 보낸 경우 서버는
    // TOKEN_EXPIRED 가 아니라 INVALID_TOKEN 을 준다.
    const refreshToken = localStorage.getItem('zipsa.refreshToken')
    if (res?.status === 401 && refreshToken && !error.config._retried) {
      error.config._retried = true
      try {
        const { data } = await refreshClient.post('/api/auth/reissue', { refreshToken })
        setAccessToken(data.data.accessToken)
        localStorage.setItem('zipsa.refreshToken', data.data.refreshToken)
        return client(error.config)
      } catch {
        onUnauthorized?.()
      }
    }

    if (res?.status === 401) onUnauthorized?.()

    // 서버가 준 사람이 읽을 수 있는 메시지를 그대로 올려서 화면이 표시하게 한다.
    return Promise.reject(new ApiError(
      res?.data?.error?.message ?? '요청을 처리하지 못했습니다.',
      res?.data?.error?.code,
      res?.status,
    ))
  },
)

export class ApiError extends Error {
  constructor(message, errorCode, status) {
    super(message)
    this.name = 'ApiError'
    this.errorCode = errorCode
    this.status = status
  }
}

export default client
