/**
 * BTCUSDT 차트의 처음 과거 캔들을 채울 공개 데이터만 Binance REST에서 직접 조회한다.
 * 호가·주문·계좌·인증은 이 파일을 사용하지 않고 Spring API를 사용한다.
 */
import { fetch } from 'expo/fetch';

import type { Kline, KlineInterval } from '@/types/chart';

const KLINE_REST_URL = 'https://fapi.binance.com/fapi/v1/klines';

export async function getKlines(
  interval: KlineInterval,
  limit = 80,
  signal?: AbortSignal,
): Promise<Kline[]> {
  const controller = new AbortController();
  const abort = () => controller.abort();
  if (signal?.aborted) controller.abort();
  signal?.addEventListener('abort', abort);
  const timeout = setTimeout(abort, 10_000);

  try {
    const query = new URLSearchParams({
      symbol: 'BTCUSDT',
      interval,
      limit: String(limit),
    });
    const response = await fetch(`${KLINE_REST_URL}?${query}`, {
      signal: controller.signal,
    });
    if (!response.ok) {
      throw new Error('BTCUSDT 캔들을 불러오지 못했습니다.');
    }

    const body: unknown = await response.json();
    if (!Array.isArray(body)) {
      throw new Error('캔들 응답 형식이 올바르지 않습니다.');
    }

    const klines = body
      .filter(Array.isArray)
      .map((row) => ({
        openTime: Number(row[0]),
        open: Number(row[1]),
        high: Number(row[2]),
        low: Number(row[3]),
        close: Number(row[4]),
        volume: Number(row[5]),
        closeTime: Number(row[6]),
      }))
      .filter(
        (item) =>
          Object.values(item).every(Number.isFinite) &&
          item.low > 0 &&
          item.volume >= 0,
      );

    if (klines.length === 0) {
      throw new Error('표시할 BTCUSDT 캔들이 없습니다.');
    }
    return klines;
  } finally {
    clearTimeout(timeout);
    signal?.removeEventListener('abort', abort);
  }
}
