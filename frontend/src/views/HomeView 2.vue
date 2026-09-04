<script setup>
import { RouterLink } from 'vue-router'

// 와이어프레임 01_메인페이지 — 영상 배너와 세 갈래 입구.
const ENTRIES = [
  { to: '/policies', label: '정보', photo: 'entry-info' },
  { to: '/properties', label: '매물', photo: 'entry-properties' },
  { to: '/community', label: '커뮤니티', photo: 'entry-community' },
]

// 배경 영상은 소리 없이 자동재생만 합니다(브라우저가 음소거 없는 자동재생을 막습니다).
</script>

<template>
  <section class="cover">
    <video class="cover-video" src="/hero.mp4" autoplay muted loop playsinline></video>
    <div class="cover-scrim" />

    <div class="cover-copy container">
      <h1>한눈에 모아보는<br />부동산 솔루션</h1>
      <p class="lead">청년 주거 정책 · 공공임대 · 실거래가 · 대출 한도를 한 화면에 모았습니다.</p>
    </div>
  </section>

  <section class="deck-wrap">
    <h2 class="deck-title">zip사가 하는 일</h2>
    <div class="deck">
      <RouterLink
        v-for="entry in ENTRIES"
        :key="entry.to"
        :to="entry.to"
        class="tile"
        :class="entry.photo"
      >
        <div class="tile-scrim" />
        <span class="tile-label">{{ entry.label }}</span>
      </RouterLink>
    </div>

    <footer class="site-footer">
      <div class="foot-brand">
        <img src="/logo-horizontal.png" alt="zip사" />
        <address>
          서울특별시 강남구 테헤란로 152<br />
          Tel 1800-0000　　Fax 02-000-0000<br />
          E-mail ZIPSA@zipsa.co.kr
        </address>
      </div>
      <div class="foot-legal">
        <p>COPYRIGHT ⓒ ZIP사. ALL RIGHTS RESERVED.</p>
        <RouterLink to="/">개인정보처리방침</RouterLink>
      </div>
    </footer>
  </section>
</template>

<style scoped>
/* ── 히어로 ──
   main 이 max-width 로 가운데 정렬돼 있어 뷰포트 폭으로 되밀어야 잘리지 않습니다. */
.cover {
  position: relative; overflow: hidden;
  width: 100vw; margin-left: calc(50% - 50vw); margin-top: -40px;
  height: 480px;
  background: #12151a; /* 영상 로드 전 대체색 */
  display: flex; align-items: center;
}
.cover-video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
/* 흰 글자가 밝은 하늘 배경 위에서도 읽히도록 어둡게 깔아줍니다. */
.cover-scrim {
  position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(0,0,0,.45), rgba(0,0,0,.15) 42%, rgba(0,0,0,.72));
}

/* flex 아이템이라 폭을 명시하지 않으면 콘텐츠 크기로 줄어들어 글이 가운데로 몰립니다. */
.cover-copy { position: relative; width: 100%; }
.cover-copy h1 {
  margin: 0 0 18px; font-size: 46px; font-weight: 800; line-height: 1.28;
  letter-spacing: -.03em; color: #fff; text-shadow: 0 2px 16px rgba(0,0,0,.35);
}
.cover-copy .lead {
  margin: 0; font-size: 16px; line-height: 1.7;
  color: rgba(255,255,255,.82); text-shadow: 0 1px 8px rgba(0,0,0,.4);
}

/* ── 카드 덱 ── */
.deck-wrap {
  position: relative;
  width: 100vw; margin-left: calc(50% - 50vw);
  background: #fff;
  padding: 72px 0 0;
}
.deck-title {
  margin: 0 0 30px; text-align: center;
  font-size: 24px; font-weight: 700; letter-spacing: -.02em; color: var(--text);
}

/* 세 장 모두 같은 폭. */
.deck {
  max-width: 1200px; margin: 0 auto; padding: 0 24px;
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px;
}

.tile {
  position: relative; display: flex; align-items: flex-end;
  min-height: 220px; padding: 20px 22px; overflow: hidden;
  background-size: cover; background-position: center;
  transition: transform .18s ease;
}

/* Figma 의 card_정보 · card_매물 · card_커뮤니티 에셋. */
.tile.entry-info { background-image: url('/card-info.png'); }
.tile.entry-properties { background-image: url('/card-properties.png'); }
.tile.entry-community { background-image: url('/card-community.png'); }

/* 사진 위에 글씨가 얹히므로 아래쪽을 진하게 깔아 대비를 확보합니다. */
.tile-scrim {
  position: absolute; inset: 0;
  background: linear-gradient(180deg, transparent 40%, rgba(10,12,16,.62) 100%);
  transition: background .18s ease;
}
.tile:hover { transform: translateY(-3px); }
.tile:hover .tile-scrim {
  background: linear-gradient(180deg, transparent 30%, rgba(10,12,16,.72) 100%);
}

.tile-label { position: relative; font-size: 19px; font-weight: 700; color: #fff; }

/* ── 푸터 ── */
.site-footer {
  margin-top: 72px; border-top: 1px solid var(--border); background: var(--surface-soft);
  padding: 34px 24px 28px;
  display: flex; flex-direction: column; gap: 18px; align-items: center; text-align: center;
}
.foot-brand { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.foot-brand img { height: 26px; opacity: .85; }
.foot-brand address { font-style: normal; font-size: 12.5px; line-height: 1.8; color: var(--muted); }
.foot-legal { font-size: 11.5px; color: #9aa0a8; display: flex; gap: 16px; align-items: center; }
.foot-legal a:hover { color: var(--primary); }

@media (max-width: 900px) {
  .cover { height: 380px; }
  .cover-copy h1 { font-size: 32px; }
  .deck { grid-template-columns: 1fr; }
}
</style>
