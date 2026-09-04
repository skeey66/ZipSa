<script setup>
import { onMounted, ref } from 'vue'
import { fetchNotices } from '@/api/housing'
import { fetchRegions } from '@/api/transactions'
import { STATUS_LABEL } from '@/constants/housing'

// 화면 09 매물 랜딩 — 하위 두 화면으로 보내고, 각각의 현황을 요약해서 보여줍니다.
const openCount = ref(null)
const soonest = ref([])
const regionCount = ref(null)

onMounted(async () => {
  const [open, regions] = await Promise.all([
    fetchNotices({ recruitStatus: 'OPEN', size: 5 }),
    fetchRegions(),
  ])
  openCount.value = open.totalElements
  soonest.value = open.content
  regionCount.value = regions.length
})
</script>

<template>
  <section class="page">
    <nav class="crumb">매물</nav>
    <h1>매물</h1>
    <p class="lead">공공임대 모집 공고와 아파트 실거래가를 한 곳에서 확인하세요.</p>

    <div class="cards">
      <RouterLink to="/properties/public-housing" class="card">
        <span class="ico" aria-hidden="true">🏠</span>
        <h2>공공임대 정보확인</h2>
        <p>행복주택·국민임대·매입임대 모집 공고와 단지별 임대조건</p>
        <p class="stat" v-if="openCount !== null">지금 모집중 <strong>{{ openCount.toLocaleString() }}</strong>건</p>
      </RouterLink>

      <RouterLink to="/transactions" class="card">
        <span class="ico" aria-hidden="true">📍</span>
        <h2>실거래가</h2>
        <p>아파트 매매·전세·월세 실거래를 지도에서 확인</p>
        <p class="stat" v-if="regionCount !== null">전국 <strong>{{ regionCount }}</strong>개 지역</p>
      </RouterLink>
    </div>

    <section class="soon" v-if="soonest.length">
      <h3>마감이 가까운 공고</h3>
      <ul>
        <li v-for="n in soonest" :key="n.id">
          <a :href="n.applyUrl" target="_blank" rel="noopener noreferrer">
            <span class="badge">{{ STATUS_LABEL[n.status] }}</span>
            <span class="name">{{ n.name }}</span>
            <span class="dday">D-{{ n.dDay }}</span>
          </a>
        </li>
      </ul>
      <RouterLink to="/properties/public-housing" class="more">공고 전체 보기 →</RouterLink>
    </section>
  </section>
</template>

<style scoped>
.crumb { font-size: 13px; color: #8a8f98; margin-bottom: 6px; }
h1 { font-size: 30px; font-weight: 700; }
.lead { color: #6b7079; font-size: 14px; margin: 8px 0 28px; }

.cards { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.card {
  display: block; padding: 28px 26px; border: 1px solid #e6e8ec; border-radius: 12px;
  transition: border-color .12s, transform .12s;
}
.card:hover { border-color: var(--primary); transform: translateY(-2px); }
.ico { font-size: 30px; }
.card h2 { font-size: 19px; font-weight: 700; margin: 12px 0 6px; }
.card p { font-size: 14px; color: #6b7079; line-height: 1.6; }
.stat { margin-top: 14px; font-size: 13px; color: #8a8f98; }
.stat strong { color: var(--primary); font-size: 16px; }

.soon { margin-top: 40px; }
.soon h3 { font-size: 16px; font-weight: 700; margin-bottom: 12px; }
.soon ul { list-style: none; padding: 0; border-top: 1px solid #e6e8ec; }
.soon li { border-bottom: 1px solid #eef0f3; }
.soon a { display: flex; align-items: center; gap: 10px; padding: 13px 4px; font-size: 14px; }
.soon a:hover .name { color: var(--primary); }
.badge { padding: 2px 8px; border-radius: 4px; font-size: 11px; background: #e6f4ea; color: #1a7f37; font-weight: 600; }
.name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dday { font-size: 12px; font-weight: 700; color: #d02f2f; }
.more { display: inline-block; margin-top: 14px; font-size: 13px; color: var(--primary); }

@media (max-width: 800px) { .cards { grid-template-columns: 1fr; } }
</style>
