import type { UTCTimestamp } from 'lightweight-charts';

// 바이낸스 USDⓈ-M 선물 kline(캔들) 데이터를 프론트에서 "직접" 가져온다.
// 백엔드(Spring)를 거치지 않고 브라우저 → 바이낸스로 직접 REST/WebSocket 호출한다.
//   - 과거 봉:  REST       https://fapi.binance.com/fapi/v1/klines
//   - 실시간 봉: WebSocket  wss://fstream.binance.com/ws/<symbol>@kline_<interval>

const SYMBOL = 'btcusdt';
const REST_BASE = 'https://fapi.binance.com/fapi/v1/klines';

// 차트 한 봉. Lightweight Charts의 CandlestickData와 호환되는 모양.
export type Candle = {
  time: UTCTimestamp; // 봉 시작 시각(초 단위)
  open: number;
  high: number;
  low: number;
  close: number;
};

// 선물 kline이 지원하는 인터벌(1초봉은 선물에 없음).
export const INTERVALS = ['1m', '5m', '15m', '1h', '4h', '1d'] as const;
export type Interval = (typeof INTERVALS)[number];

// REST 응답 한 줄: [openTime(ms), open, high, low, close, volume, closeTime(ms), ...]
type RawKline = [number, string, string, string, string, string, number, ...unknown[]];

// 과거 봉을 limit개 가져온다. (차트를 처음 채울 때 1회)
export async function fetchKlineHistory(interval: Interval, limit = 500): Promise<Candle[]> {
  const url = `${REST_BASE}?symbol=${SYMBOL.toUpperCase()}&interval=${interval}&limit=${limit}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`kline REST 실패: ${res.status}`);
  const rows: RawKline[] = await res.json();
  return rows.map(toCandle);
}

function toCandle(k: RawKline): Candle {
  return {
    time: Math.floor(k[0] / 1000) as UTCTimestamp, // openTime(ms) → 초
    open: Number(k[1]),
    high: Number(k[2]),
    low: Number(k[3]),
    close: Number(k[4]),
  };
}

// 인터벌(봉) 길이를 초로 변환. 진행 봉의 "시간 버킷" 계산에 쓴다.
// (실시간 봉은 백엔드 호가 snapshot의 best ask로 CandleChart에서 직접 묶는다.
//  바이낸스 선물의 체결 계열 push 스트림이 이 지역에서 막혀 있어 @kline WS는 못 씀.)
const INTERVAL_SECONDS: Record<Interval, number> = {
  '1m': 60,
  '5m': 300,
  '15m': 900,
  '1h': 3600,
  '4h': 14400,
  '1d': 86400,
};

export function intervalSeconds(interval: Interval): number {
  return INTERVAL_SECONDS[interval];
}
