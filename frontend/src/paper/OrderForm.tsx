import { useEffect, useState, type FormEvent } from 'react';
import type { Portfolio } from '../types';
import * as paperApi from './paperApi';

type Props = {
  portfolio: Portfolio | null; // 가용잔고·레버리지 (% 바 사이징 기준)
  midPrice: number | null; // USDT↔BTC 환산 기준가
  limitFill: { price: number; n: number } | null; // 호가창 클릭 가격 (지정가로)
  onChanged: () => void; // 주문/레버리지 변경 후 새로고침
};

const LEVERAGES = [1, 3, 5, 10, 20, 50];
const PERCENTS = [25, 50, 75, 100];

function formatBtcAmount(value: number) {
  return value.toFixed(6).replace(/\.?0+$/, '');
}

// 모의 주문 입력 폼 (레버리지/마진).
//   Long/Short · 시장가/지정가 · 수량(BTC↔USDT 토글) · 지정가 한도 · 레버리지 선택 · 잔고 % 바.
//   백엔드는 BTC 수량을 받으므로 USDT 입력은 기준가(지정가면 limit, 아니면 mid)로 BTC로 환산해 보낸다.
//   기준가가 명목·증거금 계산에도 쓰인다. 검증/체결/청산은 백엔드가 한다.
export function OrderForm({ portfolio, midPrice, limitFill, onChanged }: Props) {
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY');
  const [type, setType] = useState<'MARKET' | 'LIMIT'>('MARKET');
  const [unit, setUnit] = useState<'BTC' | 'USDT'>('BTC');
  const [amount, setAmount] = useState('0.01'); // unit 기준 입력값
  const [limitPrice, setLimitPrice] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // 호가창에서 가격을 클릭하면 지정가 모드로 바꾸고 그 가격을 한도가로 채운다.
  //   limitFill.n(클릭 카운터)이 매번 달라져 같은 가격을 다시 눌러도 effect가 재실행된다.
  useEffect(() => {
    if (limitFill) {
      setType('LIMIT');
      setLimitPrice(String(limitFill.price));
    }
  }, [limitFill]);

  const leverage = portfolio?.leverage ?? 10;
  const available = portfolio?.availableBalance ?? 0;
  const position = portfolio?.position;
  const isReducingPosition =
    !!position && ((position.side === 'LONG' && side === 'SELL') || (position.side === 'SHORT' && side === 'BUY'));
  // 환산/명목 계산 기준가: 지정가면 limit, 아니면 현재 mid.
  const refPrice = type === 'LIMIT' && Number(limitPrice) > 0 ? Number(limitPrice) : midPrice ?? 0;

  // 입력값(unit)을 BTC 수량과 USDT 명목으로 환산.
  const amountNum = Number(amount) || 0;
  const btcQty = unit === 'BTC' ? amountNum : refPrice > 0 ? amountNum / refPrice : 0;
  const notional = unit === 'USDT' ? amountNum : amountNum * refPrice;
  const openingQty = isReducingPosition && position ? Math.max(0, btcQty - position.quantity) : btcQty;
  const requiredMargin = leverage > 0 ? (openingQty * refPrice) / leverage : 0;

  // % 버튼: 반대 방향이면 포지션 수량 기준, 아니면 가용잔고 × 레버리지 기준으로 채운다.
  function applyPercent(pct: number) {
    if (refPrice <= 0) return;

    if (isReducingPosition && position) {
      const closeQty = (position.quantity * pct) / 100;
      setAmount(unit === 'USDT' ? (closeQty * refPrice).toFixed(2) : formatBtcAmount(closeQty));
      return;
    }

    const pickNotional = (available * leverage * pct) / 100;
    setAmount(unit === 'USDT' ? pickNotional.toFixed(2) : formatBtcAmount(pickNotional / refPrice));
  }

  async function changeLeverage(lev: number) {
    try {
      await paperApi.setLeverage(lev);
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : '레버리지 변경 실패');
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    if (btcQty <= 0) {
      setError(unit === 'USDT' && refPrice <= 0 ? '환산 기준가가 없습니다(호가 대기 중).' : '수량을 확인하세요.');
      return;
    }
    setSubmitting(true);
    try {
      await paperApi.createOrder({
        side,
        type,
        quantity: Math.round(btcQty * 1e6) / 1e6, // 백엔드는 BTC 수량
        limitPrice: type === 'LIMIT' ? Number(limitPrice) : undefined,
      });
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : '주문에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="order-form" onSubmit={handleSubmit}>
      <div className="seg">
        <button type="button" className={side === 'BUY' ? 'buy active' : 'buy'} onClick={() => setSide('BUY')}>
          Long
        </button>
        <button type="button" className={side === 'SELL' ? 'sell active' : 'sell'} onClick={() => setSide('SELL')}>
          Short
        </button>
      </div>

      {/* 레버리지 선택 (계좌 단위 — 변경 시 즉시 저장) */}
      <div className="lev-row">
        {LEVERAGES.map((lev) => (
          <button
            key={lev}
            type="button"
            className={leverage === lev ? 'lev active' : 'lev'}
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
            min="0"
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
        <span>≈ {btcQty.toFixed(4)} BTC</span>
        <span>명목 {notional.toFixed(2)}</span>
        <span>증거금 {requiredMargin.toFixed(2)}</span>
      </div>

      {error && <p className="auth-error">{error}</p>}

      <button type="submit" className={side === 'BUY' ? 'submit buy' : 'submit sell'} disabled={submitting}>
        {submitting ? '...' : side === 'BUY' ? 'Long 주문' : 'Short 주문'}
      </button>
    </form>
  );
}
