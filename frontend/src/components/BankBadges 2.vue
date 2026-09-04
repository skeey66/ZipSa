<script setup>
import { BANK_NAME, badgeSrc } from '@/constants/bank'

// 대출 결과를 등록한 회원에게만 붙습니다.
// "실제로 받아본 사람" 이라는 신호라 커뮤니티에서 글의 신뢰도가 달라집니다.
defineProps({
  codes: { type: Array, default: () => [] },
  size: { type: Number, default: 18 },
})
</script>

<template>
  <span v-if="codes.length" class="badges">
    <img
      v-for="code in codes"
      :key="code"
      :src="badgeSrc(code)"
      :alt="`${BANK_NAME[code]} 대출 인증`"
      :title="`${BANK_NAME[code]} 대출 결과를 등록한 회원`"
      :width="size"
      :height="size"
      loading="lazy"
    />
  </span>
</template>

<style scoped>
.badges { display: inline-flex; align-items: center; gap: 3px; vertical-align: middle; }
.badges img { border-radius: 50%; display: block; }
</style>
