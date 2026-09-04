<script setup>
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { fetchMarkers, fetchRegions, fetchTransactions } from '@/api/transactions'
import { loadKakaoMap } from '@/composables/useKakaoMap'

const DEAL_TYPES = [
  { value: 'SALE', label: '매매' },
  { value: 'JEONSE', label: '전세' },
  { value: 'MONTHLY', label: '월세' },
]

// 구 하나가 화면에 꽉 차는 정도. setBounds 는 마커 분포에 맞춰 축소해버려서
// 단지가 넓게 퍼진 구(강서·노원 등)에서는 마커가 죄다 겹칩니다. 상한을 둡니다.
const MAX_LEVEL = 5

const regions = ref([])
const sido = ref('서울')
const regionCode = ref('11200')
const dealType = ref('SALE')
const keyword = ref('')
const markers = ref([])
const deals = ref([])
const selectedApt = ref(null)
const loading = ref(false)
const error = ref('')
const mapError = ref('')

const mapEl = ref(null)
const map = shallowRef(null)
const overlays = shallowRef([])

const currentRegion = computed(
  () => regions.value.find((r) => r.regionCode === regionCode.value)?.sigungu ?? '',
)

/* ── 지역 필터 ──────────────────────────────────────
   시군구가 238개라 한 줄로 늘어놓으면 고르는 게 아니라 찾는 일이 됩니다.
   시도를 먼저 고르게 해서 아래 칸에 남는 후보를 25개 안팎으로 줄입니다. */

// 크롤러가 아직 닿지 않은 지역이 대부분입니다(전국 238곳 중 29곳만 수집).
// 눌러도 빈 지도만 나오는 칩은 흐리게 두고 못 누르게 합니다 — 지역명은 남겨서
// 「없는 지역」이 아니라 「아직 수집 전」임이 보이게 합니다.
const sidos = computed(() => {
  const byName = new Map()
  for (const r of regions.value) {
    // Map 은 넣은 순서를 지킵니다. 서버가 지역코드 순으로 주므로 서울부터 나옵니다.
    const cur = byName.get(r.sido) ?? { name: r.sido, hasData: false }
    cur.hasData ||= r.hasData
    byName.set(r.sido, cur)
  }
  return [...byName.values()]
})
const sigungus = computed(() => regions.value.filter((r) => r.sido === sido.value))

function selectSido(next) {
  if (next.name === sido.value || !next.hasData) return
  sido.value = next.name
  // 시도만 바뀌고 시군구가 남아 있으면 지도와 필터가 어긋납니다.
  // 수집된 첫 시군구로 옮겨야 지도가 곧장 채워집니다.
  regionCode.value = sigungus.value.find((r) => r.hasData).regionCode
}

/** 만원 단위 정수를 "22억 7,929만" 으로. */
function won(man) {
  if (man == null) return '-'
  const eok = Math.floor(man / 10000)
  const rest = man % 10000
  if (eok && rest) return `${eok}억 ${rest.toLocaleString()}만`
  if (eok) return `${eok}억`
  return `${rest.toLocaleString()}만`
}

function priceLabel(d) {
  if (d.dealType === 'MONTHLY') return `${won(d.dealAmount)} / ${d.monthlyRent}만`
  return won(d.dealAmount)
}

/* ── 지도 ──────────────────────────────────────────── */

async function initMap() {
  try {
    const kakao = await loadKakaoMap()
    map.value = new kakao.maps.Map(mapEl.value, {
      center: new kakao.maps.LatLng(37.5512, 127.0396),
      level: MAX_LEVEL,
    })
  } catch (e) {
    mapError.value = e.message
  }
}

function clearOverlays() {
  overlays.value.forEach((o) => o.overlay.setMap(null))
  overlays.value = []
}

/**
 * 지오코딩이 어긋난 단지 하나가 지도를 서울 전체로 벌려놓는 일이 있습니다.
 * 위·아래 5% 를 잘라낸 범위로 맞춰서 대다수 단지가 화면을 채우게 합니다.
 */
function trimmedBounds(kakao, list) {
  const lat = list.map((m) => m.latitude).sort((a, b) => a - b)
  const lng = list.map((m) => m.longitude).sort((a, b) => a - b)
  const lo = (arr) => arr[Math.floor(arr.length * 0.05)]
  const hi = (arr) => arr[Math.ceil(arr.length * 0.95) - 1]
  return new kakao.maps.LatLngBounds(
    new kakao.maps.LatLng(lo(lat), lo(lng)),
    new kakao.maps.LatLng(hi(lat), hi(lng)),
  )
}

/**
 * 겹치는 가격표를 점으로 접습니다.
 * 거래가 많은 단지부터 자리를 잡고, 확대하면 간격이 벌어져 다시 펼쳐집니다.
 */
function declutter() {
  const kakao = window.kakao
  if (!map.value || !kakao || !overlays.value.length) return

  const proj = map.value.getProjection()
  const bounds = map.value.getBounds()
  const placed = []

  for (const item of overlays.value) {
    if (!bounds.contain(item.position)) {
      item.el.classList.add('compact')
      continue
    }
    const pt = proj.containerPointFromCoords(item.position)
    const collides = placed.some((p) => Math.abs(p.x - pt.x) < 70 && Math.abs(p.y - pt.y) < 30)
    item.el.classList.toggle('compact', collides)
    if (!collides) placed.push(pt)
  }
}

function drawMarkers() {
  const kakao = window.kakao
  if (!map.value || !kakao) return
  clearOverlays()
  const list = visibleMarkers.value
  if (!list.length) return

  // 거래가 많은 단지가 먼저 자리를 잡도록 정렬해 둡니다(declutter 가 이 순서를 씁니다).
  const sorted = [...list].sort((a, b) => b.dealCount - a.dealCount)

  sorted.forEach((m) => {
    const pos = new kakao.maps.LatLng(m.latitude, m.longitude)
    const el = document.createElement('div')
    el.className = 'pin'
    // 아이콘은 항상 보이고, 가격표는 자리가 있을 때만 붙습니다(declutter 가 .compact 를 토글).
    el.innerHTML = `
      <span class="bubble"><b>${won(m.avgAmount)}</b><i>${m.aptName}</i></span>
      <svg class="house" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 3 2.5 11h2.6v9h5.1v-5.6h3.6V20h5.1v-9h2.6L12 3z"/>
      </svg>`
    el.title = `${m.aptName} · 거래 ${m.dealCount}건`
    el.onclick = () => selectApt(m.aptName)

    const overlay = new kakao.maps.CustomOverlay({ position: pos, content: el, yAnchor: 1 })
    overlay.setMap(map.value)
    overlays.value.push({ overlay, el, position: pos })
  })

  map.value.setBounds(trimmedBounds(kakao, markers.value), 30, 30, 30, 30)
  // setBounds 는 "다 보이게" 만 맞추므로 너무 축소될 수 있습니다. 상한을 걸어 되돌립니다.
  if (map.value.getLevel() > MAX_LEVEL) map.value.setLevel(MAX_LEVEL)
  setTimeout(declutter, 0)
}

/* ── 데이터 ────────────────────────────────────────── */

async function load() {
  loading.value = true
  error.value = ''
  selectedApt.value = null
  try {
    markers.value = await fetchMarkers(regionCode.value, dealType.value, 12)
    const page = await fetchTransactions(regionCode.value, dealType.value, { keyword: keyword.value })
    deals.value = page.content
    drawMarkers()
  } catch (e) {
    error.value = e.message ?? '실거래가를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

async function selectApt(aptName) {
  selectedApt.value = aptName
  const page = await fetchTransactions(regionCode.value, dealType.value, { aptName })
  deals.value = page.content
}

async function clearApt() {
  selectedApt.value = null
  const page = await fetchTransactions(regionCode.value, dealType.value, { keyword: keyword.value })
  deals.value = page.content
}

/** 마커는 그 구 전체가 이미 손에 있으므로 화면에서 걸러도 누락이 없습니다. */
const visibleMarkers = computed(() => {
  const q = keyword.value.trim()
  return q ? markers.value.filter((m) => m.aptName.includes(q)) : markers.value
})

onMounted(async () => {
  regions.value = await fetchRegions()
  sido.value = regions.value.find((r) => r.regionCode === regionCode.value)?.sido
    ?? regions.value[0]?.sido
  await initMap()
  await load()
  if (map.value) window.kakao.maps.event.addListener(map.value, 'idle', declutter)
})

watch([regionCode, dealType], load)

// 타자 한 글자마다 서버를 때리지 않도록 잠깐 모았다가 한 번만 조회합니다.
let searchTimer
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    selectedApt.value = null
    const page = await fetchTransactions(regionCode.value, dealType.value, {
      keyword: keyword.value,
    })
    deals.value = page.content
    drawMarkers()
  }, 250)
})
</script>

<template>
  <section class="page">
    <nav class="crumb">매물 <span>&gt;</span> 실거래가 확인</nav>
    <h1>실거래가 확인</h1>

    <div class="toolbar">
      <label class="search">
        <input v-model="keyword" type="search" placeholder="단지명 검색" />
        <span aria-hidden="true">🔍</span>
      </label>
      <div class="tabs">
        <button
          v-for="t in DEAL_TYPES"
          :key="t.value"
          :class="{ on: t.value === dealType }"
          @click="dealType = t.value"
        >
          {{ t.label }}
        </button>
      </div>
    </div>

    <section class="filter" aria-label="지역 필터">
      <div class="row">
        <span class="label">지역</span>
        <ul class="chips">
          <li v-for="s in sidos" :key="s.name">
            <button
              :class="{ on: s.name === sido }"
              :disabled="!s.hasData"
              :title="s.hasData ? null : '아직 수집되지 않은 지역입니다'"
              @click="selectSido(s)"
            >
              {{ s.name }}
            </button>
          </li>
        </ul>
      </div>
      <div class="row">
        <span class="label">시군구</span>
        <ul class="chips sub">
          <li v-for="r in sigungus" :key="r.regionCode">
            <button
              :class="{ on: r.regionCode === regionCode }"
              :disabled="!r.hasData"
              :title="r.hasData ? null : '아직 수집되지 않은 지역입니다'"
              @click="regionCode = r.regionCode"
            >
              {{ r.sigungu }}
            </button>
          </li>
        </ul>
      </div>
    </section>

    <div class="grid">
      <div class="map-wrap">
        <div ref="mapEl" class="map"></div>
        <div v-if="mapError" class="map-guide">
          <strong>지도를 불러오지 못했습니다</strong>
          <p>{{ mapError }}</p>
        </div>
        <!-- 마커가 0이면 지도는 직전 지역에 그대로 머뭅니다. 제목은 새 지역인데
             지도는 옛 지역이라 그냥 두면 잘못된 지도를 읽게 됩니다. -->
        <div v-else-if="!loading && !visibleMarkers.length" class="map-guide">
          <strong>{{ currentRegion }}에 표시할 거래가 없습니다</strong>
          <p>{{ keyword ? `'${keyword}' 와 맞는 단지가 없습니다.` : '최근 1년 안에 수집된 거래가 없습니다. 아래 목록은 그대로 확인할 수 있습니다.' }}</p>
        </div>

        <p v-if="!mapError" class="map-hint">
          🏠 아이콘이 거래가 있는 단지입니다. 누르면 그 단지 거래만 볼 수 있습니다 ·
          {{ visibleMarkers.length }}개 단지
        </p>
      </div>

      <aside class="side">
        <header class="side-head">
          <h2 v-if="selectedApt">{{ selectedApt }}</h2>
          <h2 v-else>{{ currentRegion }} 최근 실거래가</h2>
          <button v-if="selectedApt" class="clear" @click="clearApt">전체 보기</button>
        </header>

        <p v-if="error" class="error">{{ error }}</p>

        <ol class="deals" v-if="deals.length">
          <li v-for="(d, i) in deals" :key="d.id">
            <span class="no">{{ i + 1 }}.</span>
            <div class="body">
              <strong class="price">{{ priceLabel(d) }}</strong>
              <p class="meta">{{ d.aptName }} · {{ d.exclusiveArea }}㎡ · {{ d.dealDate }}</p>
            </div>
          </li>
        </ol>
        <p v-else-if="!loading" class="empty">
          {{ keyword ? '검색 결과가 없습니다.' : '해당 조건의 실거래 내역이 없습니다.' }}
        </p>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.crumb { font-size: 13px; color: #8a8f98; margin-bottom: 6px; }
.crumb span { margin: 0 4px; }
h1 { font-size: 30px; font-weight: 700; margin-bottom: 20px; }

.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 14px; }
.search { position: relative; flex: 1; max-width: 460px; }
.search input {
  width: 100%; padding: 11px 38px 11px 14px; font-size: 14px;
  border: 1px solid #d5d9e0; border-radius: 8px; outline: none;
}
.search input:focus { border-color: var(--primary); }
.search span { position: absolute; right: 13px; top: 50%; transform: translateY(-50%); opacity: .5; }

.tabs { display: flex; gap: 8px; }
.tabs button {
  padding: 8px 20px; border: 1px solid #d5d9e0; background: #fff;
  border-radius: 20px; cursor: pointer; font-size: 14px;
}
.tabs button.on { background: var(--primary); border-color: var(--primary); color: #fff; }

.filter { border: 1px solid var(--border); border-radius: 10px; margin-bottom: 20px; }
.filter .row { display: flex; gap: 16px; padding: 12px 16px; }
.filter .row + .row { border-top: 1px solid var(--border); }
/* 라벨 폭을 고정해야 두 줄의 칩이 같은 세로선에서 시작합니다. */
.filter .label { flex: 0 0 52px; padding-top: 6px; font-size: 13px; font-weight: 600; color: #6b7079; }

.chips { display: flex; flex-wrap: wrap; gap: 6px; list-style: none; padding: 0; margin: 0; }
.chips button {
  padding: 5px 12px; border: 1px solid #e3e6ea; background: #fff;
  border-radius: 14px; cursor: pointer; font-size: 13px; color: #555;
}
/* .on 을 빼지 않으면 hover(구체도 0,3,1)가 .chips button.on(0,2,1)을 이겨서
   누른 칩이 주황 배경에 주황 글자가 됩니다 — 글자가 사라진 것처럼 보입니다. */
.chips button:hover:not(:disabled):not(.on) { border-color: var(--primary); color: var(--primary); }
.chips button:disabled { color: #c3c9d2; border-color: #eef0f3; cursor: not-allowed; }
/* 시도는 채워서, 시군구는 옅게. 같은 세기로 칠하면 어느 쪽이 상위인지 안 보입니다. */
.chips button.on { background: var(--primary); border-color: var(--primary); color: #fff; font-weight: 600; }
.chips.sub button.on { background: var(--primary-soft); border-color: var(--primary); color: var(--primary-strong); }

.grid { display: grid; grid-template-columns: minmax(0, 1fr) 380px; gap: 24px; align-items: start; }

.map-wrap { position: relative; }
.map { width: 100%; height: 560px; border-radius: 10px; background: #eef0f3; }
.map-hint { font-size: 12px; color: #8a8f98; margin-top: 8px; }
.map-guide {
  position: absolute; left: 0; right: 0; top: 0; height: 560px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 8px; padding: 24px; background: #f7f8fa;
  border: 1px dashed #cbd2dc; border-radius: 10px; text-align: center;
}
.map-guide strong { font-size: 15px; color: #333; }
.map-guide p { font-size: 13px; color: #666; max-width: 520px; line-height: 1.6; white-space: pre-line; }

.side-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 12px; }
.side-head h2 { font-size: 17px; font-weight: 700; }
.clear { border: 0; background: none; color: var(--primary); cursor: pointer; font-size: 13px; }

.deals { list-style: none; padding: 0; border: 1px solid #e6e8ec; border-radius: 10px;
         max-height: 560px; overflow-y: auto; }
.deals li { display: flex; gap: 12px; padding: 14px 16px; border-bottom: 1px solid #f0f1f4; }
.deals li:last-child { border-bottom: 0; }
.no { color: #b0b5bd; font-size: 13px; padding-top: 3px; min-width: 20px; }
.price { color: var(--primary); font-size: 19px; font-weight: 700; font-variant-numeric: tabular-nums; }
.meta { color: #6b7079; font-size: 13px; margin-top: 4px; }
.error { color: #c0392b; font-size: 14px; }
.empty { color: #888; font-size: 14px; padding: 20px 0; }

@media (max-width: 1100px) {
  .grid { grid-template-columns: 1fr; }
  .deals { max-height: 380px; }
}
</style>
