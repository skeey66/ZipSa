<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchLoanPrediction, fetchLoanSamples } from '@/api/loan'
import DetailDrawer from '@/components/DetailDrawer.vue'
import { BANK_CODE, logoSrc } from '@/constants/bank'

const router = useRouter()

/** 막대 뒤에 은행 마크를 워터마크로 깝니다. 코드가 없는 은행이면 생략합니다. */
const markSrc = (bankName) => (BANK_CODE[bankName] ? logoSrc(BANK_CODE[bankName]) : null)

const data = ref(null)
const error = ref('')
const loading = ref(true)
const popupOpen = ref(false)
const focusedBank = ref(null)

/* 색은 강조 구간에만 씁니다. 막대 다섯 개가 전부 색이면 어디가 내 구간인지 눈에 안 띕니다.
   카드 배경은 은행별로 물들이지 않고 중립 회색으로 두고, 브랜드 색은 강조 막대와
   워터마크가 담당합니다(와이어프레임 14_대출예측). */
const THEME = {
  amber:  { hit: '#f2b705' },
  blue:   { hit: '#2f6fd0' },
  green:  { hit: '#2e9e5b' },
  violet: { hit: '#6b4fd8' },
  rose:   { hit: '#c2334f' },
}
const theme = (key) => THEME[key] ?? THEME.blue

/** 강조되지 않은 구간의 공통 색. */
const BAR_MUTED = '#cdd3db'

/** 0번 칸은 「반려」입니다. 승인 구간과 같은 색으로 칠하면 구분이 안 됩니다. */
const REJECT_HIT = '#8a8f98'
const isReject = (i) => i === 0

/** 원 → "1.6억" / "2,800만". 1억 미만을 억으로 쓰면 0.3억 같은 표기가 나옵니다. */
function money(won) {
  if (won == null) return '-'
  if (won < 100_000_000) return `${Math.round(won / 10_000).toLocaleString()}만원`
  return `${(won / 100_000_000).toFixed(1)}억원`
}

/** 막대 높이는 그 은행 안에서의 상대값입니다. 은행마다 표본 수가 달라서요. */
function barHeight(bank, value) {
  const max = Math.max(...bank.distribution, 1)
  return `${Math.max(8, (value / max) * 100)}%`
}

function openPopup(bankName) {
  focusedBank.value = bankName
  popupOpen.value = true
}

/* ── 막대 클릭 — 이 금액대를 받은 사람들 ──
   카드 전체 클릭(은행별 예상 조건)과 목적이 다르므로 팝업을 따로 씁니다.
   막대에서 이벤트를 멈추지 않으면 카드 클릭까지 같이 터집니다. */
const samples = ref(null)
const samplesOpen = ref(false)
const samplesLoading = ref(false)

async function openSamples(bank, bucket) {
  samplesOpen.value = true
  samplesLoading.value = true
  samples.value = null
  try {
    samples.value = await fetchLoanSamples(bank, bucket)
  } catch (e) {
    samples.value = { error: e.message ?? '표본을 불러오지 못했습니다.' }
  } finally {
    samplesLoading.value = false
  }
}

onMounted(async () => {
  try {
    data.value = await fetchLoanPrediction()
  } catch (e) {
    if (e.status === 401) {
      router.push({ name: 'login', query: { redirect: '/loan-prediction' } })
      return
    }
    error.value = e.message ?? '대출 예측을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
})

const report = computed(() => data.value?.report)
</script>

<template>
  <section class="page">
    <nav class="crumb">홈 <span>&gt;</span> 대출예측</nav>
    <h1>나의 대출 예측하기</h1>

    <p v-if="loading" class="state">불러오는 중…</p>
    <p v-else-if="error" class="state error">{{ error }}</p>

    <template v-else-if="data">
      <!-- 어떤 조건으로 계산했는지 보여줘야 아래 숫자를 믿을 수 있습니다. -->
      <section class="me">
        <span class="who">{{ data.profile.nickname }} 님의 조건</span>
        <ul>
          <li><b>나이</b>{{ data.profile.ageRange }}</li>
          <li><b>직업</b>{{ data.profile.job }}</li>
          <li><b>연소득</b>{{ data.profile.salaryRange }}</li>
          <li><b>결혼</b>{{ data.profile.maritalStatus }}</li>
        </ul>
        <RouterLink to="/mypage" class="edit">정보 수정</RouterLink>
      </section>

      <div class="record-cta">
        <div>
          <strong>이미 대출을 받으셨나요?</strong>
          <p>결과를 남기면 다른 회원의 예측이 정확해지고, 커뮤니티 글에 은행 뱃지가 붙습니다.</p>
        </div>
        <RouterLink to="/loan-prediction/record" class="record-btn">내 대출 결과 입력하기</RouterLink>
      </div>

      <div class="cards">
        <button
          v-for="bank in data.banks"
          :key="bank.bankName"
          type="button"
          class="card"
          @click="openPopup(bank.bankName)"
        >
          <h2>{{ bank.bankName }}</h2>

          <div class="chart">
            <!-- 은행 마크 워터마크. 장식이라 클릭을 가로채지 않습니다. -->
            <img v-if="markSrc(bank.bankName)" class="mark" :src="markSrc(bank.bankName)" alt="" aria-hidden="true" />

            <button
              v-for="(value, i) in bank.distribution"
              :key="i"
              type="button"
              class="col"
              :disabled="!value"
              :title="value
                ? (isReject(i) ? `반려 ${value}건` : `${data.buckets[i]} · ${value}명이 이 구간에서 승인`)
                : '표본 없음'"
              @click.stop="value && openSamples(bank.bankName, i)"
            >
              <!-- 가장 가능성 높은 구간을 강조 (와이어프레임 주석 ①) -->
              <span
                v-if="i === bank.highlightIndex"
                class="flag"
                :style="{ borderColor: isReject(i) ? REJECT_HIT : theme(bank.theme).hit,
                          color: isReject(i) ? REJECT_HIT : theme(bank.theme).hit }"
              >①</span>
              <div
                class="bar"
                :class="{ reject: isReject(i), on: i === bank.highlightIndex }"
                :style="{
                  height: barHeight(bank, value),
                  background: i === bank.highlightIndex
                    ? (isReject(i) ? REJECT_HIT : theme(bank.theme).hit)
                    : BAR_MUTED,
                }"
              />
            </button>
          </div>

          <ul class="xaxis" :style="{ gridTemplateColumns: `repeat(${data.buckets.length}, 1fr)` }">
            <li v-for="(label, i) in data.buckets" :key="i"
                :class="{ on: i === bank.highlightIndex, reject: isReject(i) }">{{ label }}</li>
          </ul>
          <p class="axis">반려 · 대출한도 (낮음 → 높음)</p>
          <p class="expect">
            내 예상 한도 <strong>{{ money(bank.expectedLimit) }}</strong>
            <span class="rate">연 {{ bank.expectedRate }}%</span>
          </p>
        </button>
      </div>

      <p class="legend">
        <span>① 그래프에서 가장 가능성 높은 대출한도 구간을 강조 표시</span>
        <span>② 카드(그래프) 클릭 시 해당 은행의 맞춤 대출조건 팝업창이 열림</span>
      </p>

      <section class="report">
        <header>
          <div>
            <h2>대출 한도 분석 리포트</h2>
            <p class="scope">{{ report.scope }}</p>
          </div>
          <!-- 규칙 기반인지 LLM 인지 화면에 그대로 밝힙니다. -->
          <span class="badge" :class="{ real: report.aiGenerated }">
            {{ report.aiGenerated ? 'AI 분석' : '규칙 기반 분석' }}
          </span>
        </header>

        <p class="headline">{{ report.headline }}</p>

        <dl class="metrics">
          <div v-for="m in report.metrics" :key="m.label">
            <dt>{{ m.label }}</dt>
            <dd>{{ m.value }}</dd>
            <p>{{ m.note }}</p>
          </div>
        </dl>

        <div class="body">
          <section v-for="sec in report.sections" :key="sec.title">
            <h3>{{ sec.title }}</h3>
            <p v-for="(line, i) in sec.body" :key="i">{{ line }}</p>
          </section>
        </div>

        <section class="limits">
          <h3>분석의 한계</h3>
          <ul><li v-for="(line, i) in report.limitations" :key="i">{{ line }}</li></ul>
        </section>

        <p class="disclaimer">{{ report.disclaimer }}</p>
      </section>
    </template>

    <!-- 14b 팝업 -->
    <DetailDrawer :open="popupOpen" title="나의 대출 예측하기" @close="popupOpen = false">
      <template #header>
        <div>
          <h2 class="p-title">나의 대출 예측하기</h2>
          <p class="p-sub">로그인된 개인 정보 기반 은행별 맞춤 예상 대출조건</p>
        </div>
      </template>

      <ul class="p-list" v-if="data">
        <li
          v-for="bank in data.banks"
          :key="bank.bankName"
          :class="{ on: bank.bankName === focusedBank }"
        >
          <div class="p-bank">
            <span class="name">{{ bank.bankName }}</span>
            <strong class="amount">{{ money(bank.expectedLimit) }}</strong>
          </div>
          <div class="p-detail">
            <span>{{ bank.note }}</span>
            <span class="p-rate">연 {{ bank.expectedRate }}%</span>
          </div>
        </li>
      </ul>

      <template #footer>
        <button class="p-back" @click="popupOpen = false">돌아가기</button>
      </template>
    </DetailDrawer>

    <!-- 막대 클릭 — 이 금액대를 받은 사람들 (합격 스펙 형태) -->
    <DetailDrawer :open="samplesOpen" title="이 금액대를 받은 사람들" @close="samplesOpen = false">
      <template #header>
        <div>
          <h2 class="p-title">
            {{ samples?.bankName }} · {{ samples?.bucketLabel }}
          </h2>
          <p class="p-sub">
            <template v-if="samples && !samples.error">
              {{ samples.total }}건이
              {{ samples.rejectedBucket ? '반려됐습니다' : '이 금액대에서 승인됐습니다' }} ·
              {{ samples.summary }}
            </template>
            <template v-else>회원들이 실제로 신청한 결과입니다</template>
          </p>
        </div>
      </template>

      <p v-if="samplesLoading" class="s-state">불러오는 중…</p>
      <p v-else-if="samples?.error" class="s-state error">{{ samples.error }}</p>

      <template v-else-if="samples">
        <p class="s-mine" :class="{ here: samples.mine.inThisBucket }">
          {{ samples.mine.inThisBucket ? '📍' : '↗' }} {{ samples.mine.message }}
        </p>

        <table class="specs">
          <thead>
            <tr>
              <th>나이대</th><th>직업</th><th>연소득</th><th>지역</th>
              <!-- 반려 건은 승인 금액이 없습니다. 0원으로 보여주면 거짓 정보가 됩니다. -->
              <template v-if="!samples.rejectedBucket">
                <th class="r">승인 한도</th><th class="r">금리</th>
              </template>
              <th v-else class="r">결과</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(s, i) in samples.samples" :key="i" :class="{ similar: s.similar }">
              <td>{{ s.ageRange }}<span v-if="s.similar" class="me-tag">나와 비슷</span></td>
              <td>{{ s.job }}</td>
              <td>{{ s.salaryRange }}</td>
              <td>{{ s.region }}</td>
              <template v-if="!samples.rejectedBucket">
                <td class="r strong">{{ money(s.actualLimit) }}</td>
                <td class="r">{{ s.actualRate }}%</td>
              </template>
              <td v-else class="r reject-tag">반려</td>
            </tr>
          </tbody>
        </table>

        <p class="s-note">
          개인을 알아볼 수 있는 정보는 담지 않습니다. 실제 심사 결과와 다를 수 있습니다.
        </p>
      </template>

      <template #footer>
        <button class="p-back" @click="samplesOpen = false">닫기</button>
      </template>
    </DetailDrawer>
  </section>
</template>

<style scoped>
.crumb { font-size: 13px; color: #8a8f98; margin-bottom: 6px; }
.crumb span { margin: 0 4px; }
h1 { font-size: 30px; font-weight: 700; margin-bottom: 22px; }
.state { color: #8a8f98; font-size: 14px; padding: 40px 0; text-align: center; }
.state.error { color: #c0392b; }

.cards { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.card {
  background: #f6f7f9; overflow: hidden;
  border: 1px solid transparent; border-radius: 14px; padding: 20px 22px 18px;
  text-align: left; cursor: pointer; font: inherit; transition: border-color .12s, transform .12s;
}
.card:hover { border-color: var(--primary); transform: translateY(-2px); }
.card h2 { font-size: 15px; font-weight: 700; color: var(--text); }

/* 막대 아래 기준선. 막대가 떠 보이지 않게 바닥을 그어 줍니다. */
.chart {
  position: relative;
  display: flex; align-items: flex-end; gap: 14px;
  height: 160px; margin: 22px 0 8px;
  border-bottom: 1px solid #d9dde3;
}
/* 은행 심볼 워터마크.
   배경이 투명한 원본 로고라 multiply 같은 합성 트릭이 필요 없습니다.
   막대 뒤에 깔리도록 z-index 를 낮추고, 기준선 바로 위에 세웁니다. */
.chart .mark {
  position: absolute; z-index: 0;
  left: 50%; bottom: 22%; transform: translateX(-50%);
  width: 52%; height: 175px; object-fit: contain;
  opacity: .26;
  pointer-events: none; user-select: none;
}
.col { position: relative; z-index: 1; flex: 1; height: 100%; display: flex; align-items: flex-end; }
.bar { width: 100%; border-radius: 7px; transition: height .2s ease, background .12s; }
.bar.on { box-shadow: 0 2px 8px rgba(0, 0, 0, .13); }
/* 강조 구간 마커 ①. 막대 위에 살짝 띄웁니다. */
.flag {
  position: absolute; top: -22px; left: 50%; transform: translateX(-50%);
  width: 17px; height: 17px; border-radius: 50%; border: 1.5px solid currentColor;
  background: #fff; font-size: 10px; line-height: 1;
  display: flex; align-items: center; justify-content: center;
}

/* x축 금액. 막대와 칸 수·간격이 같아야 눈으로 맞춰집니다. */
.xaxis {
  display: grid; gap: 14px;
  list-style: none; padding: 8px 0 0; margin: 0;
}
.xaxis li { font-size: 10.5px; color: #9aa0a8; text-align: center; letter-spacing: -.3px; }
.xaxis li.on { color: #1c1f23; font-weight: 700; }
.xaxis li.reject { color: #b0b5bd; }
.axis { font-size: 11px; color: #9aa0a8; margin-top: 4px; }

.me {
  display: flex; align-items: center; flex-wrap: wrap; gap: 10px 18px;
  padding: 14px 18px; margin-bottom: 18px;
  background: var(--surface-soft); border-radius: 12px;
}
.me .who { font-size: 13px; font-weight: 700; color: #1c1f23; }
.me ul { display: flex; flex-wrap: wrap; gap: 8px; list-style: none; padding: 0; margin: 0; }
.me li {
  display: flex; align-items: center; gap: 6px;
  padding: 4px 11px; background: #fff; border-radius: 14px;
  font-size: 12.5px; color: #3d4148;
}
.me li b { font-size: 11px; color: #8a8f98; font-weight: 600; }
.me .edit { margin-left: auto; font-size: 12px; color: var(--primary); }

.record-cta {
  display: flex; align-items: center; gap: 16px; flex-wrap: wrap;
  padding: 16px 20px; margin-bottom: 18px;
  border: 1px solid var(--primary-soft); background: var(--surface-soft); border-radius: 12px;
}
.record-cta strong { font-size: 14px; color: #1c1f23; }
.record-cta p { margin-top: 4px; font-size: 12.5px; color: #6b7079; line-height: 1.6; }
.record-btn {
  margin-left: auto; flex-shrink: 0; padding: 10px 20px; border-radius: 8px;
  background: var(--primary); color: #fff; font-size: 13.5px; font-weight: 600;
}
.record-btn:hover { background: var(--primary-strong); color: #fff; }
.expect { margin-top: 10px; font-size: 13px; color: #55606e; display: flex; align-items: baseline; gap: 8px; }
.expect strong { font-size: 16px; color: #1c1f23; }
.rate { margin-left: auto; font-size: 12px; color: #8a8f98; }

.legend { display: flex; flex-wrap: wrap; gap: 18px; margin: 14px 2px 26px; font-size: 12px; color: #8a8f98; }

.report {
  border: 1px solid var(--border); border-radius: 14px; padding: 26px 28px 24px; background: #fff;
}
.report > header {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 16px;
  padding-bottom: 16px; border-bottom: 1px solid var(--border);
}
.report h2 { font-size: 18px; font-weight: 700; letter-spacing: -.01em; }
/* 분석 대상·표본·기준일. 이 줄이 없으면 어느 데이터를 본 결과인지 알 수 없습니다. */
.scope { margin-top: 6px; font-size: 12px; color: #8a8f98; }
.badge {
  flex: none; font-size: 11px; padding: 4px 10px; border-radius: 10px;
  background: #eef0f3; color: #6b7079; white-space: nowrap;
}
.badge.real { background: var(--primary-soft); color: var(--primary-strong); font-weight: 600; }

.headline { margin-top: 18px; font-size: 21px; font-weight: 700; color: var(--primary-strong); }

/* 핵심 지표. 본문을 읽지 않아도 결론이 보이도록 위에 세웁니다. */
.metrics {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 1px; margin: 18px 0 26px;
  background: var(--border); border: 1px solid var(--border); border-radius: 10px; overflow: hidden;
}
.metrics > div { background: #fafafa; padding: 14px 16px; }
.metrics dt { font-size: 12px; color: #8a8f98; }
.metrics dd { margin: 5px 0 3px; font-size: 17px; font-weight: 700; letter-spacing: -.02em; }
.metrics p { font-size: 11px; color: #9aa0a8; }

.body > section + section { margin-top: 22px; }
.body h3 { font-size: 14px; font-weight: 700; margin-bottom: 8px; }
.body p { font-size: 14px; line-height: 1.75; color: #3d4148; }
.body p + p { margin-top: 6px; }

/* 한계를 각주로 숨기지 않고 한 절로 둡니다. 근거의 범위를 밝히는 것도 보고서의 일부입니다. */
.limits { margin-top: 26px; padding: 16px 18px; background: var(--surface-soft); border-radius: 10px; }
.limits h3 { font-size: 13px; font-weight: 700; margin-bottom: 8px; color: #55606e; }
.limits ul { margin: 0; padding-left: 18px; }
.limits li { font-size: 13px; line-height: 1.7; color: #6b7079; }
.limits li + li { margin-top: 4px; }

.disclaimer { margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--border);
  font-size: 12px; line-height: 1.6; color: #9aa0a8; }

/* ── 팝업 ── */
.p-title { font-size: 19px; font-weight: 700; }
.p-sub { margin-top: 5px; font-size: 13px; color: #8a8f98; }
.p-list { list-style: none; padding: 0; }
.p-list li { border: 1px solid #eef0f3; border-radius: 10px; padding: 14px 16px; margin-bottom: 10px; }
.p-list li.on { border-color: var(--primary); background: var(--surface-soft); }
.p-bank { display: flex; align-items: baseline; gap: 12px; }
.p-bank .name { font-size: 13px; color: #6b7079; }
.p-bank .amount { margin-left: auto; font-size: 18px; font-weight: 700; color: var(--primary); }
.p-detail { display: flex; margin-top: 5px; font-size: 12px; color: #9aa0a8; }
.p-rate { margin-left: auto; }
.p-back {
  width: 100%; padding: 12px; border: 0; border-radius: 8px;
  background: var(--primary); color: #fff; font-size: 14px; font-weight: 600; cursor: pointer;
}

/* 막대가 버튼이 되었으므로 기본 버튼 모양을 지웁니다. */
.col { border: 0; background: none; padding: 0; cursor: pointer; }
.col:disabled { cursor: default; }
.col:not(:disabled):hover .bar { filter: brightness(0.88); }

/* ── 표본 팝업 ── */
.s-state { color: #8a8f98; font-size: 14px; padding: 30px 0; text-align: center; }
.s-state.error { color: #c0392b; }
.s-mine {
  padding: 11px 14px; border-radius: 8px; margin-bottom: 16px;
  font-size: 13px; background: #f2f4f7; color: #55606e;
}
.s-mine.here { background: var(--primary-soft); color: var(--primary-strong); font-weight: 600; }

.specs { width: 100%; border-collapse: collapse; font-size: 13px; }
.specs th, .specs td { padding: 9px 8px; border-bottom: 1px solid #f0f1f4; text-align: left; }
.specs th { color: #8a8f98; font-weight: 500; font-size: 12px; border-bottom: 1px solid #d9dde3; }
.specs .r { text-align: right; font-variant-numeric: tabular-nums; }
.specs .strong { font-weight: 700; color: #1c1f23; }
.specs tr.similar { background: var(--surface-soft); }
.reject-tag { color: #8a8f98; font-weight: 600; }
.me-tag {
  margin-left: 6px; padding: 1px 6px; border-radius: 8px;
  background: var(--primary); color: #fff; font-size: 10px; font-weight: 600;
}
.s-note { margin-top: 14px; font-size: 11.5px; color: #9aa0a8; line-height: 1.6; }

@media (max-width: 900px) { .cards { grid-template-columns: 1fr; } }
</style>
