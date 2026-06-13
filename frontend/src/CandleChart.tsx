import { useEffect, useRef, useState } from 'react';
import {
  createChart,
  CandlestickSeries,
  LineStyle,
  type IChartApi,
  type IPriceLine,
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
import type { Order, OrderBookSnapshot, Position } from './types';

type Props = {
  snapshot: OrderBookSnapshot | null;
  position?: Position | null;
  openOrders?: Order[];
};

const INITIAL_VISIBLE_BARS = 190;
const RIGHT_MARGIN_BARS = 16;

function applyInitialVisibleRange(chart: IChartApi, dataLength: number) {
  if (dataLength <= 0) return;

  const to = dataLength - 1 + RIGHT_MARGIN_BARS;
  const from = Math.max(0, to - INITIAL_VISIBLE_BARS);
  chart.timeScale().setVisibleLogicalRange({ from, to });
}

function orderPositionLabel(side: string) {
  return side === 'BUY' ? 'Long' : 'Short';
}

function priceLineKey(position: Position | null | undefined, openOrders: Order[]) {
  const positionPart = position
    ? `${position.side}:${position.averageEntryPrice}:${position.quantity}`
    : 'flat';
  const orderPart = openOrders
    .filter((order) => order.status === 'OPEN' && order.limitPrice != null)
    .map((order) => `${order.id}:${order.side}:${order.limitPrice}:${order.quantity}:${order.filledQuantity}`)
    .join('|');
  return `${positionPart}::${orderPart}`;
}

// 캔들 차트.
// - 과거 봉: 바이낸스 kline REST로 한 번 채운다(실제 체결 OHLC).
// - 진행 봉: 내 백엔드 호가(SSE) snapshot의 mid로 실시간 갱신한다 → 호가창과 같은 값.
//   (호가 snapshot은 "가격 한 개"라, 진행 봉의 OHLC는 여기서 직접 묶는다.)
export function CandleChart({ snapshot, position = null, openOrders = [] }: Props) {
  const [activeInterval, setActiveInterval] = useState<Interval>('1m');
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const priceLinesRef = useRef<IPriceLine[]>([]);
  const currentBarRef = useRef<Candle | null>(null); // 진행 중인 봉
  const readyRef = useRef(false); // 현재 인터벌의 과거 봉이 채워졌는지
  const markerKey = priceLineKey(position, openOrders);

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
      for (const line of priceLinesRef.current) {
        seriesRef.current?.removePriceLine(line);
      }
      priceLinesRef.current = [];
      seriesRef.current = null;
      chartRef.current = null;
      chart.remove();
    };
  }, []);

  // 바이낸스처럼 내 진입가와 대기 주문가를 차트 가격선으로 표시한다.
  useEffect(() => {
    const series = seriesRef.current;
    if (!series) return;

    for (const line of priceLinesRef.current) {
      series.removePriceLine(line);
    }
    priceLinesRef.current = [];

    if (position && position.quantity > 0) {
      priceLinesRef.current.push(
        series.createPriceLine({
          price: position.averageEntryPrice,
          color: '#f59e0b',
          lineWidth: 2,
          lineStyle: LineStyle.Solid,
          axisLabelVisible: true,
          axisLabelColor: '#f59e0b',
          axisLabelTextColor: '#0d0d0d',
          title: `진입가 ${position.side}`,
        }),
      );
    }

    for (const order of openOrders) {
      if (order.status !== 'OPEN' || order.limitPrice == null) continue;

      const remainingQty = Math.max(order.quantity - order.filledQuantity, 0);
      const color = order.side === 'BUY' ? '#22c55e' : '#ef4444';
      priceLinesRef.current.push(
        series.createPriceLine({
          price: order.limitPrice,
          color,
          lineWidth: 1,
          lineStyle: LineStyle.Dashed,
          axisLabelVisible: true,
          axisLabelColor: color,
          axisLabelTextColor: '#ffffff',
          title: `대기 ${orderPositionLabel(order.side)} ${remainingQty.toFixed(3)}`,
        }),
      );
    }
  }, [markerKey]);

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
        const chart = chartRef.current;
        if (chart) applyInitialVisibleRange(chart, candles.length);
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
