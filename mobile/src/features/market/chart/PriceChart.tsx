/**
 * Binance REST 과거 캔들 뒤에 Spring 호가로 만든 진행 중 캔들을 이어 표시한다.
 * 진행 봉은 frontend와 같이 Spring SSE의 최우선 매도호가로 OHLC를 갱신한다.
 */
import { useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Svg, { G, Line, Rect, Text as SvgText } from 'react-native-svg';

import { useKlines } from '@/features/market/chart/useKlines';
import type { LiveKlinesByInterval } from '@/features/market/useDepthStream';
import { colors } from '@/theme/colors';
import type { Kline, KlineInterval } from '@/types/chart';
import { formatMarketPrice, formatVolume } from '@/utils/format';

const INTERVALS: { value: KlineInterval; label: string }[] = [
  { value: '1m', label: '1분' },
  { value: '5m', label: '5분' },
  { value: '15m', label: '15분' },
  { value: '1h', label: '1시간' },
  { value: '4h', label: '4시간' },
  { value: '1d', label: '1일' },
];

const RIGHT_GUTTER = 66;
const TOP_PADDING = 8;
const VOLUME_HEIGHT = 32;

function mergeKlines(history: Kline[], live: Kline[]): Kline[] {
  const byOpenTime = new Map<number, Kline>();
  history.forEach((item) => byOpenTime.set(item.openTime, item));
  live.forEach((item) => {
    const historical = byOpenTime.get(item.openTime);
    byOpenTime.set(
      item.openTime,
      historical
        ? {
            ...historical,
            high: Math.max(historical.high, item.high),
            low: Math.min(historical.low, item.low),
            close: item.close,
          }
        : item,
    );
  });
  return [...byOpenTime.values()]
    .sort((left, right) => left.openTime - right.openTime)
    .slice(-80);
}

export function PriceChart({
  liveKlines,
  liveConnected,
  compact = false,
}: {
  liveKlines: LiveKlinesByInterval;
  liveConnected: boolean;
  compact?: boolean;
}) {
  const [interval, setInterval] = useState<KlineInterval>('5m');
  const [width, setWidth] = useState(0);
  const history = useKlines(interval);
  const height = compact ? 166 : 258;

  const candles = useMemo(
    () =>
      mergeKlines(history.klines, liveKlines[interval]).slice(
        -(compact ? 44 : 64),
      ),
    [compact, history.klines, interval, liveKlines],
  );
  const latest = candles.at(-1);
  const previous = candles.at(-2);
  const change =
    latest && previous && previous.close > 0
      ? ((latest.close - previous.close) / previous.close) * 100
      : 0;

  const chart = useMemo(() => {
    if (candles.length === 0 || width === 0) return null;
    const plotWidth = Math.max(width - RIGHT_GUTTER, 1);
    const priceHeight = height - VOLUME_HEIGHT - 20;
    const lowest = Math.min(...candles.map((item) => item.low));
    const highest = Math.max(...candles.map((item) => item.high));
    const rangePadding = Math.max((highest - lowest) * 0.08, highest * 0.00001);
    const low = lowest - rangePadding;
    const high = highest + rangePadding;
    const priceRange = Math.max(high - low, 1e-9);
    const slot = plotWidth / candles.length;
    const bodyWidth = Math.max(2, Math.min(slot * 0.62, 9));
    const maxVolume = Math.max(...candles.map((item) => item.volume), 1);
    const x = (index: number) => index * slot + slot / 2;
    const y = (price: number) =>
      TOP_PADDING + ((high - price) / priceRange) * (priceHeight - TOP_PADDING);

    return {
      plotWidth,
      priceHeight,
      low,
      high,
      bodyWidth,
      maxVolume,
      x,
      y,
    };
  }, [candles, height, width]);

  return (
    <View style={styles.container}>
      <View style={styles.heading}>
        <View>
          <Text style={styles.title}>BTCUSDT 캔들</Text>
          <Text style={styles.source}>
            과거: Binance REST · 진행 봉: Spring 호가
          </Text>
        </View>
        <View style={styles.liveState}>
          <View style={[styles.dot, liveConnected && styles.connectedDot]} />
          <Text style={styles.liveText}>
            {liveConnected ? '실시간' : '호가 연결 중'}
          </Text>
        </View>
      </View>

      <View style={styles.intervals}>
        {INTERVALS.map((item) => (
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ selected: interval === item.value }}
            key={item.value}
            onPress={() => setInterval(item.value)}
            style={[
              styles.interval,
              interval === item.value && styles.selectedInterval,
            ]}
          >
            <Text
              style={[
                styles.intervalText,
                interval === item.value && styles.selectedIntervalText,
              ]}
            >
              {item.label}
            </Text>
          </Pressable>
        ))}
      </View>

      {latest ? (
        <View style={styles.summary}>
          <Text style={styles.latestPrice}>
            {formatMarketPrice(latest.close)}
          </Text>
          <Text
            style={[
              styles.change,
              { color: change >= 0 ? colors.buyText : colors.sellText },
            ]}
          >
            {change >= 0 ? '+' : ''}
            {change.toFixed(3)}%
          </Text>
          {!compact ? (
            <Text style={styles.ohlc}>
              시 {formatMarketPrice(latest.open)} · 고{' '}
              {formatMarketPrice(latest.high)} · 저{' '}
              {formatMarketPrice(latest.low)} · 거래량{' '}
              {formatVolume(latest.volume)} BTC
            </Text>
          ) : null}
        </View>
      ) : null}

      <View
        onLayout={(event) => setWidth(event.nativeEvent.layout.width)}
        style={{ height }}
      >
        {history.loading && candles.length === 0 ? (
          <View style={styles.empty}>
            <Text style={styles.emptyText}>
              Binance 캔들을 불러오는 중입니다.
            </Text>
          </View>
        ) : history.error && candles.length === 0 ? (
          <View style={styles.empty}>
            <Text style={styles.error}>{history.error}</Text>
            <Pressable
              accessibilityRole="button"
              onPress={history.refresh}
              style={styles.retry}
            >
              <Text style={styles.retryText}>다시 불러오기</Text>
            </Pressable>
          </View>
        ) : chart ? (
          <Svg
            accessibilityLabel="Binance BTCUSDT 캔들 차트"
            height={height}
            width={width}
          >
            {[0, 0.5, 1].map((ratio) => {
              const lineY =
                TOP_PADDING + ratio * (chart.priceHeight - TOP_PADDING);
              const price = chart.high - ratio * (chart.high - chart.low);
              return (
                <G key={ratio}>
                  <Line
                    stroke={colors.divider}
                    strokeDasharray="3 4"
                    x1={0}
                    x2={chart.plotWidth}
                    y1={lineY}
                    y2={lineY}
                  />
                  <SvgText
                    fill={colors.textMuted}
                    fontSize={9}
                    x={chart.plotWidth + 5}
                    y={lineY + 3}
                  >
                    {formatMarketPrice(price)}
                  </SvgText>
                </G>
              );
            })}
            {candles.map((candle, index) => {
              const rising = candle.close >= candle.open;
              const candleColor = rising ? colors.buyText : colors.sellText;
              const centerX = chart.x(index);
              const openY = chart.y(candle.open);
              const closeY = chart.y(candle.close);
              const bodyY = Math.min(openY, closeY);
              const bodyHeight = Math.max(Math.abs(closeY - openY), 1);
              const volumeHeight =
                (candle.volume / chart.maxVolume) * (VOLUME_HEIGHT - 5);
              return (
                <G key={candle.openTime}>
                  <Line
                    stroke={candleColor}
                    strokeWidth={1}
                    x1={centerX}
                    x2={centerX}
                    y1={chart.y(candle.high)}
                    y2={chart.y(candle.low)}
                  />
                  <Rect
                    fill={candleColor}
                    height={bodyHeight}
                    width={chart.bodyWidth}
                    x={centerX - chart.bodyWidth / 2}
                    y={bodyY}
                  />
                  <Rect
                    fill={candleColor}
                    height={volumeHeight}
                    opacity={0.38}
                    width={chart.bodyWidth}
                    x={centerX - chart.bodyWidth / 2}
                    y={height - volumeHeight}
                  />
                </G>
              );
            })}
            <Line
              stroke={colors.borderStrong}
              x1={0}
              x2={chart.plotWidth}
              y1={chart.priceHeight + 8}
              y2={chart.priceHeight + 8}
            />
          </Svg>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingVertical: 10 },
  heading: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: { color: colors.textPrimary, fontSize: 13, fontWeight: '700' },
  source: { marginTop: 4, color: colors.textMuted, fontSize: 9 },
  liveState: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: colors.textMuted,
  },
  connectedDot: { backgroundColor: colors.buyText },
  liveText: { color: colors.textMuted, fontSize: 9 },
  intervals: { flexDirection: 'row', gap: 3, marginTop: 12 },
  interval: {
    flex: 1,
    minHeight: 32,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 4,
  },
  selectedInterval: { backgroundColor: colors.accentMuted },
  intervalText: { color: colors.textMuted, fontSize: 9 },
  selectedIntervalText: { color: colors.accentText, fontWeight: '700' },
  summary: {
    flexDirection: 'row',
    alignItems: 'baseline',
    flexWrap: 'wrap',
    gap: 8,
    minHeight: 35,
    paddingTop: 9,
  },
  latestPrice: {
    color: colors.textPrimary,
    fontSize: 15,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  change: { fontSize: 9, fontVariant: ['tabular-nums'] },
  ohlc: { color: colors.textMuted, fontSize: 8 },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
  emptyText: { color: colors.textMuted, fontSize: 10 },
  error: { color: colors.sellText, fontSize: 10, textAlign: 'center' },
  retry: { minHeight: 38, justifyContent: 'center', paddingHorizontal: 12 },
  retryText: { color: colors.accentText, fontSize: 10 },
});
