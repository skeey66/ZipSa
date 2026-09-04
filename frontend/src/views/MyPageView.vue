<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

const TAB_LABELS = ['내 정보', '내가 쓴 글', '대출 시뮬레이션 결과', '관심 정책·매물', '설정']
const AGE_LABELS = {
  AGE_20S_EARLY: '20대 초반', AGE_20S_LATE: '20대 후반',
  AGE_30S_EARLY: '30대 초반', AGE_30S_LATE: '30대 후반',
  AGE_10S: '10대', AGE_40S_OVER: '40대 이상',
}
const MARITAL_LABELS = { SINGLE: '미혼', MARRIED: '기혼' }
const JOB_LABELS = {
  STUDENT: '학생', EMPLOYEE: '직장인', SELF_EMPLOYED: '자영업자',
  JOB_SEEKER: '구직 중', ETC: '기타',
}

/** 회원가입 지역 단계와 같은 목록이어야 합니다. */
// 2026년 개편으로 광주·전남은 「전남광주」로 통합됐습니다.
const REGIONS = ['서울', '경기', '인천', '부산', '대구', '대전', '울산', '세종',
  '강원', '충북', '충남', '전북', '전남광주', '경북', '경남', '제주']

const auth = useAuthStore()
const activeTab = ref(0)
const errorMessage = ref('')

/* ── 거주 지역 수정 ──
   V12 이전에 가입한 회원은 지역이 없습니다. 지역이 없으면 정책 추천이
   전국 정책만 걸러내는 수준에 머물러서, 여기서 채울 수 있게 합니다. */
const editingRegion = ref(false)
const regionDraft = ref(null)
const savingRegion = ref(false)

function startEditRegion() {
  regionDraft.value = auth.profile?.region ?? null
  editingRegion.value = true
}

async function saveRegion() {
  if (!regionDraft.value || savingRegion.value) return
  savingRegion.value = true
  errorMessage.value = ''
  try {
    await auth.updateProfile({ region: regionDraft.value })
    editingRegion.value = false
  } catch (e) {
    errorMessage.value = e.message ?? '지역을 저장하지 못했습니다.'
  } finally {
    savingRegion.value = false
  }
}

onMounted(async () => {
  try {
    await auth.loadProfile()
  } catch (e) {
    errorMessage.value = e.message
  }
})
</script>

<template>
  <!-- 와이어프레임 05_마이페이지 -->
  <div style="display:flex;gap:40px">
    <aside style="width:180px;flex-shrink:0">
      <button v-for="(label, i) in TAB_LABELS" :key="label" type="button"
              style="display:block;width:100%;text-align:left;padding:12px 0;border:0;background:none;cursor:pointer"
              :style="{ color: activeTab === i ? 'var(--brand)' : 'var(--text)',
                        fontWeight: activeTab === i ? 700 : 400 }"
              @click="activeTab = i">
        {{ label }}
      </button>
    </aside>

    <section style="flex:1">
      <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

      <template v-if="activeTab === 0 && auth.profile">
        <div class="card" style="display:flex;gap:24px;align-items:center">
          <div style="width:60px;height:60px;border-radius:50%;background:#eee;flex-shrink:0" />
          <div>
            <h2 style="margin:0 0 8px">{{ auth.profile.nickname }} 님</h2>
            <p class="muted" style="margin:0 0 12px">
              {{ AGE_LABELS[auth.profile.ageRange] }} ·
              {{ MARITAL_LABELS[auth.profile.maritalStatus] }} ·
              {{ JOB_LABELS[auth.profile.job] }}
              <template v-if="auth.profile.region"> · {{ auth.profile.region }}</template>
            </p>

            <!-- 지역이 없으면 추천 품질이 크게 떨어지므로 그 사실을 알려줍니다. -->
            <div v-if="!editingRegion" class="region-row">
              <template v-if="auth.profile.region">
                <span class="chip">거주 {{ auth.profile.region }}</span>
                <button class="linkish" @click="startEditRegion">변경</button>
              </template>
              <template v-else>
                <span class="warn">거주 지역이 없어 지역 맞춤 정책을 받지 못하고 있습니다.</span>
                <button class="linkish" @click="startEditRegion">지금 설정</button>
              </template>
            </div>

            <div v-else class="region-edit">
              <div class="region-grid">
                <button v-for="r in REGIONS" :key="r" type="button"
                        :class="{ on: regionDraft === r }" @click="regionDraft = r">{{ r }}</button>
              </div>
              <div class="region-actions">
                <button class="linkish" @click="editingRegion = false">취소</button>
                <button class="save" :disabled="!regionDraft || savingRegion" @click="saveRegion">
                  {{ savingRegion ? '저장 중…' : '저장' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <h3 style="margin-top:40px">최근 활동</h3>
        <div class="card">
          <p v-if="!auth.profile.recentActivities?.length" class="muted" style="margin:0">
            아직 활동 내역이 없습니다.
          </p>
          <p v-for="activity in auth.profile.recentActivities" :key="activity.occurredAt"
             style="margin:0;padding:12px 0;border-bottom:1px solid var(--border)">
            {{ activity.title }}
          </p>
        </div>
      </template>

      <div v-else class="card">
        <p class="muted" style="margin:0">「{{ TAB_LABELS[activeTab] }}」 탭은 담당 도메인 구현 후 연결됩니다.</p>
      </div>
    </section>
  </div>
</template>
