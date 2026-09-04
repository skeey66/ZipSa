<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createComment, deleteComment, deletePost, fetchPost, toggleLike,
} from '@/api/community'
import { CATEGORY_LABEL, shortDate } from '@/constants/community'
import BankBadges from '@/components/BankBadges.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const post = ref(null)
const comment = ref('')
const error = ref('')
const busy = ref(false)

async function load() {
  try {
    post.value = await fetchPost(route.params.id)
  } catch (e) {
    error.value = e.message ?? '게시글을 불러오지 못했습니다.'
  }
}

function requireLogin() {
  if (auth.isLoggedIn) return true
  router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

async function onLike() {
  if (!requireLogin() || busy.value) return
  busy.value = true
  try {
    const { liked } = await toggleLike(post.value.id)
    // 서버가 재계산한 값을 다시 받아옵니다. 화면에서 ±1 하면 DB 와 어긋날 수 있습니다.
    post.value.liked = liked
    const fresh = await fetchPost(post.value.id)
    post.value.likeCount = fresh.likeCount
  } finally {
    busy.value = false
  }
}

async function onComment() {
  if (!requireLogin() || !comment.value.trim() || busy.value) return
  busy.value = true
  try {
    await createComment(post.value.id, comment.value.trim())
    comment.value = ''
    await load()
  } catch (e) {
    error.value = e.message ?? '댓글을 등록하지 못했습니다.'
  } finally {
    busy.value = false
  }
}

async function onDeleteComment(id) {
  await deleteComment(id)
  await load()
}

async function onDeletePost() {
  await deletePost(post.value.id)
  router.push('/community')
}

onMounted(load)
</script>

<template>
  <section class="page" v-if="post">
    <nav class="crumb">
      <RouterLink to="/community">커뮤니티</RouterLink> <span>&gt;</span> 게시글
    </nav>

    <article class="post">
      <header>
        <span class="badge" :data-cat="post.category">{{ CATEGORY_LABEL[post.category] }}</span>
        <h1>{{ post.title }}</h1>
        <p class="meta">
          <BankBadges :codes="post.badges" :size="17" />{{ post.nickname }} ·
          {{ shortDate(post.createdAt) }} ·
          조회 {{ post.viewCount.toLocaleString() }}
          <template v-if="post.mine">
            <button class="del" @click="onDeletePost">삭제</button>
          </template>
        </p>
      </header>

      <div class="body">{{ post.content }}</div>

      <footer>
        <button class="like" :class="{ on: post.liked }" :disabled="busy" @click="onLike">
          ♥ 좋아요 {{ post.likeCount }}
        </button>
      </footer>
    </article>

    <section class="comments">
      <h2>댓글 {{ post.comments.length }}</h2>

      <ul v-if="post.comments.length">
        <li v-for="c in post.comments" :key="c.id">
          <div class="head">
            <strong>{{ c.nickname }}</strong>
            <span>{{ shortDate(c.createdAt) }}</span>
            <button v-if="c.mine" class="del" @click="onDeleteComment(c.id)">삭제</button>
          </div>
          <p>{{ c.content }}</p>
        </li>
      </ul>
      <p v-else class="empty">첫 댓글을 남겨보세요.</p>

      <form class="write" @submit.prevent="onComment">
        <textarea
          v-model="comment"
          rows="3"
          :placeholder="auth.isLoggedIn ? '댓글을 입력하세요' : '로그인 후 댓글을 쓸 수 있습니다'"
        />
        <button type="submit" :disabled="busy || !comment.trim()">등록</button>
      </form>
      <p v-if="error" class="error">{{ error }}</p>
    </section>
  </section>

  <p v-else-if="error" class="error">{{ error }}</p>
</template>

<style scoped>
.crumb { font-size: 13px; color: #8a8f98; margin-bottom: 14px; }
.crumb span { margin: 0 4px; }

.post { border-bottom: 1px solid #e6e8ec; padding-bottom: 24px; }
.post h1 { font-size: 26px; font-weight: 700; margin: 8px 0 10px; }
.meta { font-size: 13px; color: #8a8f98; }
.meta :deep(.badges) { margin-right: 5px; }
.badge {
  display: inline-block; padding: 3px 9px; border-radius: 4px;
  font-size: 12px; background: #eef0f3; color: #6b7079;
}
.badge[data-cat="LOAN"] { background: #fff1e6; color: #c2620e; }
.badge[data-cat="INFO"] { background: var(--primary-soft); color: var(--primary-strong); }
.badge[data-cat="QUESTION"] { background: #f0ebff; color: #6b46c1; }

.body { margin: 26px 0; font-size: 15px; line-height: 1.75; white-space: pre-wrap; color: #333; }

.like {
  padding: 9px 20px; border: 1px solid #d5d9e0; background: #fff;
  border-radius: 22px; cursor: pointer; font-size: 14px; color: #6b7079;
}
.like.on { background: #ffeef0; border-color: #e8637a; color: #d63a56; font-weight: 600; }

.comments { margin-top: 34px; }
.comments h2 { font-size: 16px; margin-bottom: 14px; }
.comments ul { list-style: none; padding: 0; }
.comments li { padding: 14px 0; border-bottom: 1px solid #f2f3f5; }
.comments .head { display: flex; align-items: center; gap: 10px; margin-bottom: 5px; }
.comments .head strong { font-size: 14px; }
.comments .head span { font-size: 12px; color: #9aa0a8; }
.comments li p { font-size: 14px; color: #444; line-height: 1.6; white-space: pre-wrap; }

.del { border: 0; background: none; color: #c0392b; font-size: 12px; cursor: pointer; margin-left: auto; }

.write { display: flex; gap: 10px; margin-top: 20px; }
.write textarea {
  flex: 1; padding: 11px 13px; border: 1px solid #d5d9e0; border-radius: 8px;
  font: inherit; font-size: 14px; resize: vertical; outline: none;
}
.write textarea:focus { border-color: var(--primary); }
.write button {
  align-self: flex-end; padding: 11px 22px; border: 0; border-radius: 8px;
  background: var(--primary); color: #fff; font-size: 14px; cursor: pointer;
}
.write button:disabled { opacity: .45; cursor: default; }
.empty { color: #999; font-size: 14px; padding: 14px 0; }
.error { color: #c0392b; font-size: 14px; margin-top: 10px; }
</style>
