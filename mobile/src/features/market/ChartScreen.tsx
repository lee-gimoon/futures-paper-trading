/**
 * Binance REST 과거 캔들과 Spring SSE로 갱신하는 진행 봉·호가를 함께 보여 준다.
 * 매수·매도 버튼은 Spring 주문 화면의 방향만 미리 선택한다.
 */
import { router } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppButton } from '@/components/AppButton';
import { AppIcon } from '@/components/AppIcon';
import { OrderBook } from '@/components/OrderBook';
import { PriceChart } from '@/features/market/chart/PriceChart';
import { useDepthStream } from '@/features/market/useDepthStream';
import { colors } from '@/theme/colors';
import { formatMarketPrice } from '@/utils/format';

export function ChartScreen() {
  const market = useDepthStream();
  const current = market.depth?.midPrice;
  const bestBid = market.depth?.bids[0]?.price;
  const bestAsk = market.depth?.asks[0]?.price;
  const spread =
    bestBid !== undefined && bestAsk !== undefined ? bestAsk - bestBid : null;

  const trade = (side: 'BUY' | 'SELL') =>
    router.navigate({
      pathname: '/trade',
      params: { side, intent: String(Date.now()) },
    });

  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.header}>
        <Pressable
          accessibilityLabel="이전 화면"
          accessibilityRole="button"
          onPress={() =>
            router.canGoBack() ? router.back() : router.replace('/market')
          }
          style={styles.back}
        >
          <AppIcon name="back" />
        </Pressable>
        <View style={styles.headerTitle}>
          <Text style={styles.symbol}>BTCUSDT</Text>
          <Text style={styles.caption}>USDⓈ-M · 무기한</Text>
        </View>
        <Text style={styles.paper}>모의 거래</Text>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.overview}>
          <View>
            <Text style={styles.price}>{formatMarketPrice(current)}</Text>
            <Text style={styles.springPrice}>비트코인 가격</Text>
          </View>
          <View style={styles.stats}>
            <Stat label="최우선 매수" value={formatMarketPrice(bestBid)} />
            <Stat label="최우선 매도" value={formatMarketPrice(bestAsk)} />
            <Stat label="스프레드" value={formatMarketPrice(spread)} />
          </View>
        </View>

        <View style={styles.connection}>
          <View
            style={[
              styles.dot,
              market.status === 'connected' && styles.connectedDot,
            ]}
          />
          <Text style={styles.caption}>
            {market.status === 'connected'
              ? 'Spring SSE 실시간 연결'
              : 'Spring SSE 재연결 중'}
          </Text>
          {market.status !== 'connected' ? (
            <Pressable
              accessibilityRole="button"
              onPress={market.reconnect}
              style={styles.retry}
            >
              <Text style={styles.retryText}>다시 연결</Text>
            </Pressable>
          ) : null}
        </View>
        {market.error ? <Text style={styles.error}>{market.error}</Text> : null}

        <View style={styles.tabHeading}>
          <Text style={styles.activeTab}>캔들 차트</Text>
          <Text style={styles.caption}>과거 Binance · 진행 봉 Spring 호가</Text>
        </View>
        <PriceChart
          liveConnected={market.status === 'connected'}
          liveKlines={market.liveKlines}
        />

        <View style={styles.book}>
          <OrderBook
            depth={market.depth}
            statusText={market.status === 'connected' ? '실시간' : '갱신 지연'}
          />
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <AppButton
          label="매수 / 롱"
          onPress={() => trade('BUY')}
          style={styles.flex}
          variant="buy"
        />
        <AppButton
          label="매도 / 숏"
          onPress={() => trade('SELL')}
          style={styles.flex}
          variant="sell"
        />
      </View>
    </SafeAreaView>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.stat}>
      <Text style={styles.caption}>{label}</Text>
      <Text style={styles.statValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.background },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingRight: 18,
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
  },
  back: {
    minWidth: 48,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerTitle: { flex: 1, gap: 4 },
  symbol: { color: colors.textPrimary, fontSize: 18, fontWeight: '800' },
  caption: { color: colors.textMuted, fontSize: 10, lineHeight: 15 },
  paper: {
    padding: 6,
    borderRadius: 4,
    backgroundColor: colors.accentMuted,
    color: colors.accentText,
    fontSize: 10,
  },
  content: { paddingHorizontal: 16, paddingBottom: 24 },
  overview: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 16,
    paddingVertical: 22,
  },
  price: {
    color: colors.textPrimary,
    fontSize: 28,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  springPrice: { marginTop: 8, color: colors.textMuted, fontSize: 10 },
  stats: { width: 145, justifyContent: 'space-between' },
  stat: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
  statValue: {
    color: colors.textSecondary,
    fontSize: 10,
    fontVariant: ['tabular-nums'],
  },
  connection: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 40,
    gap: 8,
  },
  dot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    backgroundColor: colors.textMuted,
  },
  connectedDot: { backgroundColor: colors.buyText },
  retry: { marginLeft: 'auto', padding: 8 },
  retryText: { color: colors.accentText, fontSize: 10 },
  error: { color: colors.sellText, fontSize: 11, marginBottom: 8 },
  tabHeading: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  activeTab: { color: colors.accentText, fontSize: 13, fontWeight: '700' },
  book: {
    marginTop: 16,
    paddingTop: 18,
    borderTopWidth: 5,
    borderTopColor: colors.surface,
  },
  footer: {
    flexDirection: 'row',
    gap: 10,
    padding: 14,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    backgroundColor: colors.background,
  },
  flex: { flex: 1 },
});
