// 백엔드 OrderBookSnapshot record와 1:1 대응하는 타입.
// 백엔드는 BigDecimal price/quantity를 기본 Jackson 설정으로 JSON 숫자로 직렬화한다.
// 4단계에서는 표시만 하므로 number로 받는다. (정밀도가 문제가 되는 단계에서 string으로 바꿀 예정)
export type OrderBookLevel = {
  price: number;
  quantity: number;
};

export type OrderBookSnapshot = {
  symbol: string;
  eventTime: number;
  bids: OrderBookLevel[];
  asks: OrderBookLevel[];
};

// 백엔드 UserResponse(id, email, displayName)와 1:1 대응. 로그인 상태 표시에 쓴다.
export type User = {
  id: number;
  email: string;
  displayName: string;
};

export type OrderSide = 'BUY' | 'SELL';
export type OrderType = 'MARKET' | 'LIMIT';
export type OrderStatus = 'NEW' | 'OPEN' | 'FILLED' | 'CANCELED' | 'REJECTED';
export type PositionSide = 'LONG' | 'SHORT';

// 백엔드 OrderResponse와 1:1 대응. 주문 1건 요약(주문 폼 응답·주문 목록에 쓴다).
export type Order = {
  id: number;
  symbol: string;
  side: OrderSide;
  type: OrderType;
  status: OrderStatus;
  limitPrice: number | null;
  quantity: number;
  filledQuantity: number;
  avgPrice: number | null;
};

// 백엔드 PortfolioResponse.PositionView와 1:1 대응. 현재 포지션(없으면 portfolio.position이 null).
export type Position = {
  symbol: string;
  side: PositionSide;
  quantity: number; // 절대값
  averageEntryPrice: number;
  markPrice: number | null;
  unrealizedPnl: number;
  notional: number; // 명목금액 = 진입가 × 수량
  liquidationPrice: number; // 추정 청산가
  leverage: number; // 이 포지션이 진입 시점에 고정한 레버리지 (신규 주문 레버리지와 별개)
};

// 백엔드 PortfolioResponse와 1:1 대응. 계좌 한 화면분.
export type Portfolio = {
  cashBalance: number;
  realizedPnl: number;
  unrealizedPnl: number;
  equity: number;
  leverage: number; // 레버리지 배수
  usedMargin: number; // 포지션에 묶인 증거금
  availableBalance: number; // 새 주문에 쓸 수 있는 여유 (% 바 사이징 기준)
  position: Position | null;
};

// 백엔드 FillResponse와 1:1 대응. 체결 1건(체결 내역에 쓴다).
export type Fill = {
  id: number;
  orderId: number;
  symbol: string;
  side: OrderSide;
  price: number;
  quantity: number;
};
