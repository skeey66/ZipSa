import client from './client'

/** NEWS-001 — 뉴스 목록 */
export const fetchNews = ({ keyword, page = 0, size = 20 } = {}) =>
  client.get('/api/news', { params: { keyword, page, size } })

/** NEWS-002 — 뉴스 상세 */
export const fetchNewsDetail = (newsId) => client.get(`/api/news/${newsId}`)
