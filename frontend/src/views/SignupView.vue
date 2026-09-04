<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { signUp, checkLoginId } from '@/api/auth'

/**
 * 와이어프레임 03(정보입력 6단계) + 04(개인정보 동의).
 * 프레임 주석대로 스텝 UI 는 질문만 바뀌고 구조는 동일하므로 데이터로 정의합니다.
 */
const STEPS = [
  {
    key: 'account',
    type: 'form',
    title: '계정을 만들어 주세요',
    subtitle: '로그인에 사용할 정보예요',
  },
  {
    key: 'ageRange',
    type: 'choice',
    title: '나이가 어떻게 되시나요?',
    subtitle: '정확한 정책 매칭을 위해 필요한 정보예요',
    // 값은 백엔드 AgeRange enum 과 1:1이어야 합니다.
    // 예전에 있던 ETC 를 그대로 두었더니 가입이 500 으로 실패했습니다.
    options: [
      { value: 'AGE_10S', label: '10대 (19세 이하)' },
      { value: 'AGE_20S_EARLY', label: '20대 초반 (20~24세)' },
      { value: 'AGE_20S_LATE', label: '20대 후반 (25~29세)' },
      { value: 'AGE_30S_EARLY', label: '30대 초반 (30~34세)' },
      { value: 'AGE_30S_LATE', label: '30대 후반 (35~39세)' },
      { value: 'AGE_40S_OVER', label: '40대 이상' },
    ],
  },
  {
    key: 'maritalStatus',
    type: 'choice',
    title: '결혼하셨나요?',
    subtitle: '신혼부부 대상 정책을 찾아드릴게요',
    options: [
      { value: 'SINGLE', label: '미혼' },
      { value: 'MARRIED', label: '기혼' },
    ],
  },
  {
    key: 'job',
    type: 'choice',
    title: '어떤 일을 하고 계신가요?',
    subtitle: '직업에 따라 신청 가능한 정책이 달라요',
    options: [
      { value: 'STUDENT', label: '학생' },
      { value: 'EMPLOYEE', label: '직장인' },
      { value: 'SELF_EMPLOYED', label: '자영업자' },
      { value: 'JOB_SEEKER', label: '구직 중' },
      { value: 'ETC', label: '기타' },
    ],
  },
  {
    key: 'salaryRange',
    type: 'choice',
    title: '연소득이 어느 정도인가요?',
    subtitle: '소득 요건이 있는 정책을 걸러드릴게요',
    options: [
      { value: 'UNDER_2000', label: '2천만원 미만' },
      { value: 'RANGE_2000_3000', label: '2천~3천만원' },
      { value: 'RANGE_3000_4000', label: '3천~4천만원' },
      { value: 'RANGE_4000_5000', label: '4천~5천만원' },
      { value: 'RANGE_5000_7000', label: '5천~7천만원' },
      { value: 'OVER_7000', label: '7천만원 이상' },
    ],
  },
  {
    key: 'region',
    type: 'choice',
    title: '어디에 살고 계신가요?',
    // 수집된 청년정책 404건 중 386건이 지자체 한정입니다.
    // 지역을 모르면 신청도 못 하는 정책을 추천하게 됩니다.
    subtitle: '청년정책의 95%가 지자체 정책이라, 지역이 있어야 신청 가능한 것만 보여드릴 수 있어요',
    columns: 3,
    options: [
      { value: '서울', label: '서울' }, { value: '경기', label: '경기' },
      { value: '인천', label: '인천' }, { value: '부산', label: '부산' },
      { value: '대구', label: '대구' }, { value: '대전', label: '대전' },
      { value: '울산', label: '울산' }, { value: '세종', label: '세종' },
      { value: '강원', label: '강원' }, { value: '충북', label: '충북' },
      { value: '충남', label: '충남' }, { value: '전북', label: '전북' },
      // 2026년 개편으로 광주광역시와 전라남도가 통합됐습니다.
      { value: '전남광주', label: '전남·광주' },
      { value: '경북', label: '경북' }, { value: '경남', label: '경남' },
      { value: '제주', label: '제주' },
    ],
  },
  { key: 'consent', type: 'consent', title: '커뮤니티 이용을 위한 정보 제공에 동의해주세요' },
]

const stepIndex = ref(0)
const step = computed(() => STEPS[stepIndex.value])
const isLastStep = computed(() => stepIndex.value === STEPS.length - 1)
const progress = computed(() => ((stepIndex.value + 1) / STEPS.length) * 100)

const form = ref({
  loginId: '', password: '', passwordConfirm: '', nickname: '',
  ageRange: null, maritalStatus: null, job: null, salaryRange: null, region: null,
  agreePrivacy: false, agreeCommunity: false, agreeMarketing: false,
})

const errorMessage = ref('')
const idCheck = ref({ checked: false, available: false, message: '' })
const submitting = ref(false)
const router = useRouter()

async function onCheckId() {
  errorMessage.value = ''
  try {
    const result = await checkLoginId(form.value.loginId)
    idCheck.value = {
      checked: true,
      available: result.available,
      message: result.available ? '사용 가능한 아이디입니다.' : '이미 사용 중인 아이디입니다.',
    }
  } catch (e) {
    errorMessage.value = e.message
  }
}

const canGoNext = computed(() => {
  const f = form.value
  switch (step.value.type) {
    case 'form':
      return f.loginId && f.password && f.password === f.passwordConfirm
        && f.nickname && idCheck.value.available
    case 'choice':
      return f[step.value.key] !== null
    case 'consent':
      return f.agreePrivacy && f.agreeCommunity
    default:
      return false
  }
})

function select(value) {
  form.value[step.value.key] = value
}

function next() {
  errorMessage.value = ''
  if (stepIndex.value < STEPS.length - 1) stepIndex.value += 1
}

function prev() {
  errorMessage.value = ''
  if (stepIndex.value > 0) stepIndex.value -= 1
}

async function onSubmit() {
  errorMessage.value = ''
  submitting.value = true
  try {
    const { passwordConfirm, ...body } = form.value
    await signUp(body)
    router.push({ name: 'login' })
  } catch (e) {
    errorMessage.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <!-- 와이어프레임 03·04_회원가입 -->
  <div style="max-width:520px;margin:40px auto">
    <div style="display:flex;justify-content:space-between;margin-bottom:24px">
      <strong>회원가입</strong>
      <span class="muted">{{ stepIndex + 1 }} / {{ STEPS.length }} 단계</span>
    </div>

    <div class="progress"><div :style="{ width: progress + '%' }" /></div>

    <h2 style="text-align:center">{{ step.title }}</h2>
    <p v-if="step.subtitle" class="muted" style="text-align:center;margin-bottom:32px">
      {{ step.subtitle }}
    </p>

    <!-- 1단계: 계정 정보 -->
    <template v-if="step.type === 'form'">
      <div style="display:flex;gap:8px">
        <input v-model="form.loginId" class="field" placeholder="아이디"
               @input="idCheck = { checked: false, available: false, message: '' }" />
        <button class="btn" style="width:110px;margin-bottom:12px" type="button"
                :disabled="!form.loginId" @click="onCheckId">중복확인</button>
      </div>
      <p v-if="idCheck.checked" class="muted"
         :style="{ color: idCheck.available ? '#1a7f37' : '#d02f2f' }">{{ idCheck.message }}</p>

      <input v-model="form.password" class="field" type="password"
             placeholder="비밀번호 (영문·숫자·특수문자 8자 이상)" />
      <input v-model="form.passwordConfirm" class="field" type="password" placeholder="비밀번호 확인" />
      <p v-if="form.passwordConfirm && form.password !== form.passwordConfirm" class="error">
        비밀번호가 일치하지 않습니다.
      </p>

      <input v-model="form.nickname" class="field" placeholder="닉네임 (2~30자)" />
    </template>

    <!-- 선택형. 지역처럼 선택지가 많은 단계는 여러 열로 깝니다. -->
    <div v-else-if="step.type === 'choice'"
         :class="['choices', { grid: step.columns }]"
         :style="step.columns ? { gridTemplateColumns: `repeat(${step.columns}, 1fr)` } : null">
      <button v-for="option in step.options" :key="option.value" type="button" class="choice"
              :class="{ selected: form[step.key] === option.value }" @click="select(option.value)">
        {{ option.label }}
      </button>
    </div>

    <!-- 6단계: 동의 -->
    <template v-else>
      <p class="muted" style="text-align:center;margin-bottom:24px">
        입력하신 정보는 커뮤니티 내 대출 한도 비교 등 통계 기능에 활용됩니다
      </p>
      <label style="display:block;margin-bottom:12px">
        <input v-model="form.agreePrivacy" type="checkbox" /> [필수] 개인정보 수집 및 이용 동의
      </label>
      <label style="display:block;margin-bottom:12px">
        <input v-model="form.agreeCommunity" type="checkbox" />
        [필수] 커뮤니티 내 활동정보(대출 시뮬레이션 결과 등) 활용 동의
      </label>
      <label style="display:block;margin-bottom:12px">
        <input v-model="form.agreeMarketing" type="checkbox" /> [선택] 마케팅 정보 수신 동의
      </label>
    </template>

    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

    <div style="display:flex;gap:12px;margin-top:32px">
      <button class="btn btn-ghost" type="button" :disabled="stepIndex === 0" @click="prev">이전</button>
      <button v-if="!isLastStep" class="btn" type="button" :disabled="!canGoNext" @click="next">다음</button>
      <button v-else class="btn" type="button" :disabled="!canGoNext || submitting" @click="onSubmit">
        {{ submitting ? '가입 중…' : '동의하고 가입 완료' }}
      </button>
    </div>
  </div>
</template>
