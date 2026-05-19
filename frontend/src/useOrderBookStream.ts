import { useEffect, useState } from 'react';
import type { OrderBookSnapshot } from './types';

// React Hook 한 줄 요약: 컴포넌트가 마운트되면 백엔드 SSE에 연결하고,
// 받은 snapshot을 state로 들고 있는다. 컴포넌트가 사라지면 연결을 닫는다.
//
// EventSource: 브라우저 내장 SSE 클라이언트. 한 번 new로 만들면 자동으로 재연결까지 해준다.
// onmessage: 서버가 보낸 "data: {...}\n\n" 한 덩어리가 도착할 때마다 호출된다.
export function useOrderBookStream(): OrderBookSnapshot | null {
  const [snapshot, setSnapshot] = useState<OrderBookSnapshot | null>(null);

  useEffect(() => {
    // Vite proxy 덕분에 '/api/...'가 그대로 localhost:8080으로 전달된다.
    const eventSource = new EventSource('/api/binance-futures/btcusdt/depth/stream');

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
