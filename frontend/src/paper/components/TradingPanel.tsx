import { AccountSummary } from './AccountSummary';
import { ClosePositionButton } from './ClosePositionButton';
import { OrderForm } from './OrderForm';
import type { Portfolio } from '../../shared/types';

type Props = {
  portfolio: Portfolio | null;
  midPrice: number | null; // App이 SSE snapshot에서 뽑아 넘기는 실시간 mid (미실현 PnL 실시간 갱신용)
  limitFill: { price: number; n: number } | null; // 호가창에서 클릭한 가격 (주문폼 지정가로)
  loading: boolean;
  error: string | null;
  onChanged: () => void;
};

// 로그인한 사용자의 거래 화면: 계좌 요약 + 주문 폼 + 주문 목록 + 체결 내역.
//   데이터는 useTrading 훅이 들고 폴링한다. 주문/취소 직후엔 refresh()로 즉시 갱신한다.
//   (이 패널은 App에서 로그인 상태일 때만 마운트한다.)
export function TradingPanel({ portfolio, midPrice, limitFill, loading, error, onChanged }: Props) {

  return (
    <div className="trading">
      <h2>모의 거래</h2>
      {error && <p className="auth-error" role="alert">{error}</p>}
      {loading && !portfolio && <p className="empty">계좌 불러오는 중...</p>}
      {portfolio && <AccountSummary portfolio={portfolio} midPrice={midPrice} />}
      {portfolio?.position && <ClosePositionButton position={portfolio.position} onClosed={onChanged} />}
      {portfolio && (
        <OrderForm portfolio={portfolio} midPrice={midPrice} limitFill={limitFill} onChanged={onChanged} />
      )}
    </div>
  );
}
