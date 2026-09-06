/** 모의 계좌, 주문과 체결에 관한 Spring API 요청을 제공한다. */

import { apiFetch, readJson } from '@/api/client';
import type { CreateOrderInput, Fill, Order, Portfolio } from '@/types/paper';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export async function createOrder(input: CreateOrderInput): Promise<Order> {
  const response = await apiFetch('/api/paper/orders', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ symbol: 'BTCUSDT', ...input }),
  });
  return readJson(response, '주문을 처리하지 못했습니다.');
}

export async function listOrders(): Promise<Order[]> {
  const response = await apiFetch('/api/paper/orders');
  return readJson(response, '주문 목록을 불러오지 못했습니다.');
}

export async function cancelOrder(id: number): Promise<Order> {
  const response = await apiFetch(`/api/paper/orders/${id}`, {
    method: 'DELETE',
  });
  return readJson(response, '주문을 취소하지 못했습니다.');
}

export async function fetchPortfolio(): Promise<Portfolio> {
  const response = await apiFetch('/api/paper/account');
  return readJson(response, '계좌 정보를 불러오지 못했습니다.');
}

export async function listFills(): Promise<Fill[]> {
  const response = await apiFetch('/api/paper/fills');
  return readJson(response, '체결 내역을 불러오지 못했습니다.');
}

export async function setLeverage(leverage: number): Promise<Portfolio> {
  const response = await apiFetch('/api/paper/account/leverage', {
    method: 'PUT',
    headers: JSON_HEADERS,
    body: JSON.stringify({ leverage }),
  });
  return readJson(response, '레버리지를 변경하지 못했습니다.');
}
