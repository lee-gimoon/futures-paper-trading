import type { Portfolio } from '../types';

type Props = {
  portfolio: Portfolio | null;
  midPrice: number | null; // 실시간 SSE mid — 미실현 PnL을 폴링 사이에도 갱신하려고 받는다
};

// 부호에 따라 색을 입혀 숫자를 보여준다(이익 초록·손실 빨강).
function Pnl({ value }: { value: number }) {
  const cls = value > 0 ? 'pnl up' : value < 0 ? 'pnl down' : 'pnl';
  const sign = value > 0 ? '+' : '';
  return <span className={cls}>{sign}{value.toFixed(2)}</span>;
}

// 계좌 요약: 평가자산 · 현금 · 실현/미실현 PnL · 레버리지/증거금/가용잔고 · 현재 포지션(청산가 포함).
//   미실현 PnL·equity는 백엔드 값 대신 실시간 mid로 다시 계산한다(SSE가 100ms마다 갱신 → 즉시 따라감).
//   청산가는 진입가·레버리지로만 정해져 mid와 무관하므로 백엔드 값을 그대로 쓴다.
export function AccountSummary({ portfolio, midPrice }: Props) {
  if (!portfolio) return <p className="empty">계좌 불러오는 중...</p>;

  const pos = portfolio.position;
  const liveUnrealized =
    pos && midPrice != null
      ? (midPrice - pos.averageEntryPrice) * (pos.side === 'LONG' ? pos.quantity : -pos.quantity)
      : portfolio.unrealizedPnl;
  const liveEquity = portfolio.cashBalance + portfolio.realizedPnl + liveUnrealized;

  return (
    <div className="account">
      <div className="account-row big">
        <span>평가자산</span>
        <span>{liveEquity.toFixed(2)} USDT</span>
      </div>
      <div className="account-row">
        <span>현금</span>
        <span>{portfolio.cashBalance.toFixed(2)}</span>
      </div>
      <div className="account-row">
        <span>실현 PnL</span>
        <Pnl value={portfolio.realizedPnl} />
      </div>
      <div className="account-row">
        <span>미실현 PnL</span>
        <Pnl value={liveUnrealized} />
      </div>
      <div className="account-row">
        <span>주문 레버리지</span>
        <span>{portfolio.leverage}x</span>
      </div>
      <div className="account-row">
        <span>가용 잔고</span>
        <span>{portfolio.availableBalance.toFixed(2)}</span>
      </div>

      {pos ? (
        <div className="position">
          <div className="account-row">
            <span>포지션</span>
            <span className={pos.side === 'LONG' ? 'pnl up' : 'pnl down'}>
              {pos.side} {pos.quantity.toFixed(3)} BTC
            </span>
          </div>
          <div className="account-row">
            <span>포지션 레버리지</span>
            <span>{pos.leverage}x</span>
          </div>
          <div className="account-row">
            <span>평균 진입가</span>
            <span>{pos.averageEntryPrice.toFixed(2)}</span>
          </div>
          <div className="account-row">
            <span>현재가(mid)</span>
            <span>{midPrice != null ? midPrice.toFixed(2) : pos.markPrice?.toFixed(2) ?? '-'}</span>
          </div>
          <div className="account-row">
            <span>사용 증거금</span>
            <span>{portfolio.usedMargin.toFixed(2)}</span>
          </div>
          <div className="account-row">
            <span>추정 청산가</span>
            <span className="pnl down">{pos.liquidationPrice.toFixed(2)}</span>
          </div>
        </div>
      ) : (
        <div className="account-row muted">
          <span>포지션</span>
          <span>없음</span>
        </div>
      )}
    </div>
  );
}
