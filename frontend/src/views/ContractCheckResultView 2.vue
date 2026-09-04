<script setup>
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'

// 와이어프레임 15b_계약서검사-결과.
// 프론트 목업입니다 — 실제 OCR·AI 분석 결과가 아니라 Figma 예시 그대로의 고정 데이터입니다.
// 15_계약서검사-파일첨부 에서 분석 시작을 누르면 항상 이 화면(이 데이터)으로 옵니다.

const MOCK = {
  fileName: '등기사항전부증명서_서울시_마포구.pdf',
  docType: '등기부등본',
  analyzedAt: '2026.09.03 14:20',
  score: 62,
  level: '주의',
  summary: '근저당권 채권최고액이 시세 대비 높고 소유자 정보가 계약 상대방과 일치하지 않습니다. 계약 전 반드시 확인이 필요한 항목이 있어요.',
  counts: { 위험: 2, 주의: 1, 안전: 2 },
  items: [
    { level: '위험', title: '근저당권 설정 · 채권최고액 3억 6,000만원',
      desc: 'OO은행 근저당 (2024.03.12), 시세 5.2억 대비 69% → 보증금 회수 위험이 높아요.' },
    { level: '위험', title: '등기상 소유자와 계약 상대방 불일치',
      desc: "등기부 소유자 '김OO' ≠ 계약서 명의자 '박OO', 위임장·신고서명 확인 필요." },
    { level: '주의', title: '전세가율 85% (보증금 4.4억 / 시세 5.2억)',
      desc: '전세가율 80% 초과 시 깡통전세 가능성, 전세보증보험 가입 여부 확인 권장.' },
    { level: '안전', title: '가압류 · 가처분 · 경매 이력 없음',
      desc: '집주인 소유권 사항에 문제가 없습니다.' },
    { level: '안전', title: '임차권등기 · 선순위 세입자 없음',
      desc: '등기부상 앞선 순위의 임차인이 없습니다.' },
  ],
  ocr: {
    인식률: '97%',
    소재지: '서울특별시 마포구 OO동 123-4 OO아파트 101동 1203호',
    소유자: '김OO (1985.OO.OO)',
    '소유권 취득일': '2021.06.18 (매매)',
    근저당권자: 'OO은행 마포지점',
    채권최고액: '360,000,000원',
    '근저당 설정일': '2024.03.12',
    전용면적: '84.97㎡',
    '등록일 / 발급일': '2026.09.03',
  },
  report: [
    '근저당 채권최고액(3.6억)과 보증금(4.4억)을 합치면 시세(5.2억)를 초과 — 경매 시 보증금을 전액 돌려받기 어려울 수 있습니다.',
    '임대인 본인 확인: 등기 소유자 신분증 또는 위임장·인감증명서(3개월 이내) 사본 요청을 권장드립니다.',
    '계약 시 특약 예시: "전입일 익일까지 근저당 말소" 또는 "전세보증보험 가입 조건" 등을 협의해 보세요.',
  ],
}

const LEVEL_STYLE = {
  위험: { color: '#d02f2f', bg: '#fdeeee', border: '#f3c6c6' },
  주의: { color: '#a86a1e', bg: '#fff8f0', border: '#f0dcc0' },
  안전: { color: '#1e8a45', bg: '#f0f9f3', border: '#bfe3cc' },
}

// 점수 게이지 — 안전 0~40 · 주의 41~70 · 위험 71~100.
const gaugeColor = computed(() => {
  if (MOCK.score > 70) return LEVEL_STYLE.위험.color
  if (MOCK.score > 40) return LEVEL_STYLE.주의.color
  return LEVEL_STYLE.안전.color
})
const gaugeStyle = computed(() => ({
  background: `conic-gradient(${gaugeColor.value} ${MOCK.score * 3.6}deg, #eee 0deg)`,
}))

const pdfNote = ref(false)
function onSavePdf() {
  pdfNote.value = true
  setTimeout(() => { pdfNote.value = false }, 2400)
}
</script>

<template>
  <p class="crumb">홈 <span>›</span> 계약서 검사</p>
  <h1>계약서 검사</h1>
  <p class="file-line">
    <span class="file-badge">PDF</span>
    {{ MOCK.fileName }} · {{ MOCK.docType }} · 분석 완료 {{ MOCK.analyzedAt }}
    <span class="badge sample-badge">샘플</span>
    <RouterLink to="/contract-check" class="reanalyze">다시 검사하기</RouterLink>
  </p>

  <section class="score-card">
    <div class="gauge" :style="gaugeStyle">
      <div class="gauge-inner">
        <strong>{{ MOCK.score }}</strong>
        <span>/ 100점</span>
      </div>
    </div>
    <div class="score-body">
      <p class="level-line">
        <span class="badge lvl" :style="{ color: LEVEL_STYLE[MOCK.level].color, background: LEVEL_STYLE[MOCK.level].bg }">
          {{ MOCK.level }}
        </span>
        <strong>종합 위험도 : {{ MOCK.level }} 단계</strong>
      </p>
      <p class="summary">{{ MOCK.summary }}</p>
      <div class="scale">
        <span class="seg safe" />
        <span class="seg caution" />
        <span class="seg danger" />
        <b class="mark" :style="{ left: MOCK.score + '%' }">▲ 현재 {{ MOCK.score }}</b>
      </div>
      <div class="scale-labels">
        <span>안전 0~40</span><span>주의 41~70</span><span>위험 71~100</span>
      </div>
    </div>
    <ul class="count-legend">
      <li v-for="(n, lv) in MOCK.counts" :key="lv">
        <span class="dot" :style="{ background: LEVEL_STYLE[lv].color }" />{{ lv }} <b>{{ n }}건</b>
      </li>
    </ul>
  </section>

  <div class="detail-row">
    <section class="risk-card">
      <h2>발견된 위험 요소</h2>
      <ul class="risk-list">
        <li v-for="(item, i) in MOCK.items" :key="i">
          <span class="badge lvl" :style="{ color: LEVEL_STYLE[item.level].color, background: LEVEL_STYLE[item.level].bg }">
            {{ item.level }}
          </span>
          <div class="risk-body">
            <p class="risk-title">{{ item.title }}</p>
            <p class="risk-desc">{{ item.desc }}</p>
          </div>
          <a href="#" class="risk-link" @click.prevent>원문 보기 ›</a>
        </li>
      </ul>
    </section>

    <section class="ocr-card">
      <h2>OCR 추출 정보 <span class="ocr-rate">인식률 {{ MOCK.ocr.인식률 }}</span></h2>
      <dl class="ocr-list">
        <template v-for="(v, k) in MOCK.ocr" :key="k">
          <template v-if="k !== '인식률'">
            <dt>{{ k }}</dt>
            <dd>{{ v }}</dd>
          </template>
        </template>
      </dl>
    </section>
  </div>

  <section class="report">
    <h2>집사의 분석레포트</h2>
    <ul>
      <li v-for="(line, i) in MOCK.report" :key="i">{{ line }}</li>
    </ul>
    <p class="disclaimer">
      본 분석은 OCR 추출과 규칙 기반 인식을 사용한 샘플 결과이며 법률 자문이 아닙니다.
      최종 판단은 공인중개사·법무사 등 전문가의 확인을 권장드립니다.
    </p>
  </section>

  <div class="cta-row">
    <RouterLink to="/loan-prediction" class="btn-ghost cta-ghost">대출예측에 반영하기</RouterLink>
    <span class="cta-wrap">
      <button type="button" class="btn cta-primary" @click="onSavePdf">레포트 PDF 저장</button>
      <span v-if="pdfNote" class="pdf-note">PDF 저장은 준비 중인 기능이에요.</span>
    </span>
  </div>
</template>

<style scoped>
.crumb { font-size: 13px; color: #8a8f98; margin-bottom: 6px; }
.crumb span { margin: 0 4px; }
h1 { font-size: 30px; font-weight: 700; margin: 0 0 12px; }

.file-line { display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  font-size: 13px; color: var(--muted); margin: 0 0 20px; }
.file-badge { background: #d64545; color: #fff; font-size: 10px; font-weight: 700;
  padding: 2px 6px; border-radius: 4px; }
.badge { font-size: 11px; padding: 3px 9px; border-radius: 10px; background: #f0f1f4; color: #8a8f98; }
.sample-badge { color: #8a8f98; }
.reanalyze { margin-left: auto; color: var(--primary); font-weight: 600; font-size: 13px; }

.score-card {
  display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 28px;
  border: 1px solid var(--border); border-radius: 14px; padding: 24px 28px; margin-bottom: 20px;
}
.gauge {
  width: 108px; height: 108px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}
.gauge-inner {
  width: 88px; height: 88px; border-radius: 50%; background: #fff;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.gauge-inner strong { font-size: 26px; font-weight: 800; line-height: 1; }
.gauge-inner span { font-size: 11px; color: var(--muted); margin-top: 3px; }

.level-line { display: flex; align-items: center; gap: 10px; margin: 0 0 8px; }
.level-line strong { font-size: 17px; }
.badge.lvl { font-weight: 700; }
.summary { margin: 0 0 14px; font-size: 13.5px; color: var(--muted); line-height: 1.6; }

.scale { position: relative; display: flex; height: 8px; border-radius: 4px; overflow: visible; margin-bottom: 22px; }
.scale .seg { flex: 1; }
.scale .seg:first-child { border-radius: 4px 0 0 4px; }
.scale .seg:last-child { border-radius: 0 4px 4px 0; }
.scale .safe { background: #6cbf84; }
.scale .caution { background: #f2c94c; }
.scale .danger { background: #e0625c; }
.scale .mark {
  position: absolute; top: -20px; transform: translateX(-50%);
  font-size: 11px; font-weight: 700; color: #1c1f23; white-space: nowrap;
}
.scale-labels { display: flex; justify-content: space-between; font-size: 11px; color: #9aa0a8; margin-top: -14px; }

.count-legend { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; }
.count-legend li { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--muted); white-space: nowrap; }
.count-legend b { color: #1c1f23; }
.dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }

.detail-row { display: grid; grid-template-columns: 1.4fr 1fr; gap: 20px; margin-bottom: 20px; }
.risk-card, .ocr-card, .report {
  border: 1px solid var(--border); border-radius: 14px; padding: 22px 24px;
}
.risk-card h2, .ocr-card h2, .report h2 { font-size: 15px; font-weight: 700; margin: 0 0 16px; }
.ocr-rate { float: right; font-size: 12px; font-weight: 600; color: var(--primary); }

.risk-list { list-style: none; padding: 0; margin: 0; }
.risk-list li {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 12px 0; border-bottom: 1px solid #f0f1f4;
}
.risk-list li:last-child { border-bottom: 0; padding-bottom: 0; }
.risk-body { flex: 1; min-width: 0; }
.risk-title { margin: 0; font-size: 13.5px; font-weight: 600; }
.risk-desc { margin: 3px 0 0; font-size: 12px; color: var(--muted); line-height: 1.55; }
.risk-link { flex-shrink: 0; font-size: 12px; color: var(--primary); font-weight: 600; white-space: nowrap; }

.ocr-list { display: grid; grid-template-columns: auto 1fr; row-gap: 10px; column-gap: 14px; margin: 0; }
.ocr-list dt { font-size: 12px; color: var(--muted); white-space: nowrap; }
.ocr-list dd { margin: 0; font-size: 12.5px; color: #1c1f23; font-weight: 500; }

.report ul { list-style: none; padding: 0; margin: 0 0 14px; }
.report li {
  position: relative; padding-left: 15px; margin-bottom: 10px;
  font-size: 13.5px; line-height: 1.7; color: #3d4148;
}
.report li::before {
  content: ''; position: absolute; left: 3px; top: 9px;
  width: 4px; height: 4px; border-radius: 50%; background: #c3c9d2;
}
.disclaimer { margin: 0; font-size: 11.5px; color: #9aa0a8; line-height: 1.6; }

.cta-row { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
.cta-ghost { display: inline-block; width: auto; padding: 12px 22px; }
.cta-wrap { position: relative; }
.cta-primary { width: auto; padding: 12px 22px; }
.pdf-note {
  position: absolute; bottom: calc(100% + 8px); right: 0; white-space: nowrap;
  background: #1c1f23; color: #fff; font-size: 12px; padding: 6px 12px; border-radius: 6px;
}

@media (max-width: 860px) {
  .score-card { grid-template-columns: 1fr; text-align: center; }
  .count-legend { flex-direction: row; justify-content: center; flex-wrap: wrap; }
  .detail-row { grid-template-columns: 1fr; }
  .cta-row { flex-direction: column; }
  .cta-ghost, .cta-primary { width: 100%; }
}
</style>
