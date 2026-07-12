import { useEffect, useState, type FormEvent } from 'react';
import type { OrderSide, OrderType, Portfolio } from '../../shared/types';
import * as paperApi from '../api/paperApi';

type Props = {
  portfolio: Portfolio; // 가용잔고·레버리지 (% 바 사이징 기준)
  midPrice: number | null; // USDT↔BTC 환산 기준가
  limitFill: { price: number; n: number } | null; // 호가창 클릭 가격 (지정가로)
  onChanged: () => void; // 주문/레버리지 변경 후 새로고침
};

const LEVERAGES = [1, 3, 5, 10, 20, 50];
const PERCENTS = [25, 50, 75, 100];
// 100% 시장가는 가용 증거금의 99.9%를 주문 예산으로 확정하고, 모든 수량/금액 계산은 그 예산을 기준으로 한다.
const MAX_MARKET_MARGIN_USAGE = 0.999;

function floorToPrecision(value: number, digits: number) {
  const factor = 10 ** digits;
  return Math.floor(value * factor) / factor;
}

function formatBtcAmount(value: number) {
  return floorToPrecision(value, 6).toFixed(6).replace(/\.?0+$/, '');
}

function formatUsdtAmount(value: number) {
  return floorToPrecision(value, 2).toFixed(2);
}

function orderErrorMessage(err: unknown) {
  if (!(err instanceof Error)) return '주문에 실패했습니다.';
  if (err.message.startsWith('가용 증거금 부족:')) {
    return '가용 증거금이 부족합니다. 주문 수량을 조금 줄여주세요.';
  }
  return err.message;
}

// 모의 주문 입력 폼 (레버리지/마진).
//   BUY/SELL · 시장가/지정가 · 수량(BTC↔USDT 토글) · 지정가 한도 · 레버리지 선택 · 잔고 % 바.
//   백엔드는 BTC 수량을 받으므로 USDT 입력은 기준가(지정가면 limit, 아니면 mid)로 BTC로 환산해 보낸다.
//   기준가가 명목·증거금 계산에도 쓰인다. 검증/체결/청산은 백엔드가 한다.
export function OrderForm({ portfolio, midPrice, limitFill, onChanged }: Props) {
  const [side, setSide] = useState<OrderSide>('BUY');
  const [type, setType] = useState<OrderType>('MARKET');
  const [unit, setUnit] = useState<'BTC' | 'USDT'>('BTC');
  const [amount, setAmount] = useState('0.01'); // unit 기준 입력값
  const [limitPrice, setLimitPrice] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [changingLeverage, setChangingLeverage] = useState<number | null>(null);

  // 호가창에서 가격을 클릭하면 지정가 모드로 바꾸고 그 가격을 한도가로 채운다.
  //   limitFill.n(클릭 카운터)이 매번 달라져 같은 가격을 다시 눌러도 effect가 재실행된다.
  useEffect(() => {
    if (limitFill) {
      setType('LIMIT');
      setLimitPrice(String(limitFill.price));
    }
  }, [limitFill]);

  const leverage = portfolio.leverage;
  const available = portfolio.availableBalance;
  const position = portfolio.position;
  const isReducingPosition =
    !!position && ((position.side === 'LONG' && side === 'SELL') || (position.side === 'SHORT' && side === 'BUY'));
  // 환산/명목 계산 기준가: 지정가면 limit, 아니면 현재 mid.
  const parsedLimitPrice = Number(limitPrice);
  const hasValidLimitPrice = Number.isFinite(parsedLimitPrice) && parsedLimitPrice > 0;
  const refPrice = type === 'LIMIT' ? (hasValidLimitPrice ? parsedLimitPrice : 0) : midPrice ?? 0;

  // 입력값(unit)을 BTC 수량과 USDT 명목으로 환산.
  const parsedAmount = Number(amount);
  const amountNum = Number.isFinite(parsedAmount) ? parsedAmount : 0;
  const rawBtcQty = unit === 'BTC' ? amountNum : refPrice > 0 ? amountNum / refPrice : 0;
  // 화면 미리보기와 API 요청이 정확히 같은 BTC 수량을 사용하도록 먼저 6자리 수량을 확정한다.
  const orderQuantity = floorToPrecision(rawBtcQty, 6);
  const notional = orderQuantity * refPrice;
  const openingQty = isReducingPosition && position ? Math.max(0, orderQuantity - position.quantity) : orderQuantity;
  const requiredMargin = leverage > 0 ? (openingQty * refPrice) / leverage : 0;

  // % 버튼: 반대 방향이면 포지션 수량 기준, 아니면 가용잔고 × 레버리지 기준으로 채운다.
  function applyPercent(pct: number) {
    if (refPrice <= 0) return;

    if (isReducingPosition && position) {
      const closeQty = (position.quantity * pct) / 100;
      setAmount(unit === 'USDT' ? formatUsdtAmount(closeQty * refPrice) : formatBtcAmount(closeQty));
      return;
    }

    const marginUsage = type === 'MARKET' && pct === 100 ? MAX_MARKET_MARGIN_USAGE : 1;
    const pickNotional = (available * marginUsage * leverage * pct) / 100;
    setAmount(unit === 'USDT' ? formatUsdtAmount(pickNotional) : formatBtcAmount(pickNotional / refPrice));
  }

  async function changeLeverage(lev: number) {
    if (changingLeverage !== null || submitting) return;
    setError('');
    setChangingLeverage(lev);
    try {
      await paperApi.setLeverage(lev);
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : '레버리지 변경 실패');
    } finally {
      setChangingLeverage(null);
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    if (type === 'LIMIT' && !hasValidLimitPrice) {
      setError('지정가는 0보다 큰 숫자여야 합니다.');
      return;
    }
    if (rawBtcQty <= 0) {
      setError(unit === 'USDT' && refPrice <= 0 ? '환산 기준가가 없습니다(호가 대기 중).' : '수량을 확인하세요.');
      return;
    }
    if (orderQuantity <= 0) {
      setError('주문 가능한 최소 수량은 0.000001 BTC입니다.');
      return;
    }
    setSubmitting(true);
    try {
      await paperApi.createOrder({
        side,
        type,
        quantity: orderQuantity, // 백엔드는 BTC 수량, 6자리에서 내림해 가용 증거금을 초과하지 않게 한다.
        limitPrice: type === 'LIMIT' ? parsedLimitPrice : undefined,
      });
      onChanged();
    } catch (err) {
      setError(orderErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="order-form" onSubmit={handleSubmit}>
      <div className="seg">
        <button type="button" className={side === 'BUY' ? 'buy active' : 'buy'} onClick={() => setSide('BUY')}>
          BUY
        </button>
        <button type="button" className={side === 'SELL' ? 'sell active' : 'sell'} onClick={() => setSide('SELL')}>
          SELL
        </button>
      </div>

      {/* 레버리지 선택 (계좌 단위 — 변경 시 즉시 저장) */}
      <div className="lev-row">
        {LEVERAGES.map((lev) => (
          <button
            key={lev}
            type="button"
            className={leverage === lev ? 'lev active' : 'lev'}
            disabled={changingLeverage !== null || submitting}
            onClick={() => changeLeverage(lev)}
          >
            {lev}x
          </button>
        ))}
      </div>

      <div className="seg">
        <button type="button" className={type === 'MARKET' ? 'active' : ''} onClick={() => setType('MARKET')}>
          시장가
        </button>
        <button type="button" className={type === 'LIMIT' ? 'active' : ''} onClick={() => setType('LIMIT')}>
          지정가
        </button>
      </div>

      {type === 'LIMIT' && (
        <label>
          지정가 (USDT)
          <input
            type="number"
            step="0.1"
            min="0.1"
            value={limitPrice}
            onChange={(e) => setLimitPrice(e.target.value)}
            required
          />
        </label>
      )}

      <label>
        <span className="amt-head">
          수량
          <span className="unit-toggle">
            <button type="button" className={unit === 'BTC' ? 'active' : ''} onClick={() => setUnit('BTC')}>
              BTC
            </button>
            <button type="button" className={unit === 'USDT' ? 'active' : ''} onClick={() => setUnit('USDT')}>
              USDT
            </button>
          </span>
        </span>
        <input
          type="number"
          step={unit === 'BTC' ? '0.000001' : '0.01'}
          min="0"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          required
        />
      </label>

      {/* 잔고 비율 % 바 */}
      <div className="pct-row">
        {PERCENTS.map((p) => (
          <button key={p} type="button" className="pct" onClick={() => applyPercent(p)}>
            {p}%
          </button>
        ))}
      </div>

      {/* 환산/증거금 미리보기 */}
      <div className="order-preview">
        <span>주문 {orderQuantity.toFixed(6)} BTC</span>
        <span>명목 {notional.toFixed(2)}</span>
        <span>증거금 {requiredMargin.toFixed(2)}</span>
      </div>

      {error && <p className="auth-error">{error}</p>}

      <button
        type="submit"
        className={side === 'BUY' ? 'submit buy' : 'submit sell'}
        disabled={submitting || changingLeverage !== null}
      >
        {submitting ? '...' : `${side} 주문`}
      </button>
    </form>
  );
}
