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
