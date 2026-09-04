import client from './client'

/** 오퍼레이션 21 — 게시글 목록 */
export const fetchPosts = ({ category, keyword, page = 0, size = 20 } = {}) =>
  client.get('/api/posts', { params: { category, keyword, page, size } })

/** 01 메인 인기글 */
export const fetchPopularPosts = (size = 5) =>
  client.get('/api/posts/popular', { params: { size } })

/** 오퍼레이션 22 — 상세 */
export const fetchPost = (postId) => client.get(`/api/posts/${postId}`)

/** 오퍼레이션 23 · 24 · 25 */
export const createPost = (body) => client.post('/api/posts', body)
export const updatePost = (postId, body) => client.patch(`/api/posts/${postId}`, body)
export const deletePost = (postId) => client.delete(`/api/posts/${postId}`)

/** 오퍼레이션 26 — 좋아요 토글 */
export const toggleLike = (postId) => client.post(`/api/posts/${postId}/likes`)

/** 오퍼레이션 27 · 28 — 댓글 */
export const createComment = (postId, content) =>
  client.post(`/api/posts/${postId}/comments`, { content })
export const deleteComment = (commentId) => client.delete(`/api/posts/comments/${commentId}`)
