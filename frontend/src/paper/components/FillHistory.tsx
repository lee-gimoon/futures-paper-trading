import type { Fill } from '../../shared/types';

type Props = {
  fills: Fill[]; // 백엔드는 오름차순 → 여기서 최신순으로 뒤집어 표시
};

// 체결 내역. 가장 최근 체결이 위로 오도록 뒤집어 보여준다.
export function FillHistory({ fills }: Props) {
  if (fills.length === 0) return <p className="empty">나의 체결 내역 없음</p>;

  const newestFirst = [...fills].reverse();
  return (
    <div className="fill-list">
      <div className="fill-row fill-header">
        <span>방향</span>
        <span>가격</span>
        <span>개수</span>
      </div>
      {newestFirst.map((f) => (
        <div key={f.id} className="fill-row">
          <span className={f.side === 'BUY' ? 'pnl up' : 'pnl down'}>{f.side}</span>
          <span>{f.price.toFixed(2)}</span>
          <span>{f.quantity.toFixed(3)}</span>
        </div>
      ))}
    </div>
  );
}
