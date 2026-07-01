import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Vite 개발 서버는 5173 포트, 백엔드는 8080 포트.
// 그냥 fetch하면 브라우저가 CORS로 막는다. 그래서 Vite에 프록시를 둬서
// 프론트가 '/api/...'로 요청하면 Vite가 가로채 http://localhost:8080으로 그대로 넘긴다.
// 브라우저 입장에서는 같은 출처(localhost:5173)와 통신하는 모양이라 CORS가 안 걸린다.
//
// ★ SSE buffer 비활성:
// 기본 proxy 동작은 응답을 buffer하는 경향이 있어, SSE의 첫 chunk만 흘러오고
// 그 다음 chunk가 buffer에 갇혀 화면이 첫 데이터 1장으로 멈추는 현상이 있었다.
// configure 훅에서 응답 헤더에 'X-Accel-Buffering: no'를 박아 buffer를 끈다.
// (이 헤더는 nginx 관례지만 http-proxy도 streaming 응답에 대해 buffer 비활성 단서로 본다.)
//
// buffer 켜기 (기본):
//   장점 - TCP packet/시스템 콜 절약, 압축 효율 ↑, 동접 처리량 ↑
//   단점 - chunk가 모일 때까지 지연 발생, 실시간 흐름 깨짐
//   상황 - 블로그·파일 다운로드 등 지연 무관한 일반 응답
//
// buffer 끄기 (지금):
//   장점 - chunk 도착 즉시 통과, 지연 거의 0
//   단점 - packet 수 ↑, CPU/대역폭 비용 ↑ (사용자 적으면 무시 가능)
//   상황 - 호가창·체결·게임 입력 등 실시간성이 효율보다 비싼 시스템
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            // SSE chunk가 즉시 흘러나가도록 buffer 비활성
            proxyRes.headers['x-accel-buffering'] = 'no';
          });
        },
      },
    },
  },
});
