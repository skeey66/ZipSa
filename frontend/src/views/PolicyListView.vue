<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchPolicies, fetchPolicy, fetchRecommended } from '@/api/policy'
import AiInsight from '@/components/AiInsight.vue'
import { fetchPolicyInsight } from '@/api/ai'
import DetailDrawer from '@/components/DetailDrawer.vue'
import RichContent from '@/components/RichContent.vue'
import { CATEGORY_COLOR, POLICY_CATEGORIES } from '@/constants/policy'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const policies = ref([])
const category = ref(null)
const keyword = ref('')
const openOnly = ref(true)
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const loading = ref(false)

const recommended = ref([])
const recoError = ref('')

/* ── 상세 패널 ─────────────────────────
   목록에서 바로 열립니다. 외부 링크로 나가면 맥락이 끊기고,
   AI 요약·내 적용 판단을 붙일 자리도 없어집니다. */
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')

async function openDetail(policyId) {
  detailLoading.value = true
  detailError.value = ''
  detail.value = { policyId }          // 껍데기를 먼저 띄워 패널이 즉시 열리게 합니다
  try {
    detail.value = await fetchPolicy(policyId)
  } catch (e) {
    detailError.value = e.message ?? '정책을 불러오지 못했습니다.'
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detail.value = null
  detailError.value = ''
}

async function load() {
  loading.value = true
  try {
    const res = await fetchPolicies({
      keyword: keyword.value.trim() || undefined,
      category: category.value ?? undefined,
      openOnly: openOnly.value,
      page: page.value,
      size: 20,
    })
    policies.value = res.content
    totalPages.value = res.totalPages
    totalElements.value = res.totalElements
  } finally {
    loading.value = false
  }
}

async function loadRecommended() {
  if (!auth.isLoggedIn) return
  try {
    recommended.value = await fetchRecommended(5)
  } catch (e) {
    // 추천이 실패해도 목록은 보여야 합니다.
    recoError.value = e.message
  }
}

let timer
watch(keyword, () => {
  clearTimeout(timer)
  timer = setTimeout(() => { page.value = 0; load() }, 250)
})
watch([category, openOnly], () => { page.value = 0; load() })
watch(page, load)
onMounted(() => { load(); loadRecommended() })
</script>

<template>
  <section class="page">
    <nav class="crumb">정보 <span>&gt;</span> 청년 정책</nav>
    <h1>청년 정책</h1>
    <p class="lead">주거비·대출·공공임대 관련 청년 정책을 모았습니다.</p>

    <!-- 맞춤 추천: 로그인한 사람에게만 -->
    <section v-if="auth.isLoggedIn && recommended.length" class="reco">
      <h2>나에게 맞는 정책</h2>
      <ul>
        <li v-for="r in recommended" :key="r.policyId">
          <button type="button" class="reco-row" @click="openDetail(r.policyId)">
            <div class="rate"><b>{{ r.relevanceRate }}</b><span>%</span></div>
            <div class="body">
              <strong>{{ r.title }}</strong>
              <p class="reasons">
                <span v-for="reason in r.matchReasons" :key="reason">{{ reason }}</span>
              </p>
            </div>
            <span v-if="r.dDay !== null" class="dday">D-{{ r.dDay }}</span>
          </button>
        </li>
      </ul>
    </section>

    <div class="toolbar">
      <label class="search">
        <input v-model="keyword" type="search" placeholder="정책명 검색 (예: 전세, 월세, 이자)" />
        <span aria-hidden="true">🔍</span>
      </label>
      <label class="only">
        <input type="checkbox" v-model="openOnly" /> 신청 가능한 정책만
      </label>
    </div>

    <ul class="chips">
      <li v-for="c in POLICY_CATEGORIES" :key="c.label">
        <button :class="{ on: c.value === category }" @click="category = c.value">{{ c.label }}</button>
      </li>
    </ul>

    <p class="total">{{ totalElements.toLocaleString() }}건</p>

    <ul class="list" v-if="policies.length">
      <li v-for="p in policies" :key="p.policyId">
        <button type="button" class="row" @click="openDetail(p.policyId)">
          <div class="head">
            <span class="cat" :style="{ color: CATEGORY_COLOR[p.category] }">{{ p.categoryName }}</span>
            <span v-if="p.region" class="region">{{ p.region }}</span>
            <span v-if="p.open && p.dDay !== null" class="dday">D-{{ p.dDay }}</span>
            <span v-else-if="!p.open" class="closed">마감</span>
          </div>
          <h3>{{ p.title }}</h3>
          <p v-if="p.summary" class="summary">{{ p.summary }}</p>
          <p class="meta">
            <span v-if="p.issuer">{{ p.issuer }}</span>
            <span v-if="p.targetAgeRange">{{ p.targetAgeRange }}</span>
          </p>
        </button>
      </li>
    </ul>
    <p v-else-if="!loading" class="empty">조건에 맞는 정책이 없습니다.</p>

    <div class="pager" v-if="totalPages > 1">
      <button :disabled="page === 0" @click="page--">이전</button>
      <span>{{ page + 1 }} / {{ totalPages }}</span>
      <button :disabled="page >= totalPages - 1" @click="page++">다음</button>
    </div>

    <!-- 상세 패널 — 풀화면이 아니라 목록 위에 겹쳐 뜹니다 -->
    <DetailDrawer :open="!!detail" :title="detail?.title ?? '정책 상세'" @close="closeDetail">
      <template #header>
        <div class="d-head">
          <span v-if="detail?.categoryName" class="cat"
                :style="{ color: CATEGORY_COLOR[detail.category] }">{{ detail.categoryName }}</span>
          <h2>{{ detail?.title }}</h2>
          <p class="d-meta">
            <span v-if="detail?.issuer">{{ detail.issuer }}</span>
            <span v-if="detail?.region">{{ detail.region }}</span>
            <span v-if="detail?.open && detail?.dDay !== null" class="dday">D-{{ detail.dDay }}</span>
            <span v-else-if="detail && !detail.open" class="closed">신청 마감</span>
          </p>
        </div>
      </template>

      <p v-if="detailError" class="d-error">{{ detailError }}</p>
      <p v-else-if="detailLoading" class="d-loading">불러오는 중…</p>

      <template v-else-if="detail">
        <dl class="facts">
          <div v-if="detail.targetAgeRange"><dt>지원 나이</dt><dd>{{ detail.targetAgeRange }}</dd></div>
          <div v-if="detail.targetJob"><dt>취업 상태</dt><dd>{{ detail.targetJob }}</dd></div>
          <div v-if="detail.targetSalaryRange"><dt>소득 조건</dt><dd>{{ detail.targetSalaryRange }}</dd></div>
          <div v-if="detail.applyEndDate">
            <dt>신청 기간</dt><dd>{{ detail.applyStartDate || '상시' }} ~ {{ detail.applyEndDate }}</dd>
          </div>
        </dl>

        <RichContent :text="detail.content" />

        <template v-if="detail.applyMethod">
          <h3 class="d-sub">신청 방법</h3>
          <RichContent :text="detail.applyMethod" />
        </template>

        <AiInsight :loader="fetchPolicyInsight" :target-id="detail.policyId" />
      </template>

      <template #footer>
        <a v-if="detail?.sourceUrl" class="d-cta" :href="detail.sourceUrl"
           target="_blank" rel="noopener noreferrer">
          {{ detail.sourceName || '원문' }}에서 신청하기 ↗
        </a>
      </template>
    </DetailDrawer>
  </section>
</template>

<style scoped>
.crumb { font-size: 13px; color: #8a8f98; margin-bottom: 6px; }
.crumb span { margin: 0 4px; }
h1 { font-size: 30px; font-weight: 700; }
.lead { color: #6b7079; font-size: 14px; margin: 8px 0 24px; }

.reco { border: 1px solid var(--primary-soft); background: var(--surface-soft); border-radius: 12px; padding: 20px 22px; margin-bottom: 28px; }
.reco h2 { font-size: 16px; font-weight: 700; margin-bottom: 12px; }
.reco ul { list-style: none; padding: 0; }
.reco li + li { border-top: 1px solid var(--border); }
.reco a { display: flex; align-items: center; gap: 14px; padding: 11px 0; }
.rate { display: flex; align-items: baseline; min-width: 46px; }
.rate b { font-size: 19px; font-weight: 700; color: var(--primary); }
.rate span { font-size: 11px; color: var(--primary); }
.reco .body { flex: 1; min-width: 0; }
.reco strong { font-size: 14px; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.reasons { margin-top: 4px; display: flex; flex-wrap: wrap; gap: 5px; }
.reasons span { font-size: 11px; color: var(--primary-strong); background: var(--primary-soft); padding: 2px 7px; border-radius: 10px; }

.toolbar { display: flex; align-items: center; gap: 18px; margin-bottom: 14px; }
.search { position: relative; flex: 1; max-width: 440px; }
.search input { width: 100%; padding: 11px 38px 11px 14px; font-size: 14px; border: 1px solid #d5d9e0; border-radius: 8px; outline: none; }
.search input:focus { border-color: var(--primary); }
.search span { position: absolute; right: 13px; top: 50%; transform: translateY(-50%); opacity: .5; }
.only { font-size: 13px; color: #555; display: flex; align-items: center; gap: 6px; cursor: pointer; }

.chips { display: flex; flex-wrap: wrap; gap: 6px; list-style: none; padding: 0; margin-bottom: 16px; }
.chips button { padding: 6px 14px; border: 1px solid #e3e6ea; background: #fff; border-radius: 16px; cursor: pointer; font-size: 13px; color: #555; }
.chips button.on { background: var(--primary); border-color: var(--primary); color: #fff; font-weight: 600; }

.total { font-size: 13px; color: #8a8f98; margin-bottom: 8px; }
.list { list-style: none; padding: 0; border-top: 1px solid #e6e8ec; }
.list li { border-bottom: 1px solid #eef0f3; }
.list a { display: block; padding: 16px 4px; }
.list a:hover { background: #fafbfc; }
.list a:hover h3 { color: var(--primary); }
.head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; font-size: 12px; }
.cat { font-weight: 600; }
.region { color: #8a8f98; }
.dday { margin-left: auto; font-weight: 700; color: #d02f2f; }
.closed { margin-left: auto; color: #b0b5bd; }
.list h3 { font-size: 15px; font-weight: 600; line-height: 1.45; }
.summary { margin-top: 5px; font-size: 13px; color: #6b7079; line-height: 1.6; }
.meta { margin-top: 6px; display: flex; gap: 10px; font-size: 12px; color: #9aa0a8; }

.pager { display: flex; align-items: center; justify-content: center; gap: 14px; margin-top: 22px; }
.pager button { padding: 7px 16px; border: 1px solid #d5d9e0; background: #fff; border-radius: 6px; cursor: pointer; font-size: 13px; }
.pager button:disabled { opacity: .4; cursor: default; }
/* 목록 항목이 버튼이 되었으므로 기본 버튼 모양을 지웁니다. */
.list .row, .reco-row {
  width: 100%; text-align: left; border: 0; background: none;
  font: inherit; color: inherit; cursor: pointer;
}
.list .row { display: block; padding: 16px 4px; }
.list .row:hover { background: #fafbfc; }
.list .row:hover h3 { color: var(--primary); }
.reco-row { display: flex; align-items: center; gap: 14px; padding: 11px 0; }

/* ── 상세 패널 ── */
.d-head { min-width: 0; }
.d-head .cat { font-size: 12px; font-weight: 600; }
.d-head h2 { font-size: 19px; font-weight: 700; line-height: 1.45; margin: 5px 0 7px; }
.d-meta { display: flex; flex-wrap: wrap; gap: 10px; font-size: 12px; color: #8a8f98; }
.d-meta .dday { font-weight: 700; color: #d02f2f; }
.d-meta .closed { color: #b0b5bd; }

.facts { border: 1px solid #eef0f3; border-radius: 10px; padding: 4px 16px; margin-bottom: 22px; }
.facts > div { display: flex; gap: 14px; padding: 9px 0; border-bottom: 1px solid #f4f5f7; }
.facts > div:last-child { border-bottom: 0; }
.facts dt { min-width: 70px; font-size: 12.5px; color: #8a8f98; }
.facts dd { font-size: 13.5px; color: #333; }

.d-sub { font-size: 15px; font-weight: 700; margin: 26px 0 10px; padding-top: 18px; border-top: 1px solid #eef0f3; }
.d-cta { display: inline-block; padding: 11px 22px; border-radius: 8px; background: var(--primary); color: #fff; font-size: 14px; font-weight: 600; }
.d-cta:hover { background: var(--primary-strong); color: #fff; }
.d-error { color: #c0392b; font-size: 14px; }
.d-loading { color: #8a8f98; font-size: 14px; padding: 20px 0; }

.empty { color: #888; font-size: 14px; padding: 30px 0; text-align: center; }
</style>
