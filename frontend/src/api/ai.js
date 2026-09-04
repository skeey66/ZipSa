import client from './client'

/** 정책 상세 — AI 요약 + 내 적용. 로그인 필요. */
export const fetchPolicyInsight = (policyId) => client.get(`/api/ai/policies/${policyId}`)

/** 뉴스 상세 — AI 요약 + 내 적용. 로그인 필요. */
export const fetchNewsInsight = (newsId) => client.get(`/api/ai/news/${newsId}`)
