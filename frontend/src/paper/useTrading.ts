import { useCallback, useEffect, useState } from 'react';
import type { Fill, Order, Portfolio } from '../types';
import * as paperApi from './paperApi';

// 거래 화면 데이터(계좌·주문·체결)를 한곳에서 들고 있는 훅.
// - 마운트 시 1회 + 3초마다 새로 불러온다(백그라운드 matcher가 OPEN 지정가를 자동 체결할 수 있어 폴링으로 따라잡는다).
// - refresh()는 주문/취소 직후 즉시 갱신용으로 노출한다.
// - 미실현 PnL의 '실시간' 갱신은 폴링이 아니라, 이미 받는 SSE mid로 화면에서 다시 계산한다(AccountSummary).
export function useTrading() {
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [fills, setFills] = useState<Fill[]>([]);

  const refresh = useCallback(async () => {
    const [p, o, f] = await Promise.all([
      paperApi.fetchPortfolio(),
      paperApi.listOrders(),
      paperApi.listFills(),
    ]);
    setPortfolio(p);
    setOrders(o);
    setFills(f);
  }, []);

  useEffect(() => {
    refresh().catch(() => {}); // 실패는 조용히 무시(다음 폴링에서 재시도)
    const timer = setInterval(() => {
      refresh().catch(() => {});
    }, 3000);
    return () => clearInterval(timer);
  }, [refresh]);

  return { portfolio, orders, fills, refresh };
}
