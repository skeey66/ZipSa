<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { fetchPolicy } from '@/api/policy'
import { CATEGORY_COLOR } from '@/constants/policy'

const route = useRoute()
const policy = ref(null)
const error = ref('')

onMounted(async () => {
  try {
    policy.value = await fetchPolicy(route.params.id)
  } catch (e) {
    error.value = e.message ?? '정책을 불러오지 못했습니다.'
  }
})
</script>

<template>
  <section class="page" v-if="policy">
    <nav class="crumb">
      <RouterLink to="/policies">청년 정책</RouterLink> <span>&gt;</span> 상세
    </nav>

    <div class="head">
      <span class="cat" :style="{ color: CATEGORY_COLOR[policy.category] }">{{ policy.categoryName }}</span>
      <span v-if="policy.open && policy.dDay !== null" class="dday">D-{{ policy.dDay }}</span>
      <span v-else-if="!policy.open" class="closed">신청 마감</span>
    </div>
    <h1>{{ policy.title }}</h1>

    <dl class="facts">
      <div v-if="policy.issuer"><dt>주관</dt><dd>{{ policy.issuer }}</dd></div>
      <div v-if="policy.region"><dt>지역</dt><dd>{{ policy.region }}</dd></div>
      <div v-if="policy.targetAgeRange"><dt>지원 나이</dt><dd>{{ policy.targetAgeRange }}</dd></div>
      <div v-if="policy.targetJob"><dt>취업 상태</dt><dd>{{ policy.targetJob }}</dd></div>
      <div v-if="policy.targetSalaryRange"><dt>소득 조건</dt><dd>{{ policy.targetSalaryRange }}</dd></div>
      <div v-if="policy.applyEndDate">
        <dt>신청 기간</dt>
        <dd>{{ policy.applyStartDate || '상시' }} ~ {{ policy.applyEndDate }}</dd>
      </div>
    </dl>

    <!--
      AI 요약(POLICY-005) 자리입니다. ANTHROPIC_API_KEY 가 없어 아직 붙이지 않았습니다.
      요약이 실패해도 이 아래 원문은 그대로 보여야 합니다.
    -->

    <section v-if="policy.content" class="body">
      <h2>정책 내용</h2>
      <p>{{ policy.content }}</p>
    </section>

    <section v-if="policy.applyMethod" class="body">
      <h2>신청 방법</h2>
      <p>{{ policy.applyMethod }}</p>
    </section>

    <a class="cta" :href="policy.sourceUrl" target="_blank" rel="noopener noreferrer">
      {{ policy.sourceName || '원문' }}에서 자세히 보기 ↗
    </a>
  </section>

  <p v-else-if="error" class="error">{{ error }}</p>
</template>

<style scoped>
.crumb { font-size: 13px; color: #8a8f98; margin-bottom: 14px; }
.crumb span { margin: 0 4px; }
.crumb a:hover { color: var(--primary); }

.head { display: flex; align-items: center; gap: 10px; font-size: 13px; margin-bottom: 8px; }
.cat { font-weight: 600; }
.dday { font-weight: 700; color: #d02f2f; }
.closed { color: #b0b5bd; }
h1 { font-size: 26px; font-weight: 700; line-height: 1.4; margin-bottom: 22px; }

.facts { border: 1px solid #e6e8ec; border-radius: 10px; padding: 6px 18px; margin-bottom: 28px; }
.facts > div { display: flex; gap: 16px; padding: 11px 0; border-bottom: 1px solid #f2f3f5; }
.facts > div:last-child { border-bottom: 0; }
.facts dt { min-width: 78px; font-size: 13px; color: #8a8f98; }
.facts dd { font-size: 14px; color: #333; }

.body { margin-bottom: 26px; }
.body h2 { font-size: 16px; font-weight: 700; margin-bottom: 10px; }
.body p { font-size: 14px; line-height: 1.8; color: #444; white-space: pre-wrap; }

.cta {
  display: inline-block; padding: 12px 24px; border-radius: 8px;
  background: var(--primary); color: #fff; font-size: 14px; font-weight: 600;
}
.cta:hover { background: var(--primary-strong); color: #fff; }
.error { color: #c0392b; font-size: 14px; }
</style>
