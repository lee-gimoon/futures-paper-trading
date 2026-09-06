import { useCallback, useEffect, useRef, useState } from 'react';

import { getLatestDepth } from '@/api/marketApi';
import { normalizeDepth } from '@/features/market/depthReducer';
import type { DepthView } from '@/types/market';

export function useLatestDepth() {
  const [depth, setDepth] = useState<DepthView | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestId = useRef(0);

  const refresh = useCallback(async (manual = true) => {
    const currentRequest = ++requestId.current;
    if (manual) setRefreshing(true);

    try {
      const snapshot = await getLatestDepth();
      if (currentRequest !== requestId.current) return;
      setDepth(normalizeDepth(snapshot));
      setError(null);
    } catch (caught) {
      if (currentRequest !== requestId.current) return;
      setError(
        caught instanceof Error
          ? caught.message
          : '최신 호가를 불러오지 못했습니다.',
      );
    } finally {
      if (currentRequest === requestId.current) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, []);

  useEffect(() => {
    const currentRequest = ++requestId.current;

    void getLatestDepth()
      .then((snapshot) => {
        if (currentRequest !== requestId.current) return;
        setDepth(normalizeDepth(snapshot));
        setError(null);
      })
      .catch((caught) => {
        if (currentRequest !== requestId.current) return;
        setError(
          caught instanceof Error
            ? caught.message
            : '최신 호가를 불러오지 못했습니다.',
        );
      })
      .finally(() => {
        if (currentRequest === requestId.current) setLoading(false);
      });

    return () => {
      requestId.current += 1;
    };
  }, []);

  return { depth, loading, refreshing, error, refresh };
}
