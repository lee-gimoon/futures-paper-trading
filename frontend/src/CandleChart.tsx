import { useEffect, useRef, useState } from 'react';
import {
  createChart,
  CandlestickSeries,
  type IChartApi,
  type ISeriesApi,
} from 'lightweight-charts';
import { INTERVALS, fetchKlineHistory, pollLatestKline, type Interval } from './binanceKline';

// 바이낸스 kline으로 그리는 진짜 캔들 차트.
// 데이터는 백엔드를 거치지 않고 브라우저가 바이낸스에 직접 요청한다(REST + WebSocket).
//
// 설계: 차트/시리즈는 useRef에 보관(React 리렌더와 분리). 인터벌이 바뀌면
// 과거 봉을 REST로 다시 채우고(setData), 실시간 봉을 WebSocket으로 갱신한다(update).
export function CandleChart() {
  const [activeInterval, setActiveInterval] = useState<Interval>('1m');
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const lastTimeRef = useRef<number>(0); // 차트에 들어간 마지막 봉의 시간(초)

  // 마운트 1회: 차트와 캔들 시리즈를 만든다.
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const chart = createChart(container, {
      autoSize: true, // 컨테이너(.chart)의 CSS width/height를 자동으로 따라간다
      layout: {
        background: { color: '#0d0d0d' },
        textColor: '#e0e0e0',
      },
      grid: {
        vertLines: { color: '#1f1f1f' },
        horzLines: { color: '#1f1f1f' },
      },
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

  // 인터벌이 바뀔 때마다: 과거 봉 REST로 채우고, REST 폴링으로 실시간 갱신.
  useEffect(() => {
    const series = seriesRef.current;
    if (!series) return;

    // 인터벌이 빠르게 바뀔 때 늦게 도착한 응답이 새 데이터를 덮어쓰지 않도록 가드.
    let aborted = false;
    lastTimeRef.current = 0; // 인터벌이 바뀌면 마지막 봉 시간 초기화

    fetchKlineHistory(activeInterval)
      .then((candles) => {
        if (aborted) return;
        series.setData(candles);
        if (candles.length > 0) lastTimeRef.current = candles[candles.length - 1].time;
        chartRef.current?.timeScale().fitContent();
      })
      .catch((err) => console.error('kline 과거 봉 로드 실패', err));

    const stopPolling = pollLatestKline(activeInterval, (candle) => {
      if (aborted) return;
      // 마지막 봉보다 과거인 봉은 건너뛴다.
      // (update에 과거 시간을 주면 에러가 나서, 직전 봉 update가 진행 봉 update를 막던 버그)
      if (candle.time < lastTimeRef.current) return;
      // 같은 time이면 마지막 봉을 갱신, 새 time이면 새 봉을 추가한다.
      series.update(candle);
      lastTimeRef.current = candle.time;
    });

    return () => {
      aborted = true;
      stopPolling();
    };
  }, [activeInterval]);

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
