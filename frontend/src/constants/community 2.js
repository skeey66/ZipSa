/** 08 화면 좌측 카테고리. 제목 앞 뱃지로도 쓰입니다. */
export const POST_CATEGORIES = [
  { value: null, label: '전체' },
  { value: 'FREE', label: '자유' },
  { value: 'INFO', label: '정보' },
  { value: 'QUESTION', label: '질문' },
  { value: 'LOAN', label: '대출' },
]

export const CATEGORY_LABEL = {
  FREE: '자유',
  INFO: '정보',
  QUESTION: '질문',
  LOAN: '대출',
}

/** 오늘이면 시각, 아니면 날짜. 목록이 빽빽해서 짧을수록 읽기 쉽습니다. */
export function shortDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  const p = (n) => String(n).padStart(2, '0')
  return sameDay
    ? `${p(d.getHours())}:${p(d.getMinutes())}`
    : `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())}`
}
