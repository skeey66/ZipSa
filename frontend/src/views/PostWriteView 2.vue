<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { createPost } from '@/api/community'
import { POST_CATEGORIES } from '@/constants/community'

const router = useRouter()
// 「전체」는 필터용 값이라 작성 화면에서는 뺍니다.
const CATEGORIES = POST_CATEGORIES.filter((c) => c.value)

const title = ref('')
const content = ref('')
const category = ref('FREE')
const error = ref('')
const busy = ref(false)

async function onSubmit() {
  if (busy.value) return
  error.value = ''
  busy.value = true
  try {
    const { postId } = await createPost({
      title: title.value.trim(),
      content: content.value.trim(),
      category: category.value,
    })
    router.replace(`/community/${postId}`)
  } catch (e) {
    error.value = e.message ?? '등록하지 못했습니다.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="page">
    <nav class="crumb">
      <RouterLink to="/community">커뮤니티</RouterLink> <span>&gt;</span> 글쓰기
    </nav>
    <h1>글쓰기</h1>

    <form @submit.prevent="onSubmit">
      <div class="row">
        <label>카테고리</label>
        <div class="cats">
          <button
            v-for="c in CATEGORIES"
            :key="c.value"
            type="button"
            :class="{ on: c.value === category }"
            @click="category = c.value"
          >
            {{ c.label }}
          </button>
        </div>
      </div>

      <div class="row">
        <label for="title">제목</label>
        <input id="title" v-model="title" maxlength="200" placeholder="제목을 입력하세요" />
      </div>

      <div class="row">
        <label for="content">내용</label>
        <textarea id="content" v-model="content" rows="14" placeholder="내용을 입력하세요" />
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <div class="actions">
        <RouterLink to="/community" class="cancel">취소</RouterLink>
        <button type="submit" :disabled="busy || !title.trim() || !content.trim()">등록</button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.crumb { font-size: 13px; color: #8a8f98; margin-bottom: 6px; }
.crumb span { margin: 0 4px; }
h1 { font-size: 28px; font-weight: 700; margin-bottom: 24px; }

.row { margin-bottom: 20px; }
.row label { display: block; font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.row input, .row textarea {
  width: 100%; padding: 12px 14px; border: 1px solid #d5d9e0; border-radius: 8px;
  font: inherit; font-size: 15px; outline: none;
}
.row textarea { resize: vertical; line-height: 1.7; }
.row input:focus, .row textarea:focus { border-color: var(--primary); }

.cats { display: flex; gap: 8px; }
.cats button {
  padding: 8px 18px; border: 1px solid #d5d9e0; background: #fff;
  border-radius: 20px; cursor: pointer; font-size: 14px;
}
.cats button.on { background: var(--primary); border-color: var(--primary); color: #fff; }

.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 26px; }
.actions .cancel {
  padding: 11px 24px; border: 1px solid #d5d9e0; border-radius: 8px;
  font-size: 14px; color: #555;
}
.actions button {
  padding: 11px 28px; border: 0; border-radius: 8px;
  background: var(--primary); color: #fff; font-size: 14px; font-weight: 600; cursor: pointer;
}
.actions button:disabled { opacity: .45; cursor: default; }
.error { color: #c0392b; font-size: 14px; }
</style>
