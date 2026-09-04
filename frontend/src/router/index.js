import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 라우트는 각 기능 브랜치가 자기 화면을 여기에 추가합니다.
 * 어느 브랜치가 무엇을 추가하는지는 docs/collab/협업-시나리오.md 「라우트 분담」 을 보세요.
 *
 * 뼈대에 전부 적어두면 아직 없는 뷰를 import 해서 빌드가 깨집니다.
 */
const routes = []

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true

  const auth = useAuthStore()
  const ready = await auth.ensureSession()
  return ready ? true : { name: 'login', query: { redirect: to.fullPath } }
})

export default router
