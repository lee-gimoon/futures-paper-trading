/** Spring의 OrderBookSnapshot 응답과 같은 구조다. */
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

export type DepthView = OrderBookSnapshot & {
  midPrice: number | null;
};

export type DepthConnectionStatus =
  | 'loading'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'paused'
  | 'error';
