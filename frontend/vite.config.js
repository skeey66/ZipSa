import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  // .env 는 리포 루트에 하나만 둔다(백엔드·크롤러와 공유).
  // 이 설정이 없으면 Vite 는 frontend/.env 만 찾아서 VITE_* 가 전부 undefined 가 된다.
  envDir: fileURLToPath(new URL('..', import.meta.url)),
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // 개발 중에는 프록시로 CORS 를 우회한다. 운영에서는 VITE_API_BASE_URL 로 직접 지정.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
