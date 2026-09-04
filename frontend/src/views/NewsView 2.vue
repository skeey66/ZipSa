<script setup>
import { onMounted, ref, watch } from 'vue'
import { fetchNews, fetchNewsDetail } from '@/api/news'
import AiInsight from '@/components/AiInsight.vue'
import { fetchNewsInsight } from '@/api/ai'
import DetailDrawer from '@/components/DetailDrawer.vue'

const items = ref([])
const keyword = ref('')
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await fetchNews({
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: 20,
    })
    items.value = res.content
    totalPages.value = res.totalPages
    totalElements.value = res.totalElements
  } finally {
    loading.value = false
  }
}

/** "2026.09.03" · 오늘이면 "오늘" */
function day(iso) {
  const d = new Date(iso)
  const now = new Date()
  const p = (n) => String(n).padStart(2, '0')
  if (d.toDateString() === now.toDateString()) return `오늘 ${p(d.getHours())}:${p(d.getMinutes())}`
  return `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())}`
}

/* ── 상세 패널 ─────────────────────────
   기사 본문은 언론사 저작물이라 저장하지 않습니다(news 테이블에 컬럼 자체가 없음).
   그래서 패널에는 요약까지만 싣고, 본문은 원문 사이트로 보냅니다. */
const detail = ref(null)

/**
 * 기사 본문을 문단으로 끊습니다.
 * 추출된 원문은 줄바꿈이 들쭉날쭉해서 그대로 뿌리면 한 덩어리로 보입니다.
 * 빈 줄이 없으면 문장 단위로 3문장씩 묶어 문단을 만듭니다.
 */
function paragraphs(text) {
  if (!text) return []
  const byBlank = text.split(/\n\s*\n/).map((s) => s.trim()).filter(Boolean)
  if (byBlank.length > 1) return byBlank

  const sentences = text.split(/(?<=[.!?…”"])\s+/).map((s) => s.trim()).filter(Boolean)
  const out = []
  for (let i = 0; i < sentences.length; i += 3) {
    out.push(sentences.slice(i, i + 3).join(' '))
  }
  return out
}

async function openDetail(newsId) {
  detail.value = { newsId }
  detail.value = await fetchNewsDetail(newsId)
}

let timer
watch(keyword, () => {
  clearTimeout(timer)
  timer = setTimeout(() => { page.value = 0; load() }, 250)
})
watch(page, load)
onMounted(load)
</script>

<template>
  <section class="page">
    <nav class="crumb">정보 <span>&gt;</span> 주거 뉴스</nav>
    <h1>주거 뉴스</h1>
    <p class="lead">청년 주거·전월세·부동산 정책 관련 기사를 모았습니다.</p>

    <label class="search">
      <input v-model="keyword" type="search" placeholder="기사 검색 (예: 전세, 청년, 대출)" />
      <span aria-hidden="true">🔍</span>
    </label>

    <p class="total">전체 {{ totalElements.toLocaleString() }}건</p>

    <ul class="list" v-if="items.length">
      <li v-for="n in items" :key="n.newsId">
        <!-- 기사 본문은 저장하지 않습니다. 원문 사이트로 보냅니다. -->
        <button type="button" class="row" @click="openDetail(n.newsId)">
          <h2>{{ n.title }}</h2>
          <p v-if="n.summary" class="summary">{{ n.summary }}</p>
          <p class="meta">
            <span class="press">{{ n.pressName }}</span>
            <span>{{ day(n.publishedAt) }}</span>
            <span class="ext">자세히 보기</span>
          </p>
        </button>
      </li>
    </ul>
    <p v-else-if="!loading" class="empty">
      {{ keyword ? '검색 결과가 없습니다.' : '수집된 뉴스가 없습니다.' }}
    </p>

    <div class="pager" v-if="totalPages > 1">
      <button :disabled="page === 0" @click="page--">이전</button>
      <span>{{ page + 1 }} / {{ totalPages }}</span>
      <button :disabled="page >= totalPages - 1" @click="page++">다음</button>
    </div>

    <DetailDrawer :open="!!detail" :title="detail?.title ?? '뉴스'" @close="detail = null">
      <template #header>
        <div class="d-head">
          <h2>{{ detail?.title }}</h2>
          <p class="d-meta">
            <span class="press">{{ detail?.pressName }}</span>
            <span v-if="detail?.publishedAt">{{ day(detail.publishedAt) }}</span>
          </p>
        </div>
      </template>

      <p v-if="detail?.summary" class="d-summary">{{ detail.summary }}</p>

      <!-- 본문이 있으면 문단으로 나눠 읽기 좋게, 없으면 원문으로 보냅니다. -->
      <article v-if="detail?.content" class="d-body">
        <p v-for="(para, i) in paragraphs(detail.content)" :key="i">{{ para }}</p>
      </article>
      <p v-else-if="detail" class="d-notice">
        본문을 가져오지 못했습니다. 아래 원문 링크에서 확인하세요.
      </p>

      <AiInsight :loader="fetchNewsInsight" :target-id="detail?.newsId" />

      <template #footer>
        <a v-if="detail?.sourceUrl" class="d-cta" :href="detail.sourceUrl"
           target="_blank" rel="noopener noreferrer">
          {{ detail.pressName }} 원문 보기 ↗
        </a>
      </template>
    </DetailDrawer>
  </section>
</template>

<style scoped>
.crumb { font-size: 13px; color: #8a8f98; margin-bottom: 6px; }
.crumb span { margin: 0 4px; }
h1 { font-size: 30px; font-weight: 700; }
.lead { color: #6b7079; font-size: 14px; margin: 8px 0 20px; }

.search { position: relative; display: block; max-width: 460px; margin-bottom: 18px; }
.search input {
  width: 100%; padding: 11px 38px 11px 14px; font-size: 14px;
  border: 1px solid #d5d9e0; border-radius: 8px; outline: none;
}
.search input:focus { border-color: var(--primary); }
.search span { position: absolute; right: 13px; top: 50%; transform: translateY(-50%); opacity: .5; }

.total { font-size: 13px; color: #8a8f98; margin-bottom: 10px; }

.list { list-style: none; padding: 0; border-top: 1px solid #e6e8ec; }
.list li { border-bottom: 1px solid #eef0f3; }
.list a { display: block; padding: 18px 4px; text-decoration: none; }
.list a:hover h2 { color: var(--primary); }
.list a:hover { background: #fafbfc; }
.list h2 { font-size: 16px; font-weight: 600; color: #1c1f23; line-height: 1.45; }
.summary {
  margin-top: 6px; font-size: 14px; color: #6b7079; line-height: 1.6;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.meta { margin-top: 8px; font-size: 12px; color: #9aa0a8; display: flex; gap: 10px; align-items: center; }
.press { color: var(--primary); font-weight: 600; }
.ext { margin-left: auto; opacity: 0; transition: opacity .12s; }
.list a:hover .ext { opacity: 1; }

/* 목록 항목이 버튼이 되었으므로 기본 버튼 모양을 지웁니다. */
.list .row {
  display: block; width: 100%; text-align: left; border: 0; background: none;
  font: inherit; color: inherit; cursor: pointer; padding: 18px 4px;
}
.list .row:hover { background: #fafbfc; }
.list .row:hover h2 { color: var(--primary); }

.d-head h2 { font-size: 18px; font-weight: 700; line-height: 1.5; }
.d-meta { margin-top: 7px; display: flex; gap: 10px; font-size: 12px; color: #9aa0a8; }
.d-summary {
  font-size: 15px; line-height: 1.75; color: #1c1f23;
  padding: 15px 17px; background: var(--surface-soft); border-radius: 10px;
}
.d-body { margin-top: 22px; }
.d-body p { font-size: 15.5px; line-height: 1.85; color: #333; margin-bottom: 17px; }
.d-body p:last-child { margin-bottom: 0; }
.d-notice {
  margin-top: 16px; padding: 11px 14px; background: var(--surface-soft); border-radius: 8px;
  font-size: 12.5px; line-height: 1.6; color: #6b7079;
}
.d-cta { display: inline-block; padding: 11px 22px; border-radius: 8px; background: var(--primary); color: #fff; font-size: 14px; font-weight: 600; }
.d-cta:hover { background: var(--primary-strong); color: #fff; }

.pager { display: flex; align-items: center; justify-content: center; gap: 14px; margin-top: 24px; }
.pager button {
  padding: 7px 16px; border: 1px solid #d5d9e0; background: #fff;
  border-radius: 6px; cursor: pointer; font-size: 13px;
}
.pager button:disabled { opacity: .4; cursor: default; }
.pager span { font-size: 13px; color: #6b7079; }
.empty { color: #888; font-size: 14px; padding: 30px 0; text-align: center; }
</style>
