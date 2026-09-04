import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 라우트는 각 기능 브랜치가 자기 화면을 여기에 추가합니다.
 * 어느 브랜치가 무엇을 추가하는지는 docs/collab/협업-시나리오.md 「라우트 분담」 을 보세요.
 *
 * 뼈대에 전부 적어두면 아직 없는 뷰를 import 해서 빌드가 깨집니다.
 */
const routes = [
  { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
  { path: '/signup', name: 'signup', component: () => import('@/views/SignupView.vue') },
  {
    path: '/mypage',
    name: 'mypage',
    component: () => import('@/views/MyPageView.vue'),
    meta: { requiresAuth: true },
  },

  // 화면 08 커뮤니티
  { path: '/community', name: 'community', component: () => import('@/views/CommunityView.vue') },
  {
    path: '/community/write',
    name: 'post-write',
    component: () => import('@/views/PostWriteView.vue'),
    meta: { requiresAuth: true },
  },
  // :id 는 /write 뒤에 둬야 합니다. 먼저 두면 write 가 id 로 잡힙니다.
  { path: '/community/:id', name: 'post-detail', component: () => import('@/views/PostDetailView.vue') },

  // 화면 14 대출예측 (개인 조건을 쓰므로 로그인 필요)
  {
    path: '/loan-prediction',
    name: 'loan-prediction',
    component: () => import('@/views/LoanPredictionView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/loan-prediction/record',
    name: 'loan-record',
    component: () => import('@/views/LoanRecordView.vue'),
    meta: { requiresAuth: true },
  },

// 화면 06 정책리스트 · 07 정책상세
  { path: '/policies', name: 'policies', component: () => import('@/views/PolicyListView.vue') },
  { path: '/policies/:id', name: 'policy-detail', component: () => import('@/views/PolicyDetailView.vue') },

  // 「정보」 하위 — 주거 뉴스
  { path: '/news', name: 'news', component: () => import('@/views/NewsView.vue') },

  // 화면 09 매물 랜딩 · 10 공공임대
  { path: '/properties', name: 'properties', component: () => import('@/views/PropertiesView.vue') },
  {
    path: '/properties/public-housing',
    name: 'public-housing',
    component: () => import('@/views/PublicHousingView.vue'),
  },

  // 화면 11 실거래가 지도
  { path: '/transactions', name: 'transactions', component: () => import('@/views/TransactionsView.vue') },

  // 화면 15 계약서 검사 (업로드한 문서를 다루므로 로그인 필요)
  {
    path: '/contract-check',
    name: 'contract-check',
    component: () => import('@/views/ContractCheckUploadView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/contract-check/result',
    name: 'contract-check-result',
    component: () => import('@/views/ContractCheckResultView.vue'),
    meta: { requiresAuth: true },
  },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true

  const auth = useAuthStore()
  const ready = await auth.ensureSession()
  return ready ? true : { name: 'login', query: { redirect: to.fullPath } }
})

export default router
