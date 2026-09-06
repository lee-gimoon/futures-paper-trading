/** 서버 호가를 가격 순서와 화면에서 사용할 기준 가격이 포함된 상태로 정리한다. */

import type {
  DepthView,
  OrderBookLevel,
  OrderBookSnapshot,
} from '@/types/market';

function cleanLevels(
  levels: OrderBookLevel[],
  descending: boolean,
): OrderBookLevel[] {
  return levels
    .filter(
      (level) =>
        Number.isFinite(level.price) &&
        Number.isFinite(level.quantity) &&
        level.price > 0 &&
        level.quantity > 0,
    )
    .sort((left, right) =>
      descending ? right.price - left.price : left.price - right.price,
    )
    .slice(0, 20);
}

export function normalizeDepth(snapshot: OrderBookSnapshot): DepthView {
  const bids = cleanLevels(snapshot.bids, true);
  const asks = cleanLevels(snapshot.asks, false);
  const bestBid = bids[0]?.price;
  const bestAsk = asks[0]?.price;
  const midPrice =
    bestBid !== undefined && bestAsk !== undefined
      ? (bestBid + bestAsk) / 2
      : null;

  return { ...snapshot, bids, asks, midPrice };
}
