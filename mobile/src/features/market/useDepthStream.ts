/**
 * Spring 서버의 최신 BTCUSDT 호가를 먼저 조회하고 SSE로 실시간 호가를 이어서 받는다.
 * 주문장과 주문 기준 가격은 이 Hook을 사용하며 Binance 캔들 연결과는 분리되어 있다.
 */
import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { AppState } from 'react-native';

import { consumeDepthStream } from '@/api/marketStream';
import { normalizeDepth } from '@/features/market/depthReducer';
import { useLatestDepth } from '@/features/market/useLatestDepth';
import type { Kline, KlineInterval } from '@/types/chart';
import type { DepthConnectionStatus, DepthView } from '@/types/market';

export type DepthPricePoint = {
  time: number;
  price: number;
};

const MAX_RECONNECT_DELAY_MS = 15_000;
const MAX_PRICE_POINTS = 240;
const KLINE_INTERVALS: [KlineInterval, number][] = [
  ['1m', 60_000],
  ['5m', 300_000],
  ['15m', 900_000],
  ['1h', 3_600_000],
  ['4h', 14_400_000],
  ['1d', 86_400_000],
];

export type LiveKlinesByInterval = Record<KlineInterval, Kline[]>;

function emptyLiveKlines(): LiveKlinesByInterval {
  return { '1m': [], '5m': [], '15m': [], '1h': [], '4h': [], '1d': [] };
}

function appendLiveKlines(
  previous: LiveKlinesByInterval,
  depth: DepthView,
): LiveKlinesByInterval {
  // frontend CandleChart와 같이 진행 중인 봉의 가격으로 최우선 매도호가를 사용한다.
  const price = depth.asks[0]?.price;
  if (price === undefined || !Number.isFinite(price) || price <= 0) {
    return previous;
  }

  const next = { ...previous };
  for (const [interval, duration] of KLINE_INTERVALS) {
    const openTime = Math.floor(depth.eventTime / duration) * duration;
    const candles = previous[interval];
    const current = candles.at(-1);

    if (current?.openTime === openTime) {
      next[interval] = [
        ...candles.slice(0, -1),
        {
          ...current,
          high: Math.max(current.high, price),
          low: Math.min(current.low, price),
          close: price,
        },
      ];
    } else if (!current || openTime > current.openTime) {
      next[interval] = [
        ...candles,
        {
          openTime,
          closeTime: openTime + duration - 1,
          open: price,
          high: price,
          low: price,
          close: price,
          // 호가에는 실제 체결 거래량이 없으므로 진행 봉 거래량은 만들지 않는다.
          volume: 0,
        },
      ].slice(-80);
    }
  }
  return next;
}

function appendPricePoint(
  points: DepthPricePoint[],
  depth: DepthView,
): DepthPricePoint[] {
  if (depth.midPrice === null) return points;

  const point = { time: depth.eventTime, price: depth.midPrice };
  const last = points.at(-1);
  const next =
    last?.time === point.time
      ? [...points.slice(0, -1), point]
      : [...points, point];
  return next.slice(-MAX_PRICE_POINTS);
}

export function useDepthStream() {
  const {
    depth: latestDepth,
    error: latestError,
    refresh: refreshLatest,
  } = useLatestDepth();
  const [streamDepth, setStreamDepth] = useState<DepthView | null>(null);
  const [priceHistory, setPriceHistory] = useState<DepthPricePoint[]>([]);
  const [liveKlines, setLiveKlines] =
    useState<LiveKlinesByInterval>(emptyLiveKlines);
  const [status, setStatus] = useState<DepthConnectionStatus>('loading');
  const [streamError, setStreamError] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);

  useFocusEffect(
    useCallback(() => {
      void retryKey;
      let disposed = false;
      let appIsActive = AppState.currentState === 'active';
      let controller: AbortController | null = null;
      let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
      let reconnectAttempt = 0;

      const clearReconnectTimer = () => {
        if (reconnectTimer) clearTimeout(reconnectTimer);
        reconnectTimer = null;
      };

      const stopConnection = () => {
        clearReconnectTimer();
        controller?.abort();
        controller = null;
      };

      const scheduleReconnect = (connect: () => void) => {
        if (disposed || !appIsActive) return;
        const delay = Math.min(
          1000 * 2 ** reconnectAttempt,
          MAX_RECONNECT_DELAY_MS,
        );
        reconnectAttempt += 1;
        setStatus('reconnecting');
        reconnectTimer = setTimeout(connect, delay);
      };

      const connect = () => {
        if (disposed || !appIsActive || controller) return;

        const currentController = new AbortController();
        controller = currentController;
        setStatus(reconnectAttempt === 0 ? 'connecting' : 'reconnecting');

        void consumeDepthStream({
          signal: currentController.signal,
          onOpen: () => {
            reconnectAttempt = 0;
            setStreamError(null);
            setStatus('connected');
          },
          onSnapshot: (snapshot) => {
            const normalized = normalizeDepth(snapshot);
            setStreamDepth(normalized);
            setPriceHistory((points) => appendPricePoint(points, normalized));
            setLiveKlines((candles) => appendLiveKlines(candles, normalized));
          },
        })
          .catch((caught) => {
            if (disposed || currentController.signal.aborted) return;
            setStreamError(
              caught instanceof Error
                ? caught.message
                : '실시간 호가 연결이 끊겼습니다.',
            );
          })
          .finally(() => {
            if (controller === currentController) controller = null;
            scheduleReconnect(connect);
          });
      };

      const subscription = AppState.addEventListener('change', (nextState) => {
        appIsActive = nextState === 'active';
        if (appIsActive) connect();
        else {
          stopConnection();
          setStatus('paused');
        }
      });

      connect();
      return () => {
        disposed = true;
        stopConnection();
        subscription.remove();
      };
    }, [retryKey]),
  );

  const depth = streamDepth ?? latestDepth;
  const fallbackHistory =
    priceHistory.length === 0 &&
    depth?.midPrice !== null &&
    depth?.midPrice !== undefined
      ? [{ time: depth.eventTime, price: depth.midPrice }]
      : priceHistory;

  const reconnect = useCallback(() => {
    setStreamError(null);
    setRetryKey((value) => value + 1);
    void refreshLatest();
  }, [refreshLatest]);

  return {
    depth,
    priceHistory: fallbackHistory,
    liveKlines,
    status,
    error: streamError ?? latestError,
    reconnect,
  };
}
