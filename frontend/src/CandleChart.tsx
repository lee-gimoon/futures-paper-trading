import { useEffect, useRef, useState } from 'react';
import {
  createChart,
  CandlestickSeries,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from 'lightweight-charts';
import {
  INTERVALS,
  fetchKlineHistory,
  intervalSeconds,
  type Candle,
  type Interval,
} from './binanceKline';
import { deriveQuote } from './quote';
import type { OrderBookSnapshot } from './types';

type Props = {
  snapshot: OrderBookSnapshot | null;
};

// 캔들 차트.
// - 과거 봉: 바이낸스 kline REST로 한 번 채운다(실제 체결 OHLC).
// - 진행 봉: 내 백엔드 호가(SSE) snapshot의 mid로 실시간 갱신한다 → 호가창과 같은 값.
//   (호가 snapshot은 "가격 한 개"라, 진행 봉의 OHLC는 여기서 직접 묶는다.)
export function CandleChart({ snapshot }: Props) {
  const [activeInterval, setActiveInterval] = useState<Interval>('1m');
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const currentBarRef = useRef<Candle | null>(null); // 진행 중인 봉
  const readyRef = useRef(false); // 현재 인터벌의 과거 봉이 채워졌는지

  // 마운트 1회: 차트와 캔들 시리즈를 만든다.
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const chart = createChart(container, {
      autoSize: true, // 컨테이너(.chart)의 CSS width/height를 자동으로 따라간다
      layout: { background: { color: '#0d0d0d' }, textColor: '#e0e0e0' },
      grid: { vertLines: { color: '#1f1f1f' }, horzLines: { color: '#1f1f1f' } },
      timeScale: { timeVisible: true, secondsVisible: false },
    });

    chartRef.current = chart;
    seriesRef.current = chart.addSeries(CandlestickSeries, {
      upColor: '#26a69a',
      downColor: '#ef5350',
      borderVisible: false,
      wickUpColor: '#26a69a',
      wickDownColor: '#ef5350',
    });

    return () => {
      chart.remove();
    };
  }, []);

  // 인터벌이 바뀔 때마다: 과거 봉을 REST로 채운다.
  useEffect(() => {
    const series = seriesRef.current;
    if (!series) return;

    let aborted = false;
    readyRef.current = false;
    currentBarRef.current = null;

    fetchKlineHistory(activeInterval)
      .then((candles) => {
        if (aborted) return;
        series.setData(candles);
        if (candles.length > 0) {
          currentBarRef.current = { ...candles[candles.length - 1] }; // 진행 봉의 시작점
        }
        chartRef.current?.timeScale().fitContent();
        readyRef.current = true;
      })
      .catch((err) => console.error('kline 과거 봉 로드 실패', err));

    return () => {
      aborted = true;
    };
  }, [activeInterval]);

  // 호가 snapshot이 올 때마다: mid로 진행 봉을 갱신한다.
  useEffect(() => {
    const series = seriesRef.current;
    if (!series || !snapshot || !readyRef.current) return;

    const quote = deriveQuote(snapshot);
    if (!quote) return;

    const price = quote.bestAsk; // 호가창의 최우선 매도호가 (mid 평균이 아님)
    const sec = intervalSeconds(activeInterval);
    // 이 가격이 속한 봉의 시작 시간(초). UTC 정렬이라 바이낸스 kline 경계와 일치.
    const t = (Math.floor(snapshot.eventTime / 1000 / sec) * sec) as UTCTimestamp;

    const bar = currentBarRef.current;
    if (!bar || t > bar.time) {
      // 새 봉 시작
      const newBar: Candle = { time: t, open: price, high: price, low: price, close: price };
      currentBarRef.current = newBar;
      series.update(newBar);
    } else if (t === bar.time) {
      // 같은 봉 갱신: close=현재가, 고가/저가 확장
      bar.close = price;
      if (price > bar.high) bar.high = price;
      if (price < bar.low) bar.low = price;
      series.update(bar);
    }
    // t < bar.time(인터벌 전환 과도기 등)이면 무시
  }, [snapshot, activeInterval]);

  return (
    <div className="chart-wrap">
      <div className="timeframes">
        {INTERVALS.map((iv) => (
          <button
            key={iv}
            className={iv === activeInterval ? 'tf active' : 'tf'}
            onClick={() => setActiveInterval(iv)}
          >
            {iv}
          </button>
        ))}
      </div>
      <div className="chart" ref={containerRef} />
    </div>
  );
}
