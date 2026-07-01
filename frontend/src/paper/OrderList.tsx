import { useState } from 'react';
import type { Order, Position } from '../types';
import * as paperApi from './paperApi';

type Props = {
  orders: Order[];
  position: Position | null;
  emptyLabel?: string;
  onChanged: () => void; // 취소 성공 후 새로고침
};

function formatOrderPrice(order: Order) {
  if (order.limitPrice != null) return order.limitPrice.toFixed(2);
  if (order.avgPrice != null) return order.avgPrice.toFixed(2);
  return '-';
}

function potentialPnl(order: Order, position: Position | null) {
  if (!position || order.limitPrice == null) return null;

  const closesLong = position.side === 'LONG' && order.side === 'SELL';
  const closesShort = position.side === 'SHORT' && order.side === 'BUY';
  if (!closesLong && !closesShort) return null;

  const remainingOrderQty = Math.max(order.quantity - order.filledQuantity, 0);
  const closeQty = Math.min(remainingOrderQty, position.quantity);
  if (closeQty <= 0) return null;

  const priceDiff = closesLong
    ? order.limitPrice - position.averageEntryPrice
    : position.averageEntryPrice - order.limitPrice;
  return priceDiff * closeQty;
}

function formatPotentialPnl(value: number | null) {
  if (value == null) return '-';
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(2)} USDT`;
}

// 내 주문 목록(최신순). OPEN 주문에만 취소 버튼을 보여준다(나머지는 종료 상태라 취소 불가).
export function OrderList({ orders, position, onChanged, emptyLabel = '주문 없음' }: Props) {
  const [busyId, setBusyId] = useState<number | null>(null);

  async function cancel(id: number) {
    setBusyId(id);
    try {
      await paperApi.cancelOrder(id);
      onChanged();
    } catch {
      // 이미 체결/취소됐을 수 있음 → 다음 폴링이 상태를 맞춰준다
    } finally {
      setBusyId(null);
    }
  }

  if (orders.length === 0) return <p className="empty">{emptyLabel}</p>;

  return (
    <div className="order-list">
      <div className="order-row order-header">
        <span>포지션</span>
        <span>유형</span>
        <span>가격</span>
        <span>수량</span>
        <span>예상손익</span>
        <span>상태</span>
        <span />
      </div>
      {orders.map((o) => {
        const pnl = potentialPnl(o, position);
        return (
          <div key={o.id} className="order-row">
            <span className={o.side === 'BUY' ? 'pnl up' : 'pnl down'}>{o.side === 'BUY' ? 'Long' : 'Short'}</span>
            <span>{o.type}</span>
            <span>{formatOrderPrice(o)}</span>
            <span>{o.quantity.toFixed(3)}</span>
            <span className={pnl == null ? 'muted' : pnl >= 0 ? 'pnl up' : 'pnl down'}>{formatPotentialPnl(pnl)}</span>
            <span className="status">{o.status}</span>
            {o.status === 'OPEN' ? (
              <button type="button" className="ghost tiny" disabled={busyId === o.id} onClick={() => cancel(o.id)}>
                취소
              </button>
            ) : (
              <span className="muted">{o.avgPrice != null ? o.avgPrice.toFixed(2) : '-'}</span>
            )}
          </div>
        );
      })}
    </div>
  );
}
