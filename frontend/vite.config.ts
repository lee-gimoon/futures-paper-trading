// Vite 설정을 만들 때 사용하는 함수입니다.
import { defineConfig } from 'vite';

// Vite에서 React 코드와 JSX를 처리하기 위한 플러그인입니다.
import react from '@vitejs/plugin-react';

// export default의 의미
// export는 이 파일의 값을 다른 곳에서 사용할 수 있게 내보내는 JavaScript 문법입니다.
// default는 이 파일의 대표값 하나를 내보낸다는 의미입니다.
// export default config;
// 이 뜻은: 이 파일의 기본값으로 config를 내보내겠다.
// 아래에서는 config 대신 Vite 설정 객체를 기본값으로 내보냅니다.
export default defineConfig({
  // Vite가 React 코드를 처리할 때 사용할 플러그인 목록입니다.
  plugins: [react()],

  // Vite 개발 서버의 동작을 설정하는 영역입니다.
  server: {
    // 개발 서버로 들어온 요청을 다른 서버로 전달하는 프록시 설정입니다.
    proxy: {
      // 요청 경로가 '/api'로 시작하는 경우 이 규칙을 적용합니다.
      '/api': {
        // '/api' 요청을 전달할 실제 백엔드 서버 주소입니다.
        target: 'http://localhost:8080',

        // 프록시 요청의 Host 헤더를 대상 서버에 맞춥니다.
        // 브라우저 주소창의 URL이 바뀌는 것은 아닙니다.
        changeOrigin: true,

        // 프록시 객체가 생성된 뒤 이벤트와 세부 동작을 설정하는 콜백입니다.
        configure: (proxy) => {
          // Spring Boot가 보낸 응답을 Vite 프록시가 받은 순간 발생하는 이벤트입니다.
          proxy.on('proxyRes', (proxyRes) => {
            // SSE 응답을 프록시가 버퍼링하지 않고 바로 전달하도록 설정합니다.
            // 주로 Nginx와 같은 프록시 서버에서 인식하는 헤더입니다.
            proxyRes.headers['x-accel-buffering'] = 'no';
          });
        },
      },
    },
  },
});
