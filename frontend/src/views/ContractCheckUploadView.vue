<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'

// 와이어프레임 15_계약서검사-파일첨부.
// 프론트 목업입니다 — 실제로 파일을 서버에 올리거나 OCR 을 돌리지 않습니다.
// 이름·크기만 읽어 화면에 보여주고, 분석 시작을 누르면 잠깐의 로딩 뒤
// 15b_계약서검사-결과(고정 목업 데이터)로 이동합니다.
const router = useRouter()

const CHECK_ITEMS = [
  { title: '근저당권 · 채권최고액', desc: '대출 잔액이 집값 대비 과도한지 확인' },
  { title: '가압류 · 가처분 · 경매', desc: '소유권 분쟁이나 강제집행 이력 확인' },
  { title: '소유자 일치 여부', desc: '계약 상대방과 등기상 소유자 비교' },
  { title: '전세가율 · 보증금 안전성', desc: '실거래가 대비 보증금 비율 계산' },
  { title: '임차권등기 · 선순위 세입자', desc: '보증금 반환 순위에 영향을 주는 권리 확인' },
]

/* 분석 진행 단계.
   막대만 도는 스피너는 "멈춘 건지 도는 건지" 구분이 안 됩니다.
   실제 파이프라인(추출 → 대조 → 산출) 순서를 그대로 보여줍니다. */
const STEPS = [
  '문서를 읽는 중',
  '텍스트를 추출하는 중 (OCR)',
  '권리관계를 대조하는 중',
  '위험도를 산출하는 중',
]
const STEP_MS = 750   // 4단계 × 750ms = 3초

const fileInput = ref(null)
const file = ref(null)
const dragOver = ref(false)
const analyzing = ref(false)
const step = ref(0)
let timer = null

// 화면을 벗어나도 타이머가 남으면 이미 사라진 라우터로 push 를 시도합니다.
onBeforeUnmount(() => clearInterval(timer))

function pick() {
  fileInput.value?.click()
}

function onFileChange(e) {
  const f = e.target.files?.[0]
  if (f) file.value = f
}

function onDrop(e) {
  dragOver.value = false
  const f = e.dataTransfer.files?.[0]
  if (f) file.value = f
}

function removeFile() {
  file.value = null
  if (fileInput.value) fileInput.value.value = ''
}

function formatSize(bytes) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))}KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`
}

function startAnalysis() {
  if (!file.value || analyzing.value) return
  analyzing.value = true
  step.value = 0

  // 실제로는 여기서 업로드 + OCR + AI 분석 API 를 부릅니다.
  // 지금은 목업이라 단계를 순서대로 넘기고 고정된 결과 화면으로 이동합니다.
  timer = setInterval(() => {
    step.value += 1
    if (step.value >= STEPS.length) {
      clearInterval(timer)
      router.push('/contract-check/result')
    }
  }, STEP_MS)
}
</script>

<template>
  <p class="crumb">홈 <span>›</span> 계약서 검사</p>
  <h1>계약서 검사</h1>
  <p class="lead">계약서를 업로드하면 집사가 문서를 읽고 위험 요소를 분석해드려요.</p>

  <div class="panel" :class="{ busy: analyzing }">
    <!-- 분석 중에는 패널 위를 덮어 조작을 막고 진행 상황만 보여줍니다. -->
    <div v-if="analyzing" class="analyzing" role="status" aria-live="polite">
      <div class="scanner" aria-hidden="true">
        <span class="doc">{{ file.name.toLowerCase().endsWith('.pdf') ? 'PDF' : 'IMG' }}</span>
        <span class="beam" />
      </div>

      <h2>계약서를 분석하고 있어요</h2>
      <p class="target">{{ file.name }} · {{ formatSize(file.size) }}</p>

      <div class="progress" :aria-valuenow="Math.round((step / STEPS.length) * 100)"
           role="progressbar" aria-valuemin="0" aria-valuemax="100">
        <span :style="{ width: `${(step / STEPS.length) * 100}%` }" />
      </div>

      <ol class="steps">
        <li v-for="(s, i) in STEPS" :key="s"
            :class="{ done: i < step, now: i === step }">
          <span class="dot" aria-hidden="true">{{ i < step ? '✓' : '' }}</span>
          {{ s }}
        </li>
      </ol>

      <p class="wait">문서 분량에 따라 몇 초 정도 걸릴 수 있어요.</p>
    </div>

    <section class="upload-col">
      <h2>파일 업로드</h2>

      <div
        class="dropzone"
        :class="{ over: dragOver, filled: file }"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="onDrop"
        @click="!file && pick()"
      >
        <template v-if="!file">
          <span class="upload-icon" aria-hidden="true">⬆</span>
          <p class="dz-title">파일을 여기에 끌어다 놓거나 클릭해서 업로드하세요</p>
          <p class="dz-sub">PDF, JPG, PNG · 최대 20MB · 스캔본도 OCR로 인식돼요</p>
          <button type="button" class="pick-btn" @click.stop="pick">파일 선택</button>
        </template>
        <template v-else>
          <span class="upload-icon done" aria-hidden="true">✓</span>
          <p class="dz-title">업로드 준비 완료</p>
          <p class="dz-sub">다른 파일로 바꾸려면 다시 클릭하세요</p>
        </template>
      </div>
      <input ref="fileInput" type="file" accept=".pdf,.jpg,.jpeg,.png" class="hidden-input" @change="onFileChange" />

      <div v-if="file" class="file-row">
        <span class="file-icon">{{ file.name.toLowerCase().endsWith('.pdf') ? 'PDF' : 'IMG' }}</span>
        <div class="file-meta">
          <p class="file-name">{{ file.name }}</p>
          <p class="file-size">{{ formatSize(file.size) }} · 업로드 완료</p>
        </div>
        <div class="file-bar"><span style="width:100%" /></div>
        <button type="button" class="file-remove" aria-label="파일 제거" @click="removeFile">×</button>
      </div>

      <label class="note">
        <input type="checkbox" checked disabled />
        업로드한 문서는 분석 후 즉시 삭제되며 저장되지 않습니다.
      </label>
    </section>

    <section class="check-col">
      <h2>집사가 확인하는 항목</h2>
      <ul class="check-list">
        <li v-for="item in CHECK_ITEMS" :key="item.title">
          <span class="check-mark" aria-hidden="true">✓</span>
          <div>
            <p class="check-title">{{ item.title }}</p>
            <p class="check-desc">{{ item.desc }}</p>
          </div>
        </li>
      </ul>

      <div class="tip">
        <p class="tip-title">💡 등기부등본은 어디서 받나요?</p>
        <p>인터넷등기소(iros.go.kr)에서 700원에 열람·발급할 수 있어요. 계약 당일 발급본으로 검사하는 것을 권장해요.</p>
      </div>
    </section>

    <button type="button" class="btn analyze-btn" :disabled="!file || analyzing" @click="startAnalysis">
      {{ analyzing ? '분석 중…' : '위험도 분석 시작' }}
    </button>
  </div>
</template>

<style scoped>
.crumb { font-size: 13px; color: #8a8f98; margin-bottom: 6px; }
.crumb span { margin: 0 4px; }
h1 { font-size: 30px; font-weight: 700; margin: 0 0 10px; }
.lead { font-size: 14.5px; color: var(--muted); margin: 0 0 24px; }

.panel {
  position: relative;
  border: 1px solid var(--border); border-radius: 14px; padding: 28px;
  display: grid; grid-template-columns: 1.3fr 1fr; gap: 28px;
}
/* 아래 내용이 비쳐 보이되 읽히지는 않게 둡니다. 화면이 통째로 바뀌면
   방금 무엇을 올렸는지 맥락이 끊깁니다. */
.panel.busy > section, .panel.busy > .analyze-btn { filter: blur(2px); opacity: .35; pointer-events: none; }

.analyzing {
  position: absolute; inset: 0; z-index: 5; border-radius: 14px;
  background: rgba(255, 255, 255, .88);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 32px; text-align: center;
}
.analyzing h2 { font-size: 18px; font-weight: 700; margin: 20px 0 6px; }
.target { margin: 0 0 22px; font-size: 12.5px; color: var(--muted); }

/* 문서 위를 훑고 지나가는 띠. 진행 중이라는 신호를 아이콘 하나로 줍니다. */
.scanner { position: relative; width: 64px; height: 78px; overflow: hidden; border-radius: 8px; }
.doc {
  position: absolute; inset: 0; border-radius: 8px;
  background: #d64545; color: #fff; font-size: 13px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.beam {
  position: absolute; left: 0; right: 0; height: 26px;
  background: linear-gradient(180deg, transparent, rgba(255, 255, 255, .85), transparent);
  animation: scan 1.5s ease-in-out infinite;
}
@keyframes scan { 0% { top: -26px; } 100% { top: 78px; } }

.progress { width: min(320px, 100%); height: 5px; border-radius: 3px; background: #eef0f3; overflow: hidden; }
.progress span { display: block; height: 100%; background: var(--primary); transition: width .5s ease; }

.steps { list-style: none; padding: 0; margin: 18px 0 0; text-align: left; }
.steps li {
  display: flex; align-items: center; gap: 8px;
  font-size: 13px; color: #b0b5bd; padding: 4px 0; transition: color .2s;
}
.steps li.done { color: var(--muted); }
.steps li.now { color: var(--text); font-weight: 600; }
.dot {
  width: 16px; height: 16px; border-radius: 50%; flex: none;
  border: 1.5px solid #d5d9e0; background: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 9px; color: #fff;
}
.steps li.done .dot { background: var(--primary); border-color: var(--primary); }
.steps li.now .dot { border-color: var(--primary); animation: pulse 1s ease-in-out infinite; }
@keyframes pulse { 50% { box-shadow: 0 0 0 4px var(--primary-soft); } }

.wait { margin: 18px 0 0; font-size: 12px; color: #9aa0a8; }

/* 움직임을 줄이도록 설정한 사용자에게는 애니메이션을 멈춥니다. */
@media (prefers-reduced-motion: reduce) {
  .beam, .steps li.now .dot { animation: none; }
  .progress span { transition: none; }
}
.panel h2 { font-size: 16px; font-weight: 700; margin: 0 0 14px; }

.dropzone {
  border: 2px dashed #e0b088; border-radius: 12px; background: var(--surface-soft);
  padding: 40px 20px; text-align: center; cursor: pointer;
  transition: border-color .12s, background .12s;
}
.dropzone.over { border-color: var(--primary); background: var(--primary-soft); }
.dropzone.filled { cursor: default; background: #f0f9f3; border-color: #bfe3cc; border-style: solid; }
.upload-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 44px; height: 44px; border-radius: 50%; margin-bottom: 12px;
  background: var(--primary-soft); color: var(--primary); font-size: 18px;
}
.upload-icon.done { background: #e0f3e6; color: #1e8a45; }
.dz-title { font-size: 15px; font-weight: 600; margin: 0 0 6px; }
.dz-sub { font-size: 12.5px; color: var(--muted); margin: 0 0 16px; }
.pick-btn {
  display: inline-block; width: auto; padding: 9px 22px; font-size: 13.5px; font-weight: 600;
  border: 1.5px solid var(--primary); border-radius: 8px; background: #fff; color: var(--primary);
  cursor: pointer; transition: background .12s, color .12s;
}
.pick-btn:hover { background: var(--primary); color: #fff; }
.hidden-input { display: none; }

.file-row {
  display: flex; align-items: center; gap: 12px; margin-top: 14px;
  padding: 12px 14px; border: 1px solid var(--border); border-radius: 10px;
}
.file-icon {
  flex-shrink: 0; width: 34px; height: 28px; border-radius: 5px;
  background: #d64545; color: #fff; font-size: 10px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.file-meta { flex: 1; min-width: 0; }
.file-name { margin: 0; font-size: 13.5px; font-weight: 600;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-size { margin: 2px 0 0; font-size: 11.5px; color: var(--muted); }
.file-bar { width: 70px; height: 5px; border-radius: 3px; background: #eee; overflow: hidden; flex-shrink: 0; }
.file-bar span { display: block; height: 100%; background: #4caf6f; }
.file-remove { border: 0; background: none; cursor: pointer; font-size: 18px; color: #9aa0a8; line-height: 1; }
.file-remove:hover { color: #d02f2f; }

.note {
  display: flex; align-items: center; gap: 8px; margin-top: 16px;
  font-size: 12px; color: var(--muted);
}

.check-list { list-style: none; padding: 0; margin: 0 0 20px; }
.check-list li { display: flex; gap: 10px; margin-bottom: 16px; }
.check-mark {
  flex-shrink: 0; width: 20px; height: 20px; border-radius: 50%; margin-top: 1px;
  background: var(--primary-soft); color: var(--primary);
  display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700;
}
.check-title { margin: 0; font-size: 13.5px; font-weight: 600; }
.check-desc { margin: 2px 0 0; font-size: 12px; color: var(--muted); line-height: 1.5; }

.tip { background: #fff8f0; border: 1px solid #f0dcc0; border-radius: 10px; padding: 14px 16px; }
.tip-title { margin: 0 0 6px; font-size: 13px; font-weight: 700; color: #a86a1e; }
.tip p:last-child { margin: 0; font-size: 12px; color: #8a6d1f; line-height: 1.6; }

.analyze-btn { grid-column: 1 / -1; }

@media (max-width: 860px) {
  .panel { grid-template-columns: 1fr; }
}
</style>
