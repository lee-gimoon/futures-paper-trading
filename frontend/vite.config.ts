// Vite 설정을 작성할 때 TypeScript 타입 추론을 도와주는 함수입니다.
import { defineConfig } from 'vite';

// React의 JSX와 Fast Refresh를 Vite에서 처리하게 해주는 플러그인입니다.
import react from '@vitejs/plugin-react';

// Vite 개발 서버와 프론트엔드 빌드에 적용할 설정을 반환합니다.
export default defineConfig({
  // Vite가 React 코드를 처리할 때 사용할 플러그인 목록입니다.
  plugins: [react()],

  // Vite 개발 서버(localhost:5173)의 동작을 설정하는 영역입니다.
  server: {
    // 개발 서버로 들어온 특정 요청을 다른 서버로 전달하는 규칙입니다.
    proxy: {
      // 요청 경로가 '/api'로 시작하는 경우 이 규칙을 적용합니다.
      '/api': {
        // '/api' 요청을 전달할 실제 백엔드 서버 주소입니다.
        target: 'http://localhost:8080',

        // 프록시 요청의 Host 헤더를 대상 서버(localhost:8080)에 맞춥니다.
        // 브라우저 주소창의 URL이 바뀌는 것은 아닙니다.
        changeOrigin: true,

        // 프록시 객체가 생성된 뒤 세부 이벤트를 설정할 수 있는 콜백입니다.
        configure: (proxy) => {
          // Spring Boot가 보낸 응답을 Vite 프록시가 받은 순간 발생하는 이벤트입니다.
          proxy.on('proxyRes', (proxyRes) => {
            // SSE 응답을 중간 프록시가 오래 모아두지 않도록 전달하는 힌트입니다.
            // 주로 Nginx 같은 프록시가 이 헤더를 인식합니다.
            proxyRes.headers['x-accel-buffering'] = 'no';
          });
        },
      },
    },
  },
});
