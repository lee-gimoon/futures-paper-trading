import { useCallback, useEffect, useRef, useState } from 'react';
import type { Fill, Order, Portfolio } from '../../shared/types';
import { HttpError } from '../../shared/http';
import * as paperApi from '../api/paperApi';

// 거래 화면 데이터(계좌·주문·체결)를 한곳에서 들고 있는 훅.
// - 마운트 시 1회 + 직전 조회 완료 3초 후 다시 불러온다(백그라운드 matcher의 체결 결과를 폴링으로 따라잡는다).
// - refresh()는 주문/취소 직후 즉시 갱신용으로 노출한다.
// - 미실현 PnL의 '실시간' 갱신은 폴링이 아니라, 이미 받는 SSE mid로 화면에서 다시 계산한다(AccountSummary).
export function useTrading(onUnauthorized: () => void) {
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [fills, setFills] = useState<Fill[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const latestRequestRef = useRef(0);

  const refresh = useCallback(async () => {
    const requestId = ++latestRequestRef.current;

    try {
      const [p, o, f] = await Promise.all([
        paperApi.fetchPortfolio(),
        paperApi.listOrders(),
        paperApi.listFills(),
      ]);

      // 폴링과 수동 갱신이 겹치면 가장 최근에 시작한 요청 결과만 반영한다.
      if (requestId !== latestRequestRef.current) return;
      setPortfolio(p);
      setOrders(o);
      setFills(f);
      setError(null);
    } catch (err) {
      if (requestId !== latestRequestRef.current) return;
      if (err instanceof HttpError && err.status === 401) {
        onUnauthorized();
        return;
      }
      setError(err instanceof Error ? err.message : '거래 정보를 불러오지 못했습니다.');
    } finally {
      if (requestId === latestRequestRef.current) setLoading(false);
    }
  }, [onUnauthorized]);

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    async function poll() {
      await refresh();
      if (!cancelled) timer = setTimeout(poll, 3000);
    }

    void poll();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
      latestRequestRef.current += 1;
    };
  }, [refresh]);

  return { portfolio, orders, fills, loading, error, refresh };
}
