<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deleteLoan, fetchMyLoans, recordLoan } from '@/api/loan'
import { BANKS, badgeSrc } from '@/constants/bank'

const router = useRouter()

const bank = ref(null)
// 반려도 기록해야 그래프의 「반려」 막대가 채워집니다.
const rejected = ref(false)
const amount = ref('')        // 만원 단위로 입력받습니다. 원 단위는 자릿수가 많아 오타가 납니다.
const rate = ref('')
const saving = ref(false)
const error = ref('')
const myLoans = ref([])

/** "15000" → 1.5억원. 입력하는 동안 바로 보여줘서 자릿수 실수를 막습니다. */
const preview = computed(() => {
  const man = Number(String(amount.value).replace(/[^\d]/g, ''))
  if (!man) return null
  const won = man * 10_000
  if (won < 100_000_000) return `${man.toLocaleString()}만원`
  return `${(won / 100_000_000).toFixed(2)}억원`
})

const canSubmit = computed(() =>
  bank.value && !saving.value && (rejected.value || Number(amount.value) >= 1000))

async function load() {
  myLoans.value = (await fetchMyLoans()).loans
}

async function onSubmit() {
  if (!canSubmit.value) return
  saving.value = true
  error.value = ''
  try {
    await recordLoan({
      bankName: bank.value,
      rejected: rejected.value,
      actualLimit: rejected.value
        ? null
        : Number(String(amount.value).replace(/[^\d]/g, '')) * 10_000,
      actualRate: rejected.value || !rate.value ? null : Number(rate.value),
    })
    bank.value = null
    rejected.value = false
    amount.value = ''
    rate.value = ''
    await load()
  } catch (e) {
    error.value = e.message ?? '등록하지 못했습니다.'
  } finally {
    saving.value = false
  }
}

async function onDelete(id) {
  await deleteLoan(id)
  await load()
}

function money(won) {
  if (won == null) return '-'
  if (won < 100_000_000) return `${Math.round(won / 10_000).toLocaleString()}만원`
  return `${(won / 100_000_000).toFixed(2)}억원`
}

onMounted(load)
</script>

<template>
  <section class="page">
    <nav class="crumb">
      <RouterLink to="/loan-prediction">대출예측</RouterLink> <span>&gt;</span> 내 대출 결과 입력
    </nav>
    <h1>내 대출 결과 입력하기</h1>
    <p class="lead">
      실제로 승인받은 결과를 남기면 다른 회원의 예측 정확도가 올라갑니다.
      등록하시면 커뮤니티 글에 은행 뱃지가 붙습니다.
    </p>

    <form class="form" @submit.prevent="onSubmit">
      <div class="field">
        <label>은행</label>
        <div class="banks">
          <button v-for="b in BANKS" :key="b.code" type="button"
                  class="bank" :class="{ on: bank === b.code }"
                  :style="bank === b.code ? { borderColor: b.color } : null"
                  @click="bank = b.code">
            <img :src="badgeSrc(b.code)" :alt="b.name" width="34" height="34" />
            <span>{{ b.name }}</span>
          </button>
        </div>
      </div>

      <div class="field">
        <label>심사 결과</label>
        <div class="result">
          <button type="button" :class="{ on: !rejected }" @click="rejected = false">승인</button>
          <button type="button" :class="{ on: rejected }" @click="rejected = true">반려</button>
        </div>
        <p v-if="rejected" class="hint">
          반려 기록도 도움이 됩니다. 어떤 조건이 떨어지는지 알 수 있어야 예측이 정확해집니다.
        </p>
      </div>

      <div class="field" v-if="!rejected">
        <label for="amount">승인 금액</label>
        <div class="amount">
          <input id="amount" v-model="amount" inputmode="numeric" placeholder="15000" />
          <span class="unit">만원</span>
          <!-- 원 단위로 받으면 0 개수를 틀립니다. 만원으로 받고 환산을 보여줍니다. -->
          <span v-if="preview" class="preview">= {{ preview }}</span>
        </div>
      </div>

      <div class="field" v-if="!rejected">
        <label for="rate">금리 <span class="opt">(선택)</span></label>
        <div class="amount">
          <input id="rate" v-model="rate" inputmode="decimal" placeholder="3.6" />
          <span class="unit">%</span>
        </div>
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <div class="actions">
        <RouterLink to="/loan-prediction" class="cancel">취소</RouterLink>
        <button type="submit" :disabled="!canSubmit">{{ saving ? '등록 중…' : '등록하기' }}</button>
      </div>
    </form>

    <section v-if="myLoans.length" class="mine">
      <h2>내가 등록한 결과</h2>
      <ul>
        <li v-for="l in myLoans" :key="l.id">
          <img v-if="l.bankCode" :src="badgeSrc(l.bankCode)" :alt="l.bankName" width="26" height="26" />
          <span class="name">{{ l.bankName }}</span>
          <strong v-if="!l.rejected">{{ money(l.actualLimit) }}</strong>
          <strong v-else class="rejected">반려</strong>
          <span v-if="l.actualRate" class="rate">연 {{ l.actualRate }}%</span>
          <span class="date">{{ l.createdAt }}</span>
          <button class="del" @click="onDelete(l.id)">삭제</button>
        </li>
      </ul>
    </section>
  </section>
</template>

<style scoped>
.crumb { font-size: 13px; color: #8a8f98; margin-bottom: 6px; }
.crumb span { margin: 0 4px; }
.crumb a:hover { color: var(--primary); }
h1 { font-size: 28px; font-weight: 700; }
.lead { color: #6b7079; font-size: 14px; line-height: 1.7; margin: 8px 0 28px; max-width: 560px; }

.form { max-width: 560px; }
.field { margin-bottom: 24px; }
.field label { display: block; font-size: 14px; font-weight: 600; margin-bottom: 10px; }
.opt { font-weight: 400; color: #9aa0a8; font-size: 12px; }

.banks { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.bank {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 16px 8px; border: 2px solid #e6e8ec; background: #fff;
  border-radius: 12px; cursor: pointer; font-size: 12px; color: #55606e;
}
.bank:hover { border-color: #c3c9d2; }
.bank.on { background: var(--surface-soft); font-weight: 700; color: #1c1f23; }
.bank img { border-radius: 50%; }

.result { display: flex; gap: 8px; }
.result button {
  padding: 9px 26px; border: 1px solid #d5d9e0; background: #fff;
  border-radius: 8px; cursor: pointer; font-size: 14px; color: #55606e;
}
.result button.on { background: var(--primary); border-color: var(--primary); color: #fff; font-weight: 600; }
.hint { margin-top: 8px; font-size: 12.5px; color: #8a8f98; line-height: 1.6; }

.amount { display: flex; align-items: center; gap: 8px; }
.amount input {
  width: 180px; padding: 12px 14px; border: 1px solid #d5d9e0; border-radius: 8px;
  font: inherit; font-size: 16px; text-align: right; outline: none;
  font-variant-numeric: tabular-nums;
}
.amount input:focus { border-color: var(--primary); }
.unit { font-size: 14px; color: #6b7079; }
.preview { margin-left: 6px; font-size: 14px; font-weight: 700; color: var(--primary); }

.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 30px; }
.actions .cancel { padding: 11px 24px; border: 1px solid #d5d9e0; border-radius: 8px; font-size: 14px; color: #555; }
.actions button {
  padding: 11px 28px; border: 0; border-radius: 8px;
  background: var(--primary); color: #fff; font-size: 14px; font-weight: 600; cursor: pointer;
}
.actions button:disabled { opacity: .45; cursor: default; }
.error { color: #c0392b; font-size: 14px; }

.mine { margin-top: 44px; max-width: 560px; }
.mine h2 { font-size: 16px; font-weight: 700; margin-bottom: 12px; }
.mine ul { list-style: none; padding: 0; border-top: 1px solid #e6e8ec; }
.mine li {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 2px; border-bottom: 1px solid #eef0f3; font-size: 14px;
}
.mine img { border-radius: 50%; }
.mine .name { color: #6b7079; font-size: 13px; }
.mine strong { font-variant-numeric: tabular-nums; }
.mine .rate { font-size: 12px; color: #8a8f98; }
.mine .rejected { color: #8a8f98; font-weight: 600; }
.mine .date { margin-left: auto; font-size: 12px; color: #b0b5bd; }
.mine .del { border: 0; background: none; color: #c0392b; font-size: 12px; cursor: pointer; }
</style>
