<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchPosts } from '@/api/community'
import { CATEGORY_LABEL, POST_CATEGORIES, shortDate } from '@/constants/community'
import BankBadges from '@/components/BankBadges.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const posts = ref([])
const category = ref(null)
const keyword = ref('')
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await fetchPosts({
      category: category.value,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: 20,
    })
    posts.value = res.content
    totalPages.value = res.totalPages
    totalElements.value = res.totalElements
  } finally {
    loading.value = false
  }
}

function selectCategory(value) {
  category.value = value
  page.value = 0
}

function onWrite() {
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: '/community/write' } })
    return
  }
  router.push('/community/write')
}

let timer
watch(keyword, () => {
  clearTimeout(timer)
  timer = setTimeout(() => {
    page.value = 0
    load()
  }, 250)
})
watch([category, page], load)
onMounted(load)
</script>

<template>
  <section class="page">
    <nav class="crumb">커뮤니티 <span>&gt;</span> 게시판</nav>
    <h1>커뮤니티</h1>

    <div class="toolbar">
      <label class="search">
        <input v-model="keyword" type="search" placeholder="제목 검색" />
        <span aria-hidden="true">🔍</span>
      </label>
      <button class="write" @click="onWrite">글쓰기</button>
    </div>

    <div class="layout">
      <aside class="cats">
        <h2>카테고리</h2>
        <ul>
          <li v-for="c in POST_CATEGORIES" :key="c.label">
            <button :class="{ on: c.value === category }" @click="selectCategory(c.value)">
              {{ c.label }}
            </button>
          </li>
        </ul>
      </aside>

      <div class="board">
        <p class="total">전체 {{ totalElements.toLocaleString() }}건</p>

        <table class="posts" v-if="posts.length">
          <thead>
            <tr>
              <th class="c-title">제목</th>
              <th class="c-user">작성자</th>
              <th class="c-num">조회</th>
              <th class="c-num">좋아요</th>
              <th class="c-date">작성일</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in posts" :key="p.id" @click="router.push(`/community/${p.id}`)">
              <td class="c-title">
                <span class="badge" :data-cat="p.category">{{ CATEGORY_LABEL[p.category] }}</span>
                <span class="title">{{ p.title }}</span>
                <span v-if="p.commentCount" class="cmt">[{{ p.commentCount }}]</span>
              </td>
              <td class="c-user">
                <div class="author">
                  <BankBadges :codes="p.badges" :size="16" />
                  <span class="nick">{{ p.nickname }}</span>
                </div>
              </td>
              <td class="c-num">{{ p.viewCount.toLocaleString() }}</td>
              <td class="c-num">{{ p.likeCount }}</td>
              <td class="c-date">{{ shortDate(p.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else-if="!loading" class="empty">게시글이 없습니다.</p>

        <div class="pager" v-if="totalPages > 1">
          <button :disabled="page === 0" @click="page--">이전</button>
          <span>{{ page + 1 }} / {{ totalPages }}</span>
          <button :disabled="page >= totalPages - 1" @click="page++">다음</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.crumb { font-size: 13px; color: #8a8f98; margin-bottom: 6px; }
.crumb span { margin: 0 4px; }
h1 { font-size: 30px; font-weight: 700; margin-bottom: 20px; }

.toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 18px; }
.search { position: relative; flex: 1; max-width: 420px; }
.search input {
  width: 100%; padding: 11px 38px 11px 14px; font-size: 14px;
  border: 1px solid #d5d9e0; border-radius: 8px; outline: none;
}
.search input:focus { border-color: var(--primary); }
.search span { position: absolute; right: 13px; top: 50%; transform: translateY(-50%); opacity: .5; }
.write {
  margin-left: auto; padding: 10px 22px; border: 0; border-radius: 8px;
  background: var(--primary); color: #fff; font-size: 14px; font-weight: 600; cursor: pointer;
}

.layout { display: grid; grid-template-columns: 140px minmax(0, 1fr); gap: 28px; align-items: start; }
.cats h2 { font-size: 14px; margin-bottom: 10px; }
.cats ul { list-style: none; padding: 0; }
.cats button {
  width: 100%; text-align: left; padding: 8px 10px; border: 0; background: none;
  border-radius: 6px; cursor: pointer; font-size: 14px; color: #555;
}
.cats button:hover { background: #f2f4f7; }
.cats button.on { background: var(--primary-soft); color: var(--primary); font-weight: 600; }

.total { font-size: 13px; color: #8a8f98; margin-bottom: 8px; }
.posts { width: 100%; table-layout: fixed; border-collapse: collapse; font-size: 14px; }
.posts th, .posts td { padding: 12px 8px; border-bottom: 1px solid #eef0f3; }
.posts th { color: #8a8f98; font-weight: 500; font-size: 13px; border-bottom: 1px solid #d9dde3; }
.posts tbody tr { cursor: pointer; }
.posts tbody tr:hover { background: #fafbfc; }
.c-title { text-align: left; }
/* 뱃지를 닉네임 왼쪽에 둡니다. 오른쪽에 두면 칸이 좁아 아래로 밀립니다. */
/* ⚠️ <td> 에 직접 display:flex 를 주면 테이블 레이아웃이 깨져서
   나머지 열의 폭 계산이 어긋납니다. 안에 래퍼를 두고 거기에 flex 를 겁니다. */
.c-user { width: 200px; color: #6b7079; }
.author { display: flex; align-items: center; gap: 6px; min-width: 0; }
.author .nick { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.c-num { width: 64px; text-align: right; color: #8a8f98; font-variant-numeric: tabular-nums; }
.c-date { width: 92px; text-align: right; color: #8a8f98; }

.badge {
  display: inline-block; min-width: 34px; padding: 2px 7px; margin-right: 8px;
  border-radius: 4px; font-size: 11px; text-align: center; background: #eef0f3; color: #6b7079;
}
.badge[data-cat="LOAN"] { background: #fff1e6; color: #c2620e; }
.badge[data-cat="INFO"] { background: var(--primary-soft); color: var(--primary-strong); }
.badge[data-cat="QUESTION"] { background: #f0ebff; color: #6b46c1; }
.title { color: #222; }
.c-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmt { margin-left: 6px; color: var(--primary); font-size: 12px; font-weight: 600; }

.pager { display: flex; align-items: center; justify-content: center; gap: 14px; margin-top: 22px; }
.pager button {
  padding: 7px 16px; border: 1px solid #d5d9e0; background: #fff;
  border-radius: 6px; cursor: pointer; font-size: 13px;
}
.pager button:disabled { opacity: .4; cursor: default; }
.pager span { font-size: 13px; color: #6b7079; }
.empty { color: #888; font-size: 14px; padding: 30px 0; text-align: center; }
</style>
