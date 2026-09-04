import client from './client'

/** 오퍼레이션 1 — 회원가입 */
export const signUp = (body) => client.post('/api/auth/signup', body)

/** 오퍼레이션 2 — 아이디 중복확인 */
export const checkLoginId = (loginId) =>
  client.get('/api/auth/check-id', { params: { loginId } })

/** 오퍼레이션 3 — 로그인 */
export const login = (loginId, password) =>
  client.post('/api/auth/login', { loginId, password })

/** 오퍼레이션 4 — 토큰 재발급 */
export const reissue = (refreshToken) =>
  client.post('/api/auth/reissue', { refreshToken })

/** 오퍼레이션 5 — 로그아웃 */
export const logout = () => client.post('/api/auth/logout')

/** 오퍼레이션 6 — 내 정보 조회 */
export const fetchMe = () => client.get('/api/users/me')

/** 오퍼레이션 7 — 내 정보 수정 */
export const updateMe = (body) => client.patch('/api/users/me', body)
