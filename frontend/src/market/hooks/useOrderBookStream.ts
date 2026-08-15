import { useEffect, useState } from 'react';
import type { OrderBookSnapshot } from '../../shared/types';

// React Hook 한 줄 요약: 컴포넌트가 마운트되면 백엔드 SSE에 연결하고,
// 받은 snapshot을 state로 들고 있는다. 컴포넌트가 사라지면 연결을 닫는다.
export function useOrderBookStream(): OrderBookSnapshot | null {
  const [snapshot, setSnapshot] = useState<OrderBookSnapshot | null>(null);

  useEffect(() => {
    // Vite proxy 덕분에 '/api/...'가 그대로 localhost:8080으로 전달된다.
    // x-accel-buffering: no 헤더는 Nginx 같은 중간 프록시가 SSE를 모으지 말고 바로 전달하라는 뜻이다.
    // EventSource = 브라우저 내장 SSE 클라이언트. new EventSource(url)은 JavaScript 객체를 만들면서
    // 동시에 URL로 SSE HTTP GET 요청을 시작하고, 끝나지 않은 text/event-stream 응답을 계속 읽는다.
    // 연결이 일시적으로 끊기면 자동 재연결도 시도한다.
    const eventSource = new EventSource('/api/binance-futures/btcusdt/depth/stream');

    // EventSource가 "data: {...}\n\n"을 SSE 이벤트 하나로 해석할 때마다 'message' 이벤트를 발생시킨다.
    // 브라우저는 MessageEvent 객체를 event 매개변수로 전달하면서,
    // onmessage 프로퍼티에 등록된 콜백 함수를 호출한다.
    eventSource.onmessage = (event) => {
      const data: OrderBookSnapshot = JSON.parse(event.data);
      setSnapshot(data);
    };

    eventSource.onerror = (err) => {
      // 브라우저가 자동 재연결을 시도하므로 여기는 로깅만.
      console.error('SSE error', err);
    };

    // cleanup: 컴포넌트가 unmount되거나 effect가 다시 돌 때 호출된다.
    return () => {
      eventSource.close();
    };
  }, []); // 빈 배열 = 마운트 시 1회만 실행

  return snapshot;
}
