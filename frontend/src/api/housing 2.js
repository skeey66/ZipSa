import client from './client'

/** HOUSING-001 — 모집 공고 목록 */
export const fetchNotices = ({ region, housingType, keyword, recruitStatus, page = 0, size = 20 } = {}) =>
  client.get('/api/public-housings', {
    params: { region, housingType, keyword, recruitStatus, page, size },
  })

/** 화면 10 캘린더 — 한 달치 공고. month 는 'YYYY-MM' */
export const fetchCalendar = (month) =>
  client.get('/api/public-housings/calendar', { params: { month } })

/** 지도 마커 — 단지 단위 집계 */
export const fetchComplexMarkers = (regionCode, housingType) =>
  client.get('/api/public-housings/map', { params: { regionCode, housingType } })

/** HOUSING-002 — 단지 상세(평형별 임대조건) */
export const fetchComplexUnits = (complexNo) =>
  client.get(`/api/public-housings/complexes/${complexNo}`)
