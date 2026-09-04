import client from './client'

/** 오퍼레이션 36 — 지역 목록 (화면 11 좌측 필터) */
export const fetchRegions = () => client.get('/api/regions')

/** 오퍼레이션 37 — 지도 마커. 아파트 단위로 집계된 값이 온다. */
export const fetchMarkers = (regionCode, dealType, months = 12) =>
  client.get('/api/transactions/map', { params: { regionCode, dealType, months } })

/**
 * 오퍼레이션 38 — 실거래 목록.
 *   aptName — 마커 클릭. 완전일치.
 *   keyword — 검색창. 부분일치. 서버에서 찾아야 페이지 밖의 단지도 걸린다.
 */
export const fetchTransactions = (regionCode, dealType, { aptName, keyword } = {}, page = 0, size = 50) =>
  client.get('/api/transactions', {
    params: { regionCode, dealType, aptName, keyword, page, size },
  })
