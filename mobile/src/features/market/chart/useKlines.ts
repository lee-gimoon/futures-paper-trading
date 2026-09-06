/** 선택한 간격이 시작되거나 바뀔 때 Binance REST 과거 캔들을 한 번 불러온다. */
import { useCallback, useEffect, useState } from 'react';

import { getKlines } from '@/api/binanceApi';
import type { Kline, KlineInterval } from '@/types/chart';

export function useKlines(interval: KlineInterval) {
  const [result, setResult] = useState<{
    requestKey: string;
    interval: KlineInterval;
    items: Kline[];
    error: string | null;
  } | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const requestKey = `${interval}:${reloadKey}`;

  useEffect(() => {
    const controller = new AbortController();

    void getKlines(interval, 80, controller.signal)
      .then((items) => {
        if (!controller.signal.aborted) {
          setResult({ requestKey, interval, items, error: null });
        }
      })
      .catch((caught) => {
        if (!controller.signal.aborted) {
          setResult({
            requestKey,
            interval,
            items: [],
            error:
              caught instanceof Error
                ? caught.message
                : '과거 캔들을 불러오지 못했습니다.',
          });
        }
      });

    return () => controller.abort();
  }, [interval, requestKey]);

  return {
    klines: result?.interval === interval ? result.items : [],
    loading: result?.requestKey !== requestKey,
    error: result?.requestKey === requestKey ? result.error : null,
    refresh: useCallback(() => setReloadKey((value) => value + 1), []),
  };
}
