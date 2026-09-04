/** 공급 유형. 값은 백엔드 HousingType enum 과 1:1. */
export const HOUSING_TYPES = [
  { value: null, label: '전체' },
  { value: 'HAPPY_HOUSE', label: '행복주택' },
  { value: 'NATIONAL_RENTAL', label: '국민임대' },
  { value: 'PERMANENT_RENTAL', label: '영구임대' },
  { value: 'PURCHASE_RENTAL', label: '매입임대' },
  { value: 'INTEGRATED_RENTAL', label: '통합공공임대' },
  { value: 'JEONSE_RENTAL', label: '전세임대' },
]

export const RECRUIT_STATUS = [
  { value: null, label: '전체' },
  { value: 'OPEN', label: '모집중' },
  { value: 'UPCOMING', label: '예정' },
  { value: 'CLOSED', label: '마감' },
]

export const STATUS_LABEL = { OPEN: '모집중', UPCOMING: '예정', CLOSED: '마감' }

/** 원 단위 금액을 "6,696만" / "1억 2,000만" 으로. 마이홈 API 는 원 단위로 준다. */
export function wonToKor(won) {
  if (won == null) return '-'
  const man = Math.round(won / 10000)
  if (man === 0) return '없음'
  const eok = Math.floor(man / 10000)
  const rest = man % 10000
  if (eok && rest) return `${eok}억 ${rest.toLocaleString()}만`
  if (eok) return `${eok}억`
  return `${rest.toLocaleString()}만`
}

/** 범위 표기. 최소·최대가 같으면 하나만 보여준다. */
export function range(min, max, fmt = wonToKor) {
  if (min == null && max == null) return '-'
  if (min === max) return fmt(min)
  return `${fmt(min)} ~ ${fmt(max)}`
}
