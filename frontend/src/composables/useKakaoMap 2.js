/**
 * 카카오 지도 SDK 로더.
 *
 * SDK 는 전역 스크립트라 여러 컴포넌트가 각자 로드하면 중복으로 붙습니다.
 * 로딩 Promise 를 모듈 스코프에 한 번만 만들어 재사용합니다.
 */

const KEY = import.meta.env.VITE_KAKAO_MAP_KEY
let loading = null

export function loadKakaoMap() {
  if (window.kakao?.maps) return Promise.resolve(window.kakao)
  if (loading) return loading

  if (!KEY) {
    return Promise.reject(
      new Error('VITE_KAKAO_MAP_KEY 가 없습니다. 리포 루트 .env 를 확인하세요.'),
    )
  }

  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    // autoload=false 로 받고 kakao.maps.load 로 초기화해야 SDK 준비 완료 시점을 알 수 있습니다.
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KEY}&autoload=false`
    script.async = true
    script.onload = () => window.kakao.maps.load(() => resolve(window.kakao))
    // 카카오는 등록되지 않은 도메인에 401 을 주는데, 그 응답 본문은 스크립트가 아니라
    // JSON 이라 브라우저에는 그냥 onerror 로만 보입니다. 원인을 직접 알려줍니다.
    script.onerror = () =>
      reject(
        new Error(
          `developers.kakao.com 에서 이 주소를 등록해야 합니다.\n` +
            `내 애플리케이션 > 앱 설정 > 플랫폼 > Web > 사이트 도메인에 아래를 추가하세요.\n\n` +
            `${window.location.origin}\n\n` +
            `(등록 후 새로고침하면 바로 반영됩니다)`,
        ),
      )
    document.head.appendChild(script)
  })
  return loading
}
