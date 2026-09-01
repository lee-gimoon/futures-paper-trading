// 백엔드 모의 거래 API 호출 모음. apiFetch가 SESSION 쿠키를 포함하고, 상태 변경 요청에는 CSRF 헤더도 자동으로 붙인다.
import type { Fill, Order, OrderSide, OrderType, Portfolio } from '../../shared/types';
import { apiFetch, toHttpError } from '../../shared/http';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 주문 생성. 심볼은 8·9단계 단일 심볼이라 BTCUSDT로 고정. 시장가면 limitPrice는 보내지 않는다.
export async function createOrder(input: {
  side: OrderSide;
  type: OrderType;
  quantity: number;
  limitPrice?: number;
}): Promise<Order> {
  const res = await apiFetch('/api/paper/orders', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ symbol: 'BTCUSDT', ...input }),
  });
  if (!res.ok) throw await toHttpError(res, '주문에 실패했습니다.');
  return res.json();
}

// 내 주문 목록(최신순).
export async function listOrders(): Promise<Order[]> {
  const res = await apiFetch('/api/paper/orders');
  if (!res.ok) throw await toHttpError(res, '주문 목록을 불러오지 못했습니다.');
  return res.json();
}

// OPEN 주문 취소. 성공 시 CANCELED된 주문을 돌려준다.
export async function cancelOrder(id: number): Promise<Order> {
  const res = await apiFetch(`/api/paper/orders/${id}`, { method: 'DELETE' });
  if (!res.ok) throw await toHttpError(res, '주문 취소에 실패했습니다.');
  return res.json();
}

// 내 계좌(현금·실현/미실현 PnL·포지션).
export async function fetchPortfolio(): Promise<Portfolio> {
  const res = await apiFetch('/api/paper/account');
  if (!res.ok) throw await toHttpError(res, '계좌 정보를 불러오지 못했습니다.');
  return res.json();
}

// 내 체결 내역(백엔드는 오름차순으로 준다 → 화면에서 최신순으로 뒤집는다).
export async function listFills(): Promise<Fill[]> {
  const res = await apiFetch('/api/paper/fills');
  if (!res.ok) throw await toHttpError(res, '체결 내역을 불러오지 못했습니다.');
  return res.json();
}

// 레버리지 변경(UI 프리셋: 1, 3, 5, 10, 20, 50). 성공 시 갱신된 계좌를 돌려준다.
export async function setLeverage(leverage: number): Promise<Portfolio> {
  const res = await apiFetch('/api/paper/account/leverage', {
    method: 'PUT',
    headers: JSON_HEADERS,
    body: JSON.stringify({ leverage }),
  });
  if (!res.ok) throw await toHttpError(res, '레버리지 변경에 실패했습니다.');
  return res.json();
}
