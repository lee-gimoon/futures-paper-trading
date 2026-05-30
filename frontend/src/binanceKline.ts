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

// 실시간 봉 갱신.
//
// 원래는 @kline WebSocket을 쓰려 했으나, 일부 지역/네트워크에서 바이낸스 선물의
// 체결 계열 push 스트림(kline·aggTrade·markPrice)이 차단돼 메시지가 0개로 온다.
// (반면 호가 계열 depth·bookTicker, 그리고 REST는 정상.) 그래서 REST를 주기적으로
// 폴링해 최신 봉을 갱신한다. 전부 선물 데이터라 호가창과 가격이 맞는다.
//
// 마지막 2개 봉을 갱신한다: 직전 봉(분 경계에서 확정) + 현재 진행 중인 봉.
// 반환된 함수를 호출하면 폴링을 멈춘다.
export function pollLatestKline(
  interval: Interval,
  onCandle: (candle: Candle) => void,
  periodMs = 300,
): () => void {
  let stopped = false;
  let timer: ReturnType<typeof setTimeout>;

  const tick = async () => {
    try {
      const candles = await fetchKlineHistory(interval, 2);
      if (stopped) return;
      candles.forEach(onCandle); // 오래된 것 → 최신 순(REST는 오름차순)
    } catch (err) {
      console.error('kline 폴링 실패', err);
    }
    // 이전 요청이 끝난 뒤 다음 요청을 예약한다 → 응답이 느려도 요청이 쌓이지 않음.
    if (!stopped) timer = setTimeout(tick, periodMs);
  };

  tick(); // 즉시 1회
  return () => {
    stopped = true;
    clearTimeout(timer);
  };
}
