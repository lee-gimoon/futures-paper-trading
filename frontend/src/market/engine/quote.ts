import type { OrderBookSnapshot } from '../../shared/types';

// 호가 snapshot에서 파생되는 네 값. 호가창(OrderBook)과 차트(PriceChart)가
// 똑같은 snapshot에서 똑같은 값을 쓰도록 계산을 여기 한 곳에 모은다.
//   bestBid  = bids 중 가장 높은 price
//   bestAsk  = asks 중 가장 낮은 price
//   midPrice = (bestBid + bestAsk) / 2
//   spread   = bestAsk - bestBid
export type Quote = {
  bestBid: number;
  bestAsk: number;
  midPrice: number;
  spread: number;
};

// 한쪽이라도 비어 있으면 계산할 수 없으므로 null. (수신 직후 잠깐 발생 가능)
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
