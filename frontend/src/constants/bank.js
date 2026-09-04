/** 은행 코드. 백엔드 BankCode enum, 뱃지 파일명(/badges/{code}.png)과 1:1입니다. */
export const BANKS = [
  { code: 'KB', name: 'KB국민은행', color: '#f2c94c' },
  { code: 'WOORI', name: '우리은행', color: '#7aa5f0' },
  { code: 'NH', name: 'NH농협은행', color: '#6cbf84' },
  { code: 'HANA', name: '하나은행', color: '#9b87e8' },
]

export const BANK_NAME = Object.fromEntries(BANKS.map((b) => [b.code, b.name]))
/** 이름 → 코드. 백엔드는 은행명("KB국민은행")으로 내려주는데 뱃지 파일명은 코드라서 필요합니다. */
export const BANK_CODE = Object.fromEntries(BANKS.map((b) => [b.name, b.code]))
/** 커뮤니티 글 옆 작은 원형 뱃지. 흰 배경이 있는 96px 이미지입니다. */
export const badgeSrc = (code) => `/badges/${code}.png`
/** 대출예측 카드 배경에 옅게 까는 은행 심볼. 배경이 투명한 원본 로고입니다. */
export const logoSrc = (code) => `/banks/${code}.png`
