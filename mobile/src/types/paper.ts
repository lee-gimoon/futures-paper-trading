export type OrderSide = 'BUY' | 'SELL';
export type OrderType = 'MARKET' | 'LIMIT';
export type OrderStatus = 'NEW' | 'OPEN' | 'FILLED' | 'CANCELED' | 'REJECTED';
export type PositionSide = 'LONG' | 'SHORT';

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

export type Position = {
  symbol: string;
  side: PositionSide;
  quantity: number;
  averageEntryPrice: number;
  markPrice: number | null;
  unrealizedPnl: number;
  notional: number;
  liquidationPrice: number;
  leverage: number;
};

export type Portfolio = {
  cashBalance: number;
  realizedPnl: number;
  unrealizedPnl: number;
  equity: number;
  leverage: number;
  usedMargin: number;
  availableBalance: number;
  position: Position | null;
};

export type Fill = {
  id: number;
  orderId: number;
  symbol: string;
  side: OrderSide;
  price: number;
  quantity: number;
};

export type CreateOrderInput = {
  side: OrderSide;
  type: OrderType;
  quantity: number;
  limitPrice?: number;
};
