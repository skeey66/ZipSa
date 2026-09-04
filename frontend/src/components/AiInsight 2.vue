<script setup>
import { ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  // 인사이트를 가져오는 함수. 정책/뉴스가 각자 다른 API 를 씁니다.
  loader: { type: Function, required: true },
  targetId: { type: [Number, String], default: null },
})

const auth = useAuthStore()
const data = ref(null)
const loading = ref(false)
const failed = ref(false)

const TONE = {
  good:    { bg: '#f0f9f3', border: '#bfe3cc', text: '#1e7a45', icon: '✅' },
  check:   { bg: '#f7f4fd', border: '#ded4f5', text: '#5b46a8', icon: '🧭' },
  caution: { bg: '#fff8f0', border: '#f0dcc0', text: '#a86a1e', icon: '⚠️' },
}
const tone = (key) => TONE[key] ?? TONE.check

watch(() => props.targetId, async (id) => {
  data.value = null
  failed.value = false
  if (!id || !auth.isLoggedIn) return
  loading.value = true
  try {
    data.value = await props.loader(id)
  } catch {
    // AI 는 보조 기능입니다. 실패해도 본문 읽기를 막지 않습니다.
    failed.value = true
  } finally {
    loading.value = false
  }
}, { immediate: true })
</script>

<template>
  <!-- 비로그인: 로그인해야 개인 맞춤 분석이 나온다는 것만 안내 -->
  <section v-if="!auth.isLoggedIn" class="locked">
    <h3>🧭 나에게 어떻게 적용되나</h3>
    <p>로그인하면 내 나이대·직업·소득 조건에 맞춰 분석해 드립니다.</p>
    <RouterLink to="/login" class="go">로그인하기</RouterLink>
  </section>

  <template v-else-if="loading">
    <section class="skeleton"><span /><span /><span /></section>
  </template>

  <template v-else-if="data">
    <!-- AI 요약 -->
    <section class="card summary">
      <h3>
        <span aria-hidden="true">✨</span>AI 요약
        <span class="badge" :class="{ real: data.aiGenerated }">
          {{ data.aiGenerated ? 'AI' : '샘플' }}
        </span>
      </h3>
      <ol>
        <li v-for="(line, i) in data.summary" :key="i">{{ line }}</li>
      </ol>
    </section>

    <!-- 나에게 어떻게 적용되나 -->
    <section
      class="card apply"
      :style="{
        background: tone(data.application.tone).bg,
        borderColor: tone(data.application.tone).border,
      }"
    >
      <h3>
        <span aria-hidden="true">🧭</span>나에게 어떻게 적용되나
        <span class="badge" :class="{ real: data.aiGenerated }">
          {{ data.aiGenerated ? 'AI' : '샘플' }}
        </span>
      </h3>

      <p class="verdict" :style="{ color: tone(data.application.tone).text }">
        {{ tone(data.application.tone).icon }} {{ data.application.verdict }}
      </p>

      <ul class="reasons">
        <li v-for="(r, i) in data.application.reasons" :key="i">{{ r }}</li>
      </ul>

      <div v-if="data.application.nextSteps.length" class="steps">
        <h4>지금 할 일</h4>
        <ul>
          <li v-for="(s, i) in data.application.nextSteps" :key="i">{{ s }}</li>
        </ul>
      </div>
    </section>
  </template>

  <p v-else-if="failed" class="failed">분석을 불러오지 못했습니다. 본문은 그대로 확인하실 수 있습니다.</p>
</template>

<style scoped>
.card { margin-top: 20px; padding: 18px 20px; border: 1px solid #e6e8ec; border-radius: 12px; }
.card h3 {
  display: flex; align-items: center; gap: 7px;
  font-size: 14px; font-weight: 700; color: #33415c; margin-bottom: 12px;
}
.badge {
  margin-left: auto; font-size: 10px; padding: 2px 7px; border-radius: 9px;
  background: #eceef2; color: #8a8f98; font-weight: 600;
}
.badge.real { background: var(--primary-soft); color: var(--primary); }

.summary { background: #fbfcfe; }
.summary ol { padding-left: 18px; margin: 0; }
.summary li { font-size: 14px; line-height: 1.75; color: #3d4148; margin-bottom: 6px; }

.verdict { font-size: 15px; font-weight: 700; margin-bottom: 12px; }
.reasons { list-style: none; padding: 0; }
.reasons li {
  position: relative; padding-left: 14px; margin-bottom: 6px;
  font-size: 13.5px; line-height: 1.7; color: #46505e;
}
.reasons li::before {
  content: ''; position: absolute; left: 2px; top: 10px;
  width: 4px; height: 4px; border-radius: 50%; background: currentColor; opacity: .4;
}
.steps { margin-top: 14px; padding-top: 12px; border-top: 1px dashed rgba(0,0,0,.12); }
.steps h4 { font-size: 12.5px; font-weight: 700; color: #55606e; margin-bottom: 6px; }
.steps ul { list-style: none; padding: 0; }
.steps li {
  position: relative; padding-left: 16px; margin-bottom: 5px;
  font-size: 13.5px; line-height: 1.7; color: #46505e;
}
.steps li::before { content: '→'; position: absolute; left: 0; opacity: .5; }

.locked {
  margin-top: 20px; padding: 20px; border: 1px dashed #cfd8e6;
  border-radius: 12px; background: #fbfcfe; text-align: center;
}
.locked h3 { font-size: 14px; font-weight: 700; color: #33415c; }
.locked p { margin: 7px 0 12px; font-size: 13px; color: #6b7079; }
.go {
  display: inline-block; padding: 8px 18px; border-radius: 7px;
  background: var(--primary); color: #fff; font-size: 13px; font-weight: 600;
}
.go:hover { color: #fff; }

.skeleton { margin-top: 20px; }
.skeleton span {
  display: block; height: 13px; border-radius: 4px; margin-bottom: 9px;
  background: linear-gradient(90deg, #f0f1f4 25%, #e6e8ec 50%, #f0f1f4 75%);
  background-size: 200% 100%; animation: shine 1.2s infinite;
}
.skeleton span:nth-child(2) { width: 85%; }
.skeleton span:nth-child(3) { width: 60%; }
@keyframes shine { from { background-position: 200% 0 } to { background-position: -200% 0 } }

.failed { margin-top: 18px; font-size: 13px; color: #9aa0a8; }
</style>
