<script setup>
import { computed } from 'vue'
import { parseRichText } from '@/utils/richText'

const props = defineProps({ text: { type: String, default: '' } })
const blocks = computed(() => parseRichText(props.text))
</script>

<template>
  <div class="rich">
    <template v-for="(b, i) in blocks" :key="i">
      <p v-if="b.type === 'lead'" class="lead">{{ b.text }}</p>

      <h4 v-else-if="b.type === 'section'" class="section">
        <span v-if="b.label" class="label">{{ b.label }}</span>
        <span v-if="b.text">{{ b.text }}</span>
      </h4>

      <p v-else-if="b.type === 'item'" class="item">
        <span v-if="b.label" class="tag">{{ b.label }}</span>{{ b.text }}
      </p>

      <p v-else-if="b.type === 'step'" class="step">
        <span class="marker">{{ b.marker }}</span>{{ b.text }}
      </p>

      <p v-else-if="b.type === 'note'" class="note">{{ b.text }}</p>

      <p v-else class="para">{{ b.text }}</p>
    </template>
  </div>
</template>

<style scoped>
.rich { font-size: 15px; line-height: 1.75; color: #333; }

/* 맨 앞 개요는 한 단계 크게 — 여기만 읽어도 무슨 정책인지 알게 합니다. */
.lead {
  font-size: 16px; line-height: 1.7; color: #1c1f23;
  padding: 16px 18px; background: var(--surface-soft); border-radius: 10px; margin-bottom: 22px;
}

.section {
  display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap;
  font-size: 15px; font-weight: 700; color: #1c1f23;
  margin: 22px 0 8px; padding-top: 4px;
}
.section:first-child { margin-top: 0; }
.label {
  font-size: 12px; font-weight: 700; color: var(--primary);
  background: var(--primary-soft); padding: 3px 9px; border-radius: 5px; white-space: nowrap;
}

.item { position: relative; padding-left: 15px; margin: 5px 0; color: #3d4148; }
.item::before {
  content: ''; position: absolute; left: 3px; top: 11px;
  width: 4px; height: 4px; border-radius: 50%; background: #c3c9d2;
}
.tag {
  display: inline-block; margin-right: 6px; font-weight: 600; color: #55606e;
}

.step { display: flex; gap: 9px; margin: 7px 0; color: #3d4148; }
.marker {
  flex-shrink: 0; width: 21px; height: 21px; margin-top: 2px;
  display: inline-flex; align-items: center; justify-content: center;
  background: var(--primary); color: #fff; border-radius: 50%;
  font-size: 11px; font-weight: 700;
}

/* 주의사항은 눈에 걸리게. 원문에서 ※ 는 대부분 예외·제한 조건입니다. */
.note {
  margin: 8px 0; padding: 10px 14px;
  background: #fffaf2; border-left: 3px solid #f0a53e;
  font-size: 13.5px; line-height: 1.65; color: #6b5a3e; border-radius: 0 6px 6px 0;
}

.para { margin: 10px 0; }
</style>
