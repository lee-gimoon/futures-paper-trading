import { useOrderBookStream } from './useOrderBookStream';
import { OrderBook } from './OrderBook';
import { CandleChart } from './CandleChart';

// 화면 셸: 캔들 차트 + 호가창.
// - 캔들 차트: 브라우저가 바이낸스 kline에 직접 연결(백엔드 무관)하므로 항상 표시.
// - 호가창: 백엔드 SSE를 구독. snapshot이 도착해야 표시.
export default function App() {
  const snapshot = useOrderBookStream();

  return (
    <div className="app">
      <h1>BTCUSDT 차트 / 호가창</h1>

      <div className="layout">
        <div className="chart-col">
          <CandleChart />
        </div>
        <div className="book-col">
          {snapshot ? (
            <OrderBook snapshot={snapshot} />
          ) : (
            <p className="empty">호가 데이터 수신 대기 중...</p>
          )}
        </div>
      </div>
    </div>
  );
}
