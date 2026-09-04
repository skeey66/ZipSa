import client from './client'

/** POLICY-001 — 목록/검색/필터 */
export const fetchPolicies = ({ keyword, region, category, openOnly = false, page = 0, size = 20 } = {}) =>
  client.get('/api/policies', { params: { keyword, region, category, openOnly, page, size } })

/** POLICY-002 — 상세 */
export const fetchPolicy = (policyId) => client.get(`/api/policies/${policyId}`)

/** POLICY-004 — 맞춤 정책. 로그인 필요. */
export const fetchRecommended = (size = 10) =>
  client.get('/api/policies/recommend', { params: { size } })
