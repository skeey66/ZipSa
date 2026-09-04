<script setup>
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

// 와이어프레임 상단바는 정보 / 매물 / 커뮤니티 3개입니다.
// 「매물」은 호버하면 하위 두 화면이 펼쳐집니다(와이어프레임 주석: "매물 호버 시 노출").
const NAV = [
  {
    to: '/policies',
    label: '정보',
    children: [
      { to: '/policies', label: '청년 정책' },
      { to: '/news', label: '주거 뉴스' },
    ],
  },
  {
    to: '/properties',
    label: '매물',
    children: [
      { to: '/properties/public-housing', label: '공공임대 정보확인' },
      { to: '/transactions', label: '실거래가' },
    ],
  },
  { to: '/loan-prediction', label: '대출예측' },
  { to: '/community', label: '커뮤니티' },
]

async function onLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="site-header">
    <header class="header container">
      <RouterLink to="/" class="brand" aria-label="zip사 홈">
        <img src="/logo-horizontal.png" alt="zip사" class="mark" />
      </RouterLink>

      <nav class="nav">
        <div v-for="item in NAV" :key="item.to" class="nav-item">
          <RouterLink :to="item.to">{{ item.label }}</RouterLink>
          <ul v-if="item.children" class="submenu">
            <li v-for="c in item.children" :key="c.to">
              <RouterLink :to="c.to">{{ c.label }}</RouterLink>
            </li>
          </ul>
        </div>
      </nav>

      <div class="account">
        <template v-if="auth.isLoggedIn">
          <RouterLink to="/mypage">마이페이지</RouterLink>
          <button class="linklike" @click="onLogout">로그아웃</button>
        </template>
        <template v-else>
          <RouterLink to="/signup">회원가입</RouterLink>
          <RouterLink to="/login" class="strong">로그인</RouterLink>
        </template>
      </div>
    </header>
  </div>

  <main class="container" style="padding-top:40px">
    <RouterView />
  </main>
</template>

<style scoped>
.site-header { border-bottom: 1px solid var(--border); background: #fff; }
/* 로고 — Figma 의 zip-logo-horizontal 에셋(집 아이콘 + zip 오렌지 + 사). */
.brand { display: flex; align-items: center; }
.brand .mark { height: 32px; width: auto; display: block; }

.nav { display: flex; gap: 34px; }

/* 드롭다운이 열려 있는 동안 마우스가 빠지지 않도록 부모가 hover 를 유지합니다. */
.nav-item { position: relative; }
.nav-item > a { display: inline-block; padding: 10px 0; }

.submenu {
  position: absolute; left: 50%; transform: translateX(-50%);
  top: 100%; min-width: 160px; margin: 0; padding: 8px 0;
  list-style: none; background: #fff; border: 1px solid #e6e8ec; border-radius: 8px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, .1);
  opacity: 0; visibility: hidden; transition: opacity .12s ease;
  z-index: 100;
}
.nav-item:hover .submenu { opacity: 1; visibility: visible; }
.submenu a { display: block; padding: 8px 16px; font-size: 14px; white-space: nowrap; color: #444; }
.submenu a:hover { background: #f4f6f9; color: var(--primary); }

.account { display: flex; align-items: center; gap: 16px; }
.account .strong { color: var(--primary); font-weight: 600; }
.linklike { border: 0; background: none; cursor: pointer; font: inherit; color: inherit; padding: 0; }
</style>
