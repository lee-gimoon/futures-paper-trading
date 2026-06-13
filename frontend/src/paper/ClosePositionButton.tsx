import { useState } from 'react';
import type { Position } from '../types';
import * as paperApi from './paperApi';

type Props = {
  position: Position;
  onClosed: () => void; // 종료 후 새로고침
};

// 포지션 원클릭 종료. 반대 방향 시장가로 포지션 수량만큼 주문 → 포지션이 닫힌다(LONG→SELL, SHORT→BUY).
//   축소(청산) 주문이라 백엔드 증거금 검증은 0으로 통과한다.
//   ※ 호가창에 보이는 유동성(상위 20레벨)만큼만 체결되므로, 큰 포지션은 한 번에 다 안 닫힐 수 있다(다시 누르면 됨).
export function ClosePositionButton({ position, onClosed }: Props) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function close() {
    setError('');
    setBusy(true);
    try {
      await paperApi.createOrder({
        side: position.side === 'LONG' ? 'SELL' : 'BUY',
        type: 'MARKET',
        quantity: position.quantity,
      });
      onClosed();
    } catch (err) {
      setError(err instanceof Error ? err.message : '포지션 종료에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="close-position">
      <button type="button" className="close-btn" disabled={busy} onClick={close}>
        {busy ? '...' : '포지션 종료 (시장가)'}
      </button>
      {error && <p className="auth-error">{error}</p>}
    </div>
  );
}
