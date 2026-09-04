<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const loginId = ref('')
const password = ref('')
const errorMessage = ref('')
const submitting = ref(false)

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

async function onSubmit() {
  errorMessage.value = ''
  submitting.value = true
  try {
    await auth.login(loginId.value, password.value)
    router.push(route.query.redirect ?? '/')
  } catch (e) {
    errorMessage.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <!-- 와이어프레임 02_로그인 -->
  <div style="max-width:340px;margin:80px auto">
    <h1 style="text-align:center;margin-bottom:40px">로그인</h1>

    <form @submit.prevent="onSubmit">
      <input v-model="loginId" class="field" type="text" placeholder="아이디" autocomplete="username" />
      <input v-model="password" class="field" type="password" placeholder="비밀번호"
             autocomplete="current-password" />

      <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

      <button class="btn" type="submit" :disabled="submitting || !loginId || !password">
        {{ submitting ? '로그인 중…' : '로그인' }}
      </button>
    </form>

    <p class="muted" style="text-align:center;margin-top:20px">
      아직 계정이 없으신가요?
      <RouterLink to="/signup">회원가입</RouterLink>
    </p>
  </div>
</template>
