import { useCallback, useRef, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { AppState } from 'react-native';

import * as paperApi from '@/api/paperApi';
import type { Fill, Order } from '@/types/paper';

export function usePaperOrders(enabled = true) {
  const [orders, setOrders] = useState<Order[]>([]);
  const [fills, setFills] = useState<Fill[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cancelingId, setCancelingId] = useState<number | null>(null);
  const requestId = useRef(0);
  const canceling = useRef(false);

  const refresh = useCallback(
    async (manual = true) => {
      if (!enabled) return;
      const currentRequest = ++requestId.current;
      if (manual) setRefreshing(true);

      try {
        const [nextOrders, nextFills] = await Promise.all([
          paperApi.listOrders(),
          paperApi.listFills(),
        ]);
        if (currentRequest !== requestId.current) return;
        setOrders(nextOrders);
        setFills([...nextFills].reverse());
        setError(null);
      } catch (caught) {
        if (currentRequest !== requestId.current) return;
        setError(
          caught instanceof Error
            ? caught.message
            : '주문 내역을 불러오지 못했습니다.',
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
        setOrders([]);
        setFills([]);
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
        // 서버의 지정가 체결 결과를 현재 탭에 반영한다.
        timer = setInterval(() => void refresh(false), 3000);
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

  const cancel = useCallback(
    async (id: number) => {
      if (!enabled || canceling.current) return false;
      canceling.current = true;
      setCancelingId(id);
      setError(null);
      try {
        await paperApi.cancelOrder(id);
        await refresh(false);
        return true;
      } catch (caught) {
        setError(
          caught instanceof Error
            ? caught.message
            : '주문을 취소하지 못했습니다.',
        );
        return false;
      } finally {
        setCancelingId(null);
        canceling.current = false;
      }
    },
    [enabled, refresh],
  );

  return {
    orders,
    fills,
    loading,
    refreshing,
    error,
    cancelingId,
    refresh,
    cancel,
  };
}
