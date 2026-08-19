import type { OrderBookLevel, OrderBookSnapshot } from '../../shared/types';
import { deriveQuote } from '../engine/quote';

// 표 한 행에 그릴 값들. price와 quantity는 서버 호가에서 받고,
// cumulative는 화면에서 누적 계산해 추가한다.
type Row = {
  price: number;
  quantity: number;
  cumulative: number;
};

// 매도 호가를 화면용 행으로 변환한다.
// 누적 수량은 최저 매도가(best ask)부터 계산해야 한다.
// 다만 화면에서는 높은 매도가가 위에 오므로, 누적 계산 후 행 순서를 뒤집는다.
function buildAskRows(asks: OrderBookLevel[]): Row[] {
  // `[...asks]`: asks의 원소를 펼쳐 만든 새 배열(얕은 복사). sort()가 원본 배열 순서를 바꾸므로 복사본을 정렬한다.
  // `sort((a, b) => ...)`: sort에 넘기는 화살표 비교 함수. sort가 배열 원소 두 개를 a, b에 넣어 필요할 때마다 호출한다.
  // `a.price - b.price`가 음수면 a를 앞에 두므로 가격이 낮은 순서(오름차순)가 된다.
  const sorted = [...asks].sort((a, b) => a.price - b.price);
  let cum = 0;
  const rows = sorted.map((lvl) => {
    cum += lvl.quantity;
    return { price: lvl.price, quantity: lvl.quantity, cumulative: cum }; // 객체 안의 속성 이름: 실제 값 (타입 표기 아님)
  });
  return rows.reverse();
}

// 매수 호가를 화면용 행으로 변환한다.
// 최고 매수가(best bid)부터 누적하고, 이 순서가 화면 표시 순서와도 같으므로 그대로 반환한다.
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
  // `?`는 선택 props 속성: 부모가 onPriceClick 속성을 전달하지 않아도 된다.
  onPriceClick?: (price: number) => void; // 전달되면 가격 행 클릭 → 주문폼 지정가로 입력(바이낸스식)
};

export function OrderBook({ snapshot, onPriceClick }: Props) {
  const askRows = buildAskRows(snapshot.asks);
  const bidRows = buildBidRows(snapshot.bids);

  // spread는 차트와 동일한 계산을 쓰도록 공용 deriveQuote에서 가져온다.
  const quote = deriveQuote(snapshot);
  const spread = quote ? quote.spread : 0;

  return (
    <div className="orderbook">
      <div className="header">
        <span>price</span>
        <span>quantity</span>
        <span>cumulative</span>
      </div>

      <div className="asks">
        {/*
          Array.prototype.map:
          배열의 각 원소에 콜백 함수를 한 번씩 호출하고, 각 호출의 반환값을 같은 순서로 모아 새 배열을 반환한다.
          원본 배열은 변경하지 않는다.
        */}
        {/*
          <div>...</div>는 JSX 표현식이고, 실행되면 React 요소 값이 됩니다.
          map은 그 React 요소 값들을 모아 새 배열로 만들고, React는 이 배열을 asks div 안에 들어갈 자식 목록으로 인식하며,
          ReactDOM이 실제 DOM div들로 렌더링합니다.
        */}
        {askRows.map((row) => (
          <div
            key={`ask-${row.price}`}
            className="row ask"
            onClick={() => {
              // `?.()` = onPriceClick 속성이 있을 때만 호출한다. 선택 props라 undefined일 수 있다.
              onPriceClick?.(row.price);
            }}
            title="클릭 → 지정가로 입력"
          >
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
          <div
            key={`bid-${row.price}`}
            className="row bid"
            onClick={() => {
              // `?.()` = onPriceClick 속성이 있을 때만 호출한다. 선택 props라 undefined일 수 있다.
              onPriceClick?.(row.price);
            }}
            title="클릭 → 지정가로 입력"
          >
            <span>{row.price.toFixed(2)}</span>
            <span>{row.quantity.toFixed(3)}</span>
            <span>{row.cumulative.toFixed(3)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
