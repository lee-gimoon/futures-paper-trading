// 백엔드 모의 거래 API 호출 모음. authApi.ts와 같은 방식 — 쿠키 세션이라 credentials:'include'만 붙인다.
import type { Fill, Order, Portfolio } from '../../shared/types';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 실패 응답이면 백엔드가 준 {"message": ...}를 꺼내 Error로 만든다(없으면 fallback).
async function toError(res: Response, fallback: string): Promise<Error> {
  try {
    const body = await res.json();
    if (body && typeof body.message === 'string') return new Error(body.message);
  } catch {
    // 본문이 JSON이 아닐 수 있음(검증 400 등) → fallback
  }
  return new Error(fallback);
}

// 주문 생성. 심볼은 8·9단계 단일 심볼이라 BTCUSDT로 고정. 시장가면 limitPrice는 보내지 않는다.
export async function createOrder(input: {
  side: 'BUY' | 'SELL';
  type: 'MARKET' | 'LIMIT';
  quantity: number;
  limitPrice?: number;
}): Promise<Order> {
  const res = await fetch('/api/paper/orders', {
    method: 'POST',
    headers: JSON_HEADERS,
    credentials: 'include',
    body: JSON.stringify({ symbol: 'BTCUSDT', ...input }),
  });
  if (!res.ok) throw await toError(res, '주문에 실패했습니다.');
  return res.json();
}

// 내 주문 목록(최신순).
export async function listOrders(): Promise<Order[]> {
  const res = await fetch('/api/paper/orders', { credentials: 'include' });
  if (!res.ok) throw await toError(res, '주문 목록을 불러오지 못했습니다.');
  return res.json();
}

// OPEN 주문 취소. 성공 시 CANCELED된 주문을 돌려준다.
export async function cancelOrder(id: number): Promise<Order> {
  const res = await fetch(`/api/paper/orders/${id}`, { method: 'DELETE', credentials: 'include' });
  if (!res.ok) throw await toError(res, '주문 취소에 실패했습니다.');
  return res.json();
}

// 내 계좌(현금·실현/미실현 PnL·포지션).
export async function fetchPortfolio(): Promise<Portfolio> {
  const res = await fetch('/api/paper/account', { credentials: 'include' });
  if (!res.ok) throw await toError(res, '계좌 정보를 불러오지 못했습니다.');
  return res.json();
}

// 내 체결 내역(백엔드는 오름차순으로 준다 → 화면에서 최신순으로 뒤집는다).
export async function listFills(): Promise<Fill[]> {
  const res = await fetch('/api/paper/fills', { credentials: 'include' });
  if (!res.ok) throw await toError(res, '체결 내역을 불러오지 못했습니다.');
  return res.json();
}

// 레버리지 변경(UI 프리셋: 1, 3, 5, 10, 20, 50). 성공 시 갱신된 계좌를 돌려준다.
export async function setLeverage(leverage: number): Promise<Portfolio> {
  const res = await fetch('/api/paper/account/leverage', {
    method: 'PUT',
    headers: JSON_HEADERS,
    credentials: 'include',
    body: JSON.stringify({ leverage }),
  });
  if (!res.ok) throw await toError(res, '레버리지 변경에 실패했습니다.');
  return res.json();
}
