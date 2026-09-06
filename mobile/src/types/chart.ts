export type KlineInterval = '1m' | '5m' | '15m' | '1h' | '4h' | '1d';

export type Kline = {
  openTime: number;
  closeTime: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
};
