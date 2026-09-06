import test from 'node:test';
import assert from 'node:assert/strict';
import {
  estimateOrder,
  quantityForPercent,
  LEVERAGES,
} from '../src/features/trade/orderEstimate.ts';
import { normalizeDepth } from '../src/features/market/depthReducer.ts';
import { authReturn } from '../src/features/auth/authReturn.ts';
import { formatMarketPrice } from '../src/utils/format.ts';

const account = {
  cashBalance: 10000,
  realizedPnl: 0,
  unrealizedPnl: 0,
  equity: 10000,
  leverage: 10,
  usedMargin: 0,
  availableBalance: 10000,
  position: null,
};
const long = { ...account, position: { side: 'LONG', quantity: 0.1 } };
const short = { ...account, position: { side: 'SHORT', quantity: 0.1 } };

test('신규 롱과 숏의 명목 금액과 증거금을 계산한다', () => {
  for (const side of ['BUY', 'SELL'])
    assert.deepEqual(estimateOrder(0.1, 80000, 10, side, account), {
      notional: 8000,
      margin: 800,
      closingQuantity: 0,
    });
});
test('반대 포지션 일부 종료에는 신규 증거금을 계산하지 않는다', () => {
  assert.equal(estimateOrder(0.05, 80000, 10, 'SELL', long).margin, 0);
  assert.equal(estimateOrder(0.05, 80000, 10, 'BUY', short).margin, 0);
});
test('포지션을 넘어서는 반대 주문에는 새로 여는 수량의 증거금만 계산한다', () => {
  const estimate = estimateOrder(0.2, 80000, 20, 'SELL', long);
  assert.equal(estimate.closingQuantity, 0.1);
  assert.equal(estimate.margin, 400);
});
test('같은 방향 추가 주문은 전체 수량에 대해 증거금이 필요하다', () => {
  assert.equal(estimateOrder(0.2, 80000, 10, 'BUY', long).margin, 1600);
});
test('입력 중이거나 잘못된 값은 NaN과 음수 추정치를 표시하지 않는다', () => {
  for (const quantity of [NaN, Infinity, -1, 0])
    assert.equal(estimateOrder(quantity, 80000, 10, 'BUY', account).margin, 0);
  assert.equal(estimateOrder(1, 0, 10, 'BUY', account).margin, 0);
});
test('잔고 비율은 5% 여유를 남기고 수량을 내림한다', () => {
  assert.equal(quantityForPercent(100, 80000, 'BUY', account), '1.1875');
  assert.equal(quantityForPercent(25, 80000, 'BUY', account), '0.296875');
  assert.equal(quantityForPercent(100, 80000, 'SELL', long), '1.2875');
});
test('미로그인 또는 가격 없는 상태에서는 비율 수량을 만들지 않는다', () => {
  assert.equal(quantityForPercent(100, 0, 'BUY', account), '');
  assert.equal(quantityForPercent(100, 80000, 'BUY', null), '');
  assert.equal(
    quantityForPercent(100, 80000, 'BUY', { ...account, availableBalance: 0 }),
    '0',
  );
});
test('지원 레버리지는 서버에서 허용한 배수만 포함한다', () => {
  assert.deepEqual(LEVERAGES, [1, 3, 5, 10, 20, 50]);
});
test('호가 정렬과 중간 가격은 유효하고 수량이 남은 항목만 사용한다', () => {
  const result = normalizeDepth({
    symbol: 'BTCUSDT',
    eventTime: 1,
    bids: [
      { price: 99, quantity: 2 },
      { price: 100, quantity: 3 },
      { price: 1000, quantity: 0 },
    ],
    asks: [
      { price: 102, quantity: 1 },
      { price: 101, quantity: 2 },
      { price: 1, quantity: -1 },
    ],
  });
  assert.equal(result.bids[0].price, 100);
  assert.equal(result.asks[0].price, 101);
  assert.equal(result.midPrice, 100.5);
});
test('호가 한쪽이 없을 때 중간 가격을 만들지 않는다', () => {
  assert.equal(
    normalizeDepth({
      symbol: 'BTCUSDT',
      eventTime: 1,
      bids: [],
      asks: [{ price: 100, quantity: 1 }],
    }).midPrice,
    null,
  );
});
test('로그인 반환 경로는 허용된 앱 탭으로 제한한다', () => {
  assert.equal(authReturn('/trade'), '/trade');
  assert.equal(authReturn('/orders'), '/orders');
  assert.equal(authReturn('https://example.com'), '/market');
  assert.equal(authReturn(['/trade']), '/market');
});
test('소액 코인 가격을 0.00으로 반올림해 버리지 않는다', () => {
  assert.equal(formatMarketPrice(0.08912), '0.08912');
  assert.equal(formatMarketPrice(1.4079), '1.4079');
  assert.equal(formatMarketPrice(null), '—');
});
