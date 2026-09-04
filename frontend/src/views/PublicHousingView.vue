<script setup>
import { computed, nextTick, onMounted, ref, shallowRef, watch } from 'vue'
import { fetchCalendar, fetchComplexMarkers, fetchComplexUnits, fetchNotices } from '@/api/housing'
import { fetchRegions } from '@/api/transactions'
import { loadKakaoMap } from '@/composables/useKakaoMap'
import { HOUSING_TYPES, RECRUIT_STATUS, STATUS_LABEL, range, wonToKor } from '@/constants/housing'

const MAX_LEVEL = 5

/**
 * 지도·목록에 보여줄 단지 이름.
 *
 * 매입임대는 LH 가 한 동네에 흩어진 주택을 사들이는 방식이라 「단지」가 없습니다.
 * 마이홈포털이 단지명 자리에 지역명을 넣어주는데(전체 7,124곳 중 3,396곳),
 * 그대로 그리면 지도에 「인천광역시 연수구」 마커가 여러 개 겹쳐 서로 구분되지 않습니다.
 * 이 경우에는 도로명주소를 대신 씁니다 — 지역명 단지는 주소가 전부 채워져 있습니다.
 */
// 「OO시」 단독 형태는 일부러 뺐습니다 — 「래미안라클래시」·「마포더클래시」처럼
// 시로 끝나는 진짜 단지명까지 주소로 바꿔버립니다(실제 데이터에서 확인).
const REGION_LIKE = /(특별시|광역시|특별자치시|특별자치도)\s*[가-힣]*[구군]?$|^[가-힣]{2,}시\s[가-힣]+[구군]$/
function complexLabel(m) {
  if (!m) return ''
  return REGION_LIKE.test((m.name ?? '').trim()) && m.roadAddress ? m.roadAddress : m.name
}

/* ── 상태 ─────────────────────────────── */
const tab = ref('notice')            // notice | map
const regions = ref([])
const regionCode = ref('11680')      // 강남구
const housingType = ref(null)
const recruitStatus = ref('OPEN')

const notices = ref([])
const totalElements = ref(0)
const page = ref(0)
const totalPages = ref(0)

const month = ref(new Date())
const calendar = ref([])
const pickedDay = ref(null)

const markers = ref([])
const selected = ref(null)          // { marker, units }
const mapEl = ref(null)
const map = shallowRef(null)
const overlays = shallowRef([])
const mapError = ref('')
const loading = ref(false)

const selectedRegion = computed(
  () => regions.value.find((r) => r.regionCode === regionCode.value),
)
const currentRegion = computed(() => selectedRegion.value?.sigungu ?? '')
// 공고의 지역은 시도 단위("서울특별시", "경기도")로 옵니다. 구 이름으로 거르면 0건이 됩니다.
const currentSido = computed(() => selectedRegion.value?.sido ?? '')
const monthKey = computed(() => {
  const d = month.value
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
})

/* ── 공고 목록 ─────────────────────────── */
async function loadNotices() {
  loading.value = true
  try {
    const res = await fetchNotices({
      region: currentSido.value || undefined,
      housingType: housingType.value ?? undefined,
      recruitStatus: recruitStatus.value ?? undefined,
      page: page.value,
      size: 20,
    })
    notices.value = res.content
    totalElements.value = res.totalElements
    totalPages.value = res.totalPages
  } finally {
    loading.value = false
  }
}

/* ── 캘린더 ───────────────────────────── */
async function loadCalendar() {
  calendar.value = await fetchCalendar(monthKey.value)
  pickedDay.value = null
}

/** 달력 격자. 앞뒤 빈 칸을 null 로 채워 7칸씩 끊어 놓는다. */
const grid = computed(() => {
  const d = month.value
  const first = new Date(d.getFullYear(), d.getMonth(), 1)
  const last = new Date(d.getFullYear(), d.getMonth() + 1, 0)
  const cells = Array(first.getDay()).fill(null)
  for (let i = 1; i <= last.getDate(); i += 1) cells.push(i)
  while (cells.length % 7) cells.push(null)
  return cells
})

/** 그날 모집을 "시작하는" 공고. 캘린더 점의 기준이다. */
function noticesStartingOn(day) {
  if (!day) return []
  const key = `${monthKey.value}-${String(day).padStart(2, '0')}`
  return calendar.value.filter((n) => n.recruitStartDate === key)
}

const dayNotices = computed(() => (pickedDay.value ? noticesStartingOn(pickedDay.value) : []))

function moveMonth(delta) {
  const d = new Date(month.value)
  d.setMonth(d.getMonth() + delta)
  month.value = d
}

/* ── 지도 ─────────────────────────────── */
async function initMap() {
  try {
    const kakao = await loadKakaoMap()
    map.value = new kakao.maps.Map(mapEl.value, {
      center: new kakao.maps.LatLng(37.4979, 127.0276),
      level: MAX_LEVEL,
    })
    window.kakao.maps.event.addListener(map.value, 'idle', declutter)
  } catch (e) {
    mapError.value = e.message
  }
}

function clearOverlays() {
  overlays.value.forEach((o) => o.overlay.setMap(null))
  overlays.value = []
}

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
    const hit = placed.some((p) => Math.abs(p.x - pt.x) < 74 && Math.abs(p.y - pt.y) < 30)
    item.el.classList.toggle('compact', hit)
    if (!hit) placed.push(pt)
  }
}

async function loadMarkers() {
  markers.value = await fetchComplexMarkers(regionCode.value, housingType.value ?? undefined)
  selected.value = null
  drawMarkers()
}

function drawMarkers() {
  const kakao = window.kakao
  if (!map.value || !kakao) return
  clearOverlays()
  if (!markers.value.length) return

  const bounds = new kakao.maps.LatLngBounds()
  // 세대수가 많은 단지가 라벨 자리를 먼저 차지하도록 정렬해 둔다.
  const sorted = [...markers.value].sort(
    (a, b) => (b.householdCount ?? 0) - (a.householdCount ?? 0),
  )
  sorted.forEach((m) => {
    const pos = new kakao.maps.LatLng(m.latitude, m.longitude)
    bounds.extend(pos)
    const el = document.createElement('div')
    el.className = 'pin'
    el.innerHTML = `
      <span class="bubble"><b>${m.housingTypeName}</b><i>${complexLabel(m)}</i></span>
      <svg class="house" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 3 2.5 11h2.6v9h5.1v-5.6h3.6V20h5.1v-9h2.6L12 3z"/>
      </svg>`
    el.title = `${complexLabel(m)} · ${m.householdCount ?? '-'}세대`
    el.onclick = () => selectComplex(m)
    const overlay = new kakao.maps.CustomOverlay({ position: pos, content: el, yAnchor: 1 })
    overlay.setMap(map.value)
    overlays.value.push({ overlay, el, position: pos })
  })

  map.value.setBounds(bounds, 30, 30, 30, 30)
  if (map.value.getLevel() > MAX_LEVEL) map.value.setLevel(MAX_LEVEL)
  setTimeout(declutter, 0)
}

async function selectComplex(marker) {
  const units = await fetchComplexUnits(marker.complexNo)
  selected.value = { marker, units }
}

/* ── 초기화 ───────────────────────────── */
onMounted(async () => {
  regions.value = await fetchRegions()
  await Promise.all([loadNotices(), loadCalendar()])
})

/**
 * 지도는 탭을 처음 열 때 만듭니다.
 * display:none 인 채로 초기화하면 컨테이너 크기가 0이라 타일도 마커도 그려지지 않습니다.
 * 이미 만든 뒤에도 탭을 다시 열면 relayout() 으로 크기를 다시 재게 해야 합니다.
 */
watch(tab, async (value) => {
  if (value !== 'map') return
  await nextTick()
  if (!map.value && !mapError.value) {
    await initMap()
    await loadMarkers()
    return
  }
  map.value?.relayout()
  drawMarkers()
})

watch([regionCode, housingType, recruitStatus], () => {
  page.value = 0
  loadNotices()
  if (map.value) loadMarkers()
})
watch(page, loadNotices)
watch(month, loadCalendar)
</script>

<template>
  <section class="page">
    <nav class="crumb">매물 <span>&gt;</span> 공공임대 정보확인</nav>
    <h1>공공임대 정보확인</h1>

    <div class="tabs">
      <button :class="{ on: tab === 'notice' }" @click="tab = 'notice'">모집 공고</button>
      <button :class="{ on: tab === 'map' }" @click="tab = 'map'">단지 지도</button>
    </div>

    <div class="filters">
      <select v-model="regionCode">
        <option v-for="r in regions" :key="r.regionCode" :value="r.regionCode">
          {{ r.regionName }}
        </option>
      </select>
      <div class="chips">
        <button v-for="t in HOUSING_TYPES" :key="t.label"
                :class="{ on: t.value === housingType }" @click="housingType = t.value">
          {{ t.label }}
        </button>
      </div>
      <div v-if="tab === 'notice'" class="chips status">
        <button v-for="s in RECRUIT_STATUS" :key="s.label"
                :class="{ on: s.value === recruitStatus }" @click="recruitStatus = s.value">
          {{ s.label }}
        </button>
      </div>
    </div>

    <!-- ── 모집 공고 탭 ────────────────────── -->
    <div v-show="tab === 'notice'" class="grid">
      <div class="board">
        <p class="total">
          {{ currentSido }} 공고 {{ totalElements.toLocaleString() }}건
          <span class="note">모집 공고는 시·도 단위로 제공됩니다</span>
        </p>
        <ul class="notices" v-if="notices.length">
          <li v-for="n in notices" :key="n.id">
            <a :href="n.applyUrl" target="_blank" rel="noopener noreferrer">
              <div class="head">
                <span class="badge" :data-st="n.status">{{ STATUS_LABEL[n.status] }}</span>
                <span class="type">{{ n.housingTypeName }}</span>
                <span v-if="n.status === 'OPEN'" class="dday">D-{{ n.dDay }}</span>
              </div>
              <h3>{{ n.name }}</h3>
              <p class="meta">{{ n.region }} · {{ n.recruitStartDate }} ~ {{ n.recruitEndDate }}</p>
            </a>
          </li>
        </ul>
        <p v-else-if="!loading" class="empty">조건에 맞는 공고가 없습니다.</p>

        <div class="pager" v-if="totalPages > 1">
          <button :disabled="page === 0" @click="page--">이전</button>
          <span>{{ page + 1 }} / {{ totalPages }}</span>
          <button :disabled="page >= totalPages - 1" @click="page++">다음</button>
        </div>
      </div>

      <aside class="cal">
        <header>
          <button @click="moveMonth(-1)">‹</button>
          <strong>{{ monthKey.replace('-', '. ') }}</strong>
          <button @click="moveMonth(1)">›</button>
        </header>
        <ul class="dow"><li v-for="d in ['일','월','화','수','목','금','토']" :key="d">{{ d }}</li></ul>
        <ul class="days">
          <li v-for="(day, i) in grid" :key="i">
            <button v-if="day" :class="{ on: pickedDay === day, has: noticesStartingOn(day).length }"
                    @click="pickedDay = pickedDay === day ? null : day">
              {{ day }}
              <span v-if="noticesStartingOn(day).length" class="dot"></span>
            </button>
          </li>
        </ul>
        <p class="cal-hint">점이 있는 날 = 모집 시작</p>

        <div v-if="pickedDay" class="picked">
          <h4>{{ monthKey.split('-')[1] }}월 {{ pickedDay }}일 모집 시작</h4>
          <ul v-if="dayNotices.length">
            <li v-for="n in dayNotices" :key="n.id">
              <a :href="n.applyUrl" target="_blank" rel="noopener noreferrer">{{ n.name }}</a>
            </li>
          </ul>
          <p v-else class="empty">이 날 시작하는 공고가 없습니다.</p>
        </div>
      </aside>
    </div>

    <!-- ── 단지 지도 탭 ────────────────────── -->
    <div v-show="tab === 'map'" class="grid">
      <div class="map-wrap">
        <div ref="mapEl" class="map"></div>
        <div v-if="mapError" class="map-guide">
          <strong>지도를 불러오지 못했습니다</strong>
          <p>{{ mapError }}</p>
        </div>
        <p v-if="!mapError" class="map-hint">
          🏠 아이콘을 누르면 평형별 임대조건을 볼 수 있습니다 · {{ markers.length }}개 단지
        </p>
      </div>

      <aside class="side">
        <template v-if="selected">
          <h2>{{ complexLabel(selected.marker) }}</h2>
          <p v-if="complexLabel(selected.marker) !== selected.marker.roadAddress" class="addr">
            {{ selected.marker.roadAddress }}
          </p>
          <p class="tags">
            <span>{{ selected.marker.housingTypeName }}</span>
            <span v-if="selected.marker.institution">{{ selected.marker.institution }}</span>
            <span v-if="selected.marker.householdCount">{{ selected.marker.householdCount }}세대</span>
          </p>
          <table class="units">
            <thead><tr><th>평형</th><th>전용</th><th class="r">보증금</th><th class="r">월세</th></tr></thead>
            <tbody>
              <tr v-for="u in selected.units" :key="u.id">
                <td>{{ u.styleName || '-' }}</td>
                <td>{{ u.exclusiveArea }}㎡</td>
                <td class="r">{{ wonToKor(u.deposit) }}</td>
                <td class="r">{{ wonToKor(u.monthlyRent) }}</td>
              </tr>
            </tbody>
          </table>
        </template>
        <template v-else>
          <h2>{{ currentRegion }} 공공임대 단지</h2>
          <p class="empty">지도에서 단지를 선택하세요.</p>
          <ul class="quick" v-if="markers.length">
            <li v-for="m in markers.slice(0, 12)" :key="m.complexNo">
              <button @click="selectComplex(m)">
                <strong>{{ complexLabel(m) }}</strong>
                <span>{{ m.housingTypeName }} · 보증금 {{ range(m.minDeposit, m.maxDeposit) }}</span>
              </button>
            </li>
          </ul>
        </template>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.crumb { font-size: 13px; color: #8a8f98; margin-bottom: 6px; }
.crumb span { margin: 0 4px; }
h1 { font-size: 30px; font-weight: 700; margin-bottom: 18px; }

.tabs { display: flex; gap: 8px; margin-bottom: 16px; }
.tabs button {
  padding: 9px 22px; border: 1px solid #d5d9e0; background: #fff;
  border-radius: 22px; cursor: pointer; font-size: 14px;
}
.tabs button.on { background: var(--primary); border-color: var(--primary); color: #fff; }

.filters { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 16px; margin-bottom: 18px; }
.filters select {
  padding: 9px 12px; border: 1px solid #d5d9e0; border-radius: 8px; font-size: 14px; outline: none;
}
.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chips button {
  padding: 5px 12px; border: 1px solid #e3e6ea; background: #fff;
  border-radius: 14px; cursor: pointer; font-size: 13px; color: #555;
}
.chips button.on { background: var(--primary-soft); border-color: var(--primary); color: var(--primary); font-weight: 600; }
.chips.status button.on { background: var(--primary); color: #fff; }

.grid { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 24px; align-items: start; }

.total { font-size: 13px; color: #8a8f98; margin-bottom: 10px; }
.total .note { margin-left: 8px; font-size: 11px; color: #b0b5bd; }
.notices { list-style: none; padding: 0; border-top: 1px solid #e6e8ec; }
.notices li { border-bottom: 1px solid #eef0f3; }
.notices a { display: block; padding: 16px 4px; }
.notices a:hover { background: #fafbfc; }
.notices a:hover h3 { color: var(--primary); }
.head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.badge { padding: 2px 8px; border-radius: 4px; font-size: 11px; background: #eef0f3; color: #6b7079; }
.badge[data-st="OPEN"] { background: #e6f4ea; color: #1a7f37; font-weight: 600; }
.badge[data-st="UPCOMING"] { background: #fff4e5; color: #b26a00; }
.type { font-size: 12px; color: var(--primary); }
.dday { margin-left: auto; font-size: 12px; font-weight: 700; color: #d02f2f; }
.notices h3 { font-size: 15px; font-weight: 600; line-height: 1.45; }
.meta { margin-top: 5px; font-size: 12px; color: #8a8f98; }

.cal { border: 1px solid #e6e8ec; border-radius: 10px; padding: 16px; }
.cal header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.cal header button { border: 0; background: none; font-size: 20px; cursor: pointer; color: #6b7079; padding: 0 8px; }
.cal header strong { font-size: 15px; }
.dow, .days { list-style: none; padding: 0; display: grid; grid-template-columns: repeat(7, 1fr); }
.dow li { text-align: center; font-size: 11px; color: #9aa0a8; padding-bottom: 6px; }
.days li { aspect-ratio: 1; }
.days button {
  position: relative; width: 100%; height: 100%; border: 0; background: none;
  border-radius: 6px; cursor: pointer; font-size: 13px; color: #444;
}
.days button:hover { background: #f2f4f7; }
.days button.has { font-weight: 700; color: var(--primary); }
.days button.on { background: var(--primary); color: #fff; }
.dot { position: absolute; left: 50%; bottom: 4px; transform: translateX(-50%);
       width: 4px; height: 4px; border-radius: 50%; background: currentColor; }
.cal-hint { margin-top: 8px; font-size: 11px; color: #9aa0a8; text-align: center; }
.picked { margin-top: 14px; border-top: 1px solid #eef0f3; padding-top: 12px; }
.picked h4 { font-size: 13px; margin-bottom: 8px; }
.picked ul { list-style: none; padding: 0; }
.picked li { font-size: 13px; line-height: 1.5; padding: 4px 0; }
.picked a:hover { color: var(--primary); }

.map-wrap { position: relative; }
.map { width: 100%; height: 540px; border-radius: 10px; background: #eef0f3; }
.map-hint { font-size: 12px; color: #8a8f98; margin-top: 8px; }
.map-guide {
  position: absolute; left: 0; right: 0; top: 0; height: 540px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 8px; padding: 24px; background: #f7f8fa;
  border: 1px dashed #cbd2dc; border-radius: 10px; text-align: center;
}
.map-guide p { font-size: 13px; color: #666; max-width: 520px; line-height: 1.6; white-space: pre-line; }

.side h2 { font-size: 17px; font-weight: 700; margin-bottom: 6px; }
.addr { font-size: 13px; color: #6b7079; margin-bottom: 8px; }
.tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
.tags span { padding: 3px 9px; border-radius: 12px; background: var(--primary-soft); color: var(--primary); font-size: 12px; }
.units { width: 100%; border-collapse: collapse; font-size: 13px; }
.units th, .units td { padding: 8px 6px; border-bottom: 1px solid #f0f1f4; text-align: left; }
.units th { color: #8a8f98; font-weight: 500; font-size: 12px; }
.units .r { text-align: right; font-variant-numeric: tabular-nums; }

.quick { list-style: none; padding: 0; margin-top: 12px; }
.quick button {
  width: 100%; text-align: left; padding: 10px 12px; border: 1px solid #eef0f3;
  background: #fff; border-radius: 8px; cursor: pointer; margin-bottom: 6px;
}
.quick button:hover { border-color: var(--primary); }
.quick strong { display: block; font-size: 13px; }
.quick span { font-size: 12px; color: #8a8f98; }

.pager { display: flex; align-items: center; justify-content: center; gap: 14px; margin-top: 20px; }
.pager button { padding: 7px 16px; border: 1px solid #d5d9e0; background: #fff; border-radius: 6px; cursor: pointer; font-size: 13px; }
.pager button:disabled { opacity: .4; cursor: default; }
.empty { color: #888; font-size: 13px; padding: 16px 0; }

@media (max-width: 1100px) { .grid { grid-template-columns: 1fr; } }
</style>
