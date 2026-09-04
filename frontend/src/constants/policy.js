/** 정책 분류. 값은 백엔드 PolicyCategory enum 과 1:1. */
export const POLICY_CATEGORIES = [
  { value: null, label: '전체' },
  { value: 'HOUSING', label: '주거지원' },
  { value: 'LOAN', label: '대출·이자' },
  { value: 'PUBLIC_HOUSING', label: '공공임대' },
  { value: 'SUPPLY', label: '주택공급' },
]

export const CATEGORY_COLOR = {
  HOUSING: '#1f6feb',
  LOAN: '#c2620e',
  PUBLIC_HOUSING: '#1a7f37',
  SUPPLY: '#6b46c1',
}
