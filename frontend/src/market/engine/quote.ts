import type { OrderBookSnapshot } from '../../shared/types';

// deriveQuote()가 만들어 반환하는 호가 요약값의 형태다.
//   - OrderBook은 spread를 표시한다.
//   - CandleChart는 bestAsk로 진행 중인 캔들을 갱신한다.
//   - TradingPanel은 midPrice를 현재가 표시, 미실현 PnL, 시장가 주문 금액 환산에 쓴다.
export type Quote = {
  bestBid: number;
  bestAsk: number;
  midPrice: number;
  spread: number;
};

// snapshot의 매수·매도 호가에서 화면 공통 기준값을 계산한다.
//   bestBid  = bids 중 가장 높은 price
//   bestAsk  = asks 중 가장 낮은 price
//   midPrice = (bestBid + bestAsk) / 2
//   spread   = bestAsk - bestBid
// 한쪽 호가가 비어 있으면 최우선 호가를 정할 수 없으므로 null을 반환한다.
export function deriveQuote(snapshot: OrderBookSnapshot): Quote | null {
  if (snapshot.bids.length === 0 || snapshot.asks.length === 0) return null;

  const bestBid = Math.max(...snapshot.bids.map((level) => level.price));
  const bestAsk = Math.min(...snapshot.asks.map((level) => level.price));

  return {
    bestBid,
    bestAsk,
    midPrice: (bestBid + bestAsk) / 2,
    spread: bestAsk - bestBid,
  };
}
