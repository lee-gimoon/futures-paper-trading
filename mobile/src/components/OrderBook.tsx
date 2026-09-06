/**
 * 매도 호가·현재 가격·매수 호가를 한 영역에 표시한다.
 * 정해진 개수의 상위 호가만 그리므로 주문 화면의 바깥 ScrollView와 스크롤이 충돌하지 않는다.
 */
import {
  Pressable,
  StyleProp,
  StyleSheet,
  Text,
  View,
  ViewStyle,
} from 'react-native';
import { colors } from '@/theme/colors';
import type { DepthView, OrderBookLevel } from '@/types/market';
import { formatMarketPrice, formatQuantity } from '@/utils/format';

type OrderBookProps = {
  depth: DepthView | null;
  statusText: string;
  onSelectPrice?: (price: number) => void;
  style?: StyleProp<ViewStyle>;
  compact?: boolean;
  asset?: string;
};

export function OrderBook({
  depth,
  statusText,
  onSelectPrice,
  style,
  compact = false,
  asset = 'BTC',
}: OrderBookProps) {
  const count = compact ? 6 : 10;
  const asks = depth?.asks.slice(0, count).reverse() ?? [];
  const bids = depth?.bids.slice(0, count) ?? [];
  const maxQuantity = Math.max(
    ...asks.concat(bids).map((item) => item.quantity),
    1e-9,
  );
  const bidQuantity = bids.reduce((sum, item) => sum + item.quantity, 0);
  const askQuantity = asks.reduce((sum, item) => sum + item.quantity, 0);
  const bidRatio =
    bidQuantity + askQuantity > 0
      ? bidQuantity / (bidQuantity + askQuantity)
      : 0.5;

  const row = (item: OrderBookLevel, side: 'bid' | 'ask') => (
    <Pressable
      key={side + item.price}
      accessibilityRole={onSelectPrice ? 'button' : undefined}
      accessibilityLabel={
        (side === 'bid' ? '매수' : '매도') +
        ' 호가 ' +
        item.price +
        ', 수량 ' +
        item.quantity
      }
      disabled={!onSelectPrice}
      onPress={() => onSelectPrice?.(item.price)}
      style={[styles.row, compact && styles.compactRow]}
    >
      {/* 수량이 큰 가격대일수록 배경 막대를 길게 그린다. */}
      <View
        pointerEvents="none"
        style={[
          styles.bar,
          {
            width: `${(item.quantity / maxQuantity) * 100}%`,
            backgroundColor:
              side === 'bid' ? colors.buyMuted : colors.sellMuted,
          },
        ]}
      />
      <Text
        numberOfLines={1}
        style={[
          styles.number,
          compact && styles.smallNumber,
          { color: side === 'bid' ? colors.buyText : colors.sellText },
        ]}
      >
        {formatMarketPrice(item.price)}
      </Text>
      <Text
        numberOfLines={1}
        style={[styles.quantity, compact && styles.smallNumber]}
      >
        {formatQuantity(item.quantity)}
      </Text>
    </Pressable>
  );
  return (
    <View style={[styles.container, style]}>
      {!compact ? (
        <View style={styles.heading}>
          <Text style={styles.title}>호가</Text>
          <Text style={styles.status}>{statusText}</Text>
        </View>
      ) : null}
      <View style={styles.headers}>
        <Text style={styles.label}>가격(USDT)</Text>
        <Text style={styles.label}>수량({asset})</Text>
      </View>
      {asks.length ? (
        asks.map((item) => row(item, 'ask'))
      ) : (
        <Text style={styles.empty}>매도 호가 대기 중</Text>
      )}
      <View style={styles.mid}>
        <Text
          numberOfLines={1}
          style={[styles.midPrice, compact && { fontSize: 17 }]}
        >
          {formatMarketPrice(depth?.midPrice)}
        </Text>
        <Text style={styles.label}>
          {compact ? statusText : '현재 가격'}
        </Text>
      </View>
      {bids.length ? (
        bids.map((item) => row(item, 'bid'))
      ) : (
        <Text style={styles.empty}>매수 호가 대기 중</Text>
      )}
      {depth ? (
        <>
          <View style={styles.ratio}>
            <View style={{ flex: bidRatio, backgroundColor: colors.buyText }} />
            <View
              style={{ flex: 1 - bidRatio, backgroundColor: colors.sellText }}
            />
          </View>
          <View style={styles.headers}>
            <Text style={[styles.label, { color: colors.buyText }]}>
              매수 {Math.round(bidRatio * 100)}%
            </Text>
            <Text style={[styles.label, { color: colors.sellText }]}>
              매도 {Math.round((1 - bidRatio) * 100)}%
            </Text>
          </View>
        </>
      ) : null}
    </View>
  );
}
const styles = StyleSheet.create({
  container: { minWidth: 0 },
  heading: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingBottom: 10,
  },
  title: { color: colors.textPrimary, fontSize: 15, fontWeight: '700' },
  status: { color: colors.textMuted, fontSize: 10 },
  headers: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 7,
  },
  label: { color: colors.textMuted, fontSize: 9 },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    minHeight: 32,
    paddingHorizontal: 3,
    gap: 4,
  },
  compactRow: { minHeight: 25 },
  bar: { position: 'absolute', right: 0, top: 1, bottom: 1 },
  number: {
    color: colors.textPrimary,
    fontSize: 12,
    fontVariant: ['tabular-nums'],
  },
  smallNumber: { fontSize: 11 },
  quantity: {
    color: colors.textSecondary,
    fontSize: 12,
    fontVariant: ['tabular-nums'],
  },
  mid: { paddingVertical: 10, gap: 3 },
  midPrice: {
    color: colors.textPrimary,
    fontSize: 22,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  empty: {
    color: colors.textMuted,
    fontSize: 10,
    paddingVertical: 55,
    textAlign: 'center',
  },
  ratio: { height: 3, flexDirection: 'row', gap: 3, marginTop: 12 },
});
