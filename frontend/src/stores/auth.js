import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import { setAccessToken, setUnauthorizedHandler, hasAccessToken } from '@/api/client'

const REFRESH_KEY = 'zipsa.refreshToken'

export const useAuthStore = defineStore('auth', () => {
  const nickname = ref(null)
  const profile = ref(null)
  // Access Token 은 메모리에만 둡니다. localStorage 에 두면 XSS 로 그대로 탈취됩니다.
  const hasSession = ref(Boolean(localStorage.getItem(REFRESH_KEY)))

  const isLoggedIn = computed(() => hasSession.value)

  setUnauthorizedHandler(() => clearSession())

  function clearSession() {
    setAccessToken(null)
    localStorage.removeItem(REFRESH_KEY)
    nickname.value = null
    profile.value = null
    hasSession.value = false
  }

  async function login(loginId, password) {
    const tokens = await authApi.login(loginId, password)
    setAccessToken(tokens.accessToken)
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken)
    nickname.value = tokens.nickname
    hasSession.value = true
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      clearSession()
    }
  }

  /** 새로고침 후 Access Token 이 사라졌을 때 Refresh Token 으로 세션을 복구한다. */
  async function restore() {
    const refreshToken = localStorage.getItem(REFRESH_KEY)
    if (!refreshToken) return false
    try {
      const tokens = await authApi.reissue(refreshToken)
      setAccessToken(tokens.accessToken)
      localStorage.setItem(REFRESH_KEY, tokens.refreshToken)
      hasSession.value = true
      return true
    } catch {
      clearSession()
      return false
    }
  }

  async function loadProfile() {
    profile.value = await authApi.fetchMe()
    nickname.value = profile.value.nickname
    return profile.value
  }

  /**
   * 보호된 화면에 들어가기 전에 호출한다.
   * access token 은 메모리에만 있어서 새로고침하면 사라지므로,
   * "refresh token 이 있다"만으로 통과시키면 API 가 401 을 맞는다.
   */
  async function ensureSession() {
    if (hasAccessToken()) return true
    return restore()
  }

  /**
   * 프로필 부분 수정(PATCH). 응답을 그대로 스토어에 반영해서
   * 화면이 서버 값과 어긋나지 않게 합니다(낙관적 갱신을 쓰지 않는 이유).
   */
  async function updateProfile(patch) {
    const updated = await authApi.updateMe(patch)
    profile.value = { ...profile.value, ...updated }
    if (updated.nickname) nickname.value = updated.nickname
    return profile.value
  }

  return {
    nickname, profile, isLoggedIn, updateProfile,
    login, logout, restore, ensureSession, loadProfile, clearSession,
  }
})
