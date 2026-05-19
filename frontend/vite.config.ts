import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Vite 개발 서버는 5173 포트, 백엔드는 8080 포트.
// 그냥 fetch하면 브라우저가 CORS로 막는다. 그래서 Vite에 프록시를 둬서
// 프론트가 '/api/...'로 요청하면 Vite가 가로채 http://localhost:8080으로 그대로 넘긴다.
// 브라우저 입장에서는 같은 출처(localhost:5173)와 통신하는 모양이라 CORS가 안 걸린다.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
});
