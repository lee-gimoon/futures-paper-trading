import { useCallback, useRef, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { AppState } from 'react-native';

import * as paperApi from '@/api/paperApi';
import type { Portfolio } from '@/types/paper';

export function usePaperAccount(enabled = true) {
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [changingLeverage, setChangingLeverage] = useState(false);
  const [closingPosition, setClosingPosition] = useState(false);
  const requestId = useRef(0);
  const mutating = useRef(false);

  const refresh = useCallback(
    async (manual = true) => {
      if (!enabled || mutating.current) return;
      const currentRequest = ++requestId.current;
      if (manual) setRefreshing(true);

      try {
        const nextPortfolio = await paperApi.fetchPortfolio();
        if (currentRequest !== requestId.current) return;
        setPortfolio(nextPortfolio);
        setError(null);
      } catch (caught) {
        if (currentRequest !== requestId.current) return;
        setError(
          caught instanceof Error
            ? caught.message
            : '계좌 정보를 불러오지 못했습니다.',
        );
      } finally {
        if (currentRequest === requestId.current) {
          setLoading(false);
          setRefreshing(false);
        }
      }
    },
    [enabled],
  );

  useFocusEffect(
    useCallback(() => {
      if (!enabled) {
        requestId.current += 1;
        setPortfolio(null);
        setLoading(false);
        setRefreshing(false);
        setError(null);
        return;
      }
      setLoading(true);
      let timer: ReturnType<typeof setInterval> | null = null;

      const stopPolling = () => {
        if (timer) clearInterval(timer);
        timer = null;
      };
      const startPolling = () => {
        if (timer) return;
        void refresh(false);
        timer = setInterval(() => void refresh(false), 5000);
      };

      if (AppState.currentState === 'active') startPolling();
      const subscription = AppState.addEventListener('change', (nextState) => {
        if (nextState === 'active') startPolling();
        else stopPolling();
      });

      return () => {
        stopPolling();
        subscription.remove();
        requestId.current += 1;
      };
    }, [enabled, refresh]),
  );

  const changeLeverage = useCallback(
    async (leverage: number) => {
      if (!enabled || mutating.current) return false;
      mutating.current = true;
      requestId.current += 1;
      setChangingLeverage(true);
      setError(null);
      try {
        setPortfolio(await paperApi.setLeverage(leverage));
        return true;
      } catch (caught) {
        setError(
          caught instanceof Error
            ? caught.message
            : '레버리지를 변경하지 못했습니다.',
        );
        return false;
      } finally {
        setChangingLeverage(false);
        mutating.current = false;
      }
    },
    [enabled],
  );

  const closePosition = useCallback(async () => {
    if (!portfolio?.position || closingPosition) return;

    setClosingPosition(true);
    setError(null);
    try {
      await paperApi.createOrder({
        side: portfolio.position.side === 'LONG' ? 'SELL' : 'BUY',
        type: 'MARKET',
        quantity: portfolio.position.quantity,
      });
      await refresh(false);
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : '포지션을 종료하지 못했습니다.',
      );
    } finally {
      setClosingPosition(false);
    }
  }, [closingPosition, portfolio, refresh]);

  return {
    portfolio,
    loading,
    refreshing,
    error,
    changingLeverage,
    closingPosition,
    refresh,
    changeLeverage,
    closePosition,
  };
}
