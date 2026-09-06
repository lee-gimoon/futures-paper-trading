/** 화면의 예상 증거금 계산이며 최종 주문 가능 여부와 체결 가격은 서버가 판단한다. */
import type { OrderSide, Portfolio } from '@/types/paper';

export const LEVERAGES = [1, 3, 5, 10, 20, 50] as const;

export function estimateOrder(
  quantity: number,
  price: number,
  leverage: number,
  side: OrderSide,
  portfolio: Portfolio | null,
) {
  const valid = [quantity, price, leverage].every(
    (value) => Number.isFinite(value) && value > 0,
  );
  if (!valid) return { notional: 0, margin: 0, closingQuantity: 0 };
  const position = portfolio?.position;
  const opposing =
    position &&
    ((position.side === 'LONG' && side === 'SELL') ||
      (position.side === 'SHORT' && side === 'BUY'));
  const closingQuantity = opposing ? Math.min(quantity, position.quantity) : 0;
  return {
    notional: quantity * price,
    margin: ((quantity - closingQuantity) * price) / leverage,
    closingQuantity,
  };
}

export function quantityForPercent(
  percent: number,
  price: number,
  side: OrderSide,
  portfolio: Portfolio | null,
): string {
  if (
    !portfolio ||
    !Number.isFinite(price) ||
    price <= 0 ||
    percent <= 0 ||
    percent > 100
  )
    return '';
  const position = portfolio.position;
  const opposing =
    position &&
    ((position.side === 'LONG' && side === 'SELL') ||
      (position.side === 'SHORT' && side === 'BUY'));
  // 시세 변동 여유분 5%를 남기고, 반대 포지션은 먼저 줄일 수 있는 수량으로 더한다.
  const maximum =
    (Math.max(0, portfolio.availableBalance) * portfolio.leverage * 0.95) /
      price +
    (opposing ? position.quantity : 0);
  return String(Math.floor(((maximum * percent) / 100) * 1e6) / 1e6);
}
