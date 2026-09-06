/** 공개 BTCUSDT 호가 조회 요청을 제공한다. */

import { apiFetch, readJson } from '@/api/client';
import type { OrderBookSnapshot } from '@/types/market';

export async function getLatestDepth(): Promise<OrderBookSnapshot> {
  const response = await apiFetch('/api/binance-futures/btcusdt/depth/latest');
  return readJson(response, '최신 호가를 불러오지 못했습니다.');
}
