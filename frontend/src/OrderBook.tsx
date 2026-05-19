import type { OrderBookLevel, OrderBookSnapshot } from './types';

// 표 한 행에 그릴 값들. quantity와 cumulative는 화면에서 계산해 만든다.
type Row = {
  price: number;
  quantity: number;
  cumulative: number;
};

// asks: 가격 오름차순으로 정렬 → best ask부터 누적 → 그릴 때는 역순(높은 가격이 맨 위)
function buildAskRows(asks: OrderBookLevel[]): Row[] {
  const sorted = [...asks].sort((a, b) => a.price - b.price);
  let cum = 0;
  const rows = sorted.map((lvl) => {
    cum += lvl.quantity;
    return { price: lvl.price, quantity: lvl.quantity, cumulative: cum };
  });
  return rows.reverse();
}

// bids: 가격 내림차순으로 정렬 → best bid부터 누적 → 그대로 그리면 됨
function buildBidRows(bids: OrderBookLevel[]): Row[] {
  const sorted = [...bids].sort((a, b) => b.price - a.price);
  let cum = 0;
  return sorted.map((lvl) => {
    cum += lvl.quantity;
    return { price: lvl.price, quantity: lvl.quantity, cumulative: cum };
  });
}

type Props = {
  snapshot: OrderBookSnapshot;
};

export function OrderBook({ snapshot }: Props) {
  const askRows = buildAskRows(snapshot.asks);
  const bidRows = buildBidRows(snapshot.bids);

  // best ask = asks 중 가장 낮은 가격 = 역순으로 정렬된 askRows의 마지막
  // best bid = bids 중 가장 높은 가격 = bidRows의 첫번째
  const bestAsk = askRows.length > 0 ? askRows[askRows.length - 1].price : 0;
  const bestBid = bidRows.length > 0 ? bidRows[0].price : 0;
  const spread = bestAsk - bestBid;

  return (
    <div className="orderbook">
      <div className="header">
        <span>price</span>
        <span>quantity</span>
        <span>cumulative</span>
      </div>

      <div className="asks">
        {askRows.map((row) => (
          <div key={`ask-${row.price}`} className="row ask">
            <span>{row.price.toFixed(2)}</span>
            <span>{row.quantity.toFixed(3)}</span>
            <span>{row.cumulative.toFixed(3)}</span>
          </div>
        ))}
      </div>

      <div className="spread">
        spread {spread.toFixed(2)}
      </div>

      <div className="bids">
        {bidRows.map((row) => (
          <div key={`bid-${row.price}`} className="row bid">
            <span>{row.price.toFixed(2)}</span>
            <span>{row.quantity.toFixed(3)}</span>
            <span>{row.cumulative.toFixed(3)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
