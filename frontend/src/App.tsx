import { useOrderBookStream } from './useOrderBookStream';
import { OrderBook } from './OrderBook';

// 화면 셸: 호가창만 표시.
// 백엔드가 @PostConstruct로 부팅 시 Binance WebSocket을 자동 연결하므로,
// 프론트엔드에서 시작 트리거를 보낼 필요가 없다. SSE 스트림만 구독하면 된다.
export default function App() {
  const snapshot = useOrderBookStream();

  return (
    <div className="app">
      <h1>BTCUSDT 호가창</h1>

      {snapshot ? (
        <OrderBook snapshot={snapshot} />
      ) : (
        <p className="empty">데이터 수신 대기 중...</p>
      )}
    </div>
  );
}
