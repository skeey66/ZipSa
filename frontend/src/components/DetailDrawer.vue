<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '' },
})
const emit = defineEmits(['close'])

const panel = ref(null)

function onKey(e) {
  if (e.key === 'Escape') emit('close')
}

/**
 * 패널이 열려 있는 동안 뒤 페이지가 같이 스크롤되면 안 됩니다.
 * overflow 를 잠그되, 원래 값을 기억했다가 되돌립니다(다른 곳에서 이미 잠갔을 수 있음).
 */
let savedOverflow = ''
watch(() => props.open, (open) => {
  if (open) {
    savedOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    // 열릴 때 패널로 초점을 옮겨야 키보드 사용자가 바로 읽고 ESC 로 닫을 수 있습니다.
    requestAnimationFrame(() => panel.value?.focus())
  } else {
    document.body.style.overflow = savedOverflow
  }
})

onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  document.body.style.overflow = savedOverflow
})
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open" class="backdrop" @click.self="emit('close')">
        <div
          ref="panel"
          class="panel"
          role="dialog"
          aria-modal="true"
          :aria-label="title"
          tabindex="-1"
        >
          <header class="bar">
            <slot name="header" />
            <button class="close" aria-label="닫기" @click="emit('close')">✕</button>
          </header>

          <div class="scroll">
            <slot />
          </div>

          <footer v-if="$slots.footer" class="foot">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.backdrop {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(17, 20, 24, .45);
  display: flex; align-items: center; justify-content: center;
  padding: 32px 20px;
}

/* 풀스크린이 아니라 가운데 패널. 뒤 화면이 비치도록 여백을 남깁니다. */
.panel {
  width: 100%; max-width: 780px; max-height: 86vh;
  display: flex; flex-direction: column;
  background: #fff; border-radius: 14px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, .25);
  outline: none;
}

.bar {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 22px 26px 16px; border-bottom: 1px solid #eef0f3;
}
.close {
  flex-shrink: 0; margin-left: auto; width: 30px; height: 30px;
  border: 0; background: #f2f4f7; border-radius: 8px;
  cursor: pointer; color: #6b7079; font-size: 13px;
}
.close:hover { background: #e6e9ee; color: #333; }

.scroll { overflow-y: auto; padding: 22px 26px 26px; }
.foot { padding: 16px 26px; border-top: 1px solid #eef0f3; background: #fafbfc; border-radius: 0 0 14px 14px; }

.fade-enter-active, .fade-leave-active { transition: opacity .16s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.fade-enter-active .panel { transition: transform .16s ease; }
.fade-enter-from .panel { transform: translateY(10px); }

@media (max-width: 720px) {
  .backdrop { padding: 0; align-items: flex-end; }
  .panel { max-width: none; max-height: 92vh; border-radius: 14px 14px 0 0; }
}
</style>
