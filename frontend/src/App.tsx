import { useState } from 'react';
import { useOrderBookStream } from './useOrderBookStream';
import { OrderBook } from './OrderBook';

// 화면 셸: 스트림 시작 버튼 + 호가창.
// useOrderBookStream이 SSE에 연결해 snapshot을 받아주고, 우리는 그걸 OrderBook에 넘기기만 한다.
export default function App() {
  const snapshot = useOrderBookStream();
  const [starting, setStarting] = useState(false);
  const [started, setStarted] = useState(false);

  // 백엔드의 POST /raw/start를 부른다. 백엔드가 그 시점에 Binance WebSocket에 연결한다.
  // 일단 한 번 연결되면 서버가 떠 있는 동안 계속 흐르므로 버튼은 한 번만 누르면 된다.
  const startStream = async () => {
    setStarting(true);
    try {
      const res = await fetch('/api/binance-futures/btcusdt/depth/raw/start', {
        method: 'POST',
      });
      if (res.ok) setStarted(true);
    } catch (err) {
      console.error('failed to start stream', err);
    } finally {
      setStarting(false);
    }
  };

  return (
    <div className="app">
      <h1>BTCUSDT 호가창</h1>

      <button onClick={startStream} disabled={starting || started}>
        {started ? '스트림 동작 중' : starting ? '시작 중...' : '스트림 시작'}
      </button>

      {snapshot ? (
        <OrderBook snapshot={snapshot} />
      ) : (
        <p className="empty">
          아직 데이터가 없습니다. 위 버튼을 눌러 스트림을 시작하세요.
        </p>
      )}
    </div>
  );
}
