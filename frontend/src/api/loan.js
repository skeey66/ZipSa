import client from './client'

/** 화면 14 — 은행별 한도 분포 + 내 예상 한도 + 분석레포트. 로그인 필요. */
export const fetchLoanPrediction = () => client.get('/api/loans/prediction')

/** 막대 클릭 — 그 은행·금액대에서 승인받은 회원들의 조건 */
export const fetchLoanSamples = (bank, bucket) =>
  client.get('/api/loans/prediction/samples', { params: { bank, bucket } })

/** LOAN-005 — 내 대출 결과 등록 */
export const recordLoan = (body) => client.post('/api/loans/actual', body)

/** LOAN-002 — 내가 등록한 대출 */
export const fetchMyLoans = () => client.get('/api/loans/me')

export const deleteLoan = (loanId) => client.delete(`/api/loans/actual/${loanId}`)
