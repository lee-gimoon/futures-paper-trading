import { useOrderBookStream } from './useOrderBookStream';
import { OrderBook } from './OrderBook';
import { CandleChart } from './CandleChart';

// 화면 셸: 캔들 차트 + 호가창.
// - 캔들 차트: 과거 봉은 바이낸스 kline, 진행 봉은 호가 snapshot(mid)으로 갱신.
// - 호가창: 백엔드 SSE를 구독. snapshot이 도착해야 표시.
// 같은 snapshot을 둘 다에 넘기므로 차트 진행 봉과 호가창 가격이 일치한다.
export default function App() {
  const snapshot = useOrderBookStream();

  return (
    <div className="app">
      <h1>BTCUSDT 차트 / 호가창</h1>

      <div className="layout">
        <div className="chart-col">
          <CandleChart snapshot={snapshot} />
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
