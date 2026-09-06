/**
 * BTCUSDT 하나만 다루는 시세 첫 화면이다.
 * 과거 캔들은 Binance REST에서 한 번 받고, 진행 봉과 현재 호가는 Spring SSE로 갱신한다.
 */
import { router } from 'expo-router';
import {
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppButton } from '@/components/AppButton';
import { AppIcon } from '@/components/AppIcon';
import { OrderBook } from '@/components/OrderBook';
import { useAuth } from '@/features/auth/useAuth';
import { PriceChart } from '@/features/market/chart/PriceChart';
import { useDepthStream } from '@/features/market/useDepthStream';
import { colors } from '@/theme/colors';
import { formatMarketPrice } from '@/utils/format';

export function MarketScreen() {
  const { status } = useAuth();
  const market = useDepthStream();
  const bestBid = market.depth?.bids[0]?.price;
  const bestAsk = market.depth?.asks[0]?.price;
  const spread =
    bestBid !== undefined && bestAsk !== undefined ? bestAsk - bestBid : null;
  const firstPrice = market.priceHistory[0]?.price;
  const currentPrice = market.depth?.midPrice;
  const change =
    firstPrice && currentPrice
      ? ((currentPrice - firstPrice) / firstPrice) * 100
      : 0;
  const connected = market.status === 'connected';

  return (
    <SafeAreaView edges={['top', 'left', 'right']} style={styles.safeArea}>
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={
          <RefreshControl
            onRefresh={market.reconnect}
            refreshing={
              market.status === 'loading' || market.status === 'connecting'
            }
            tintColor={colors.accent}
          />
        }
      >
        <View style={styles.brandRow}>
          <View style={styles.brand}>
            <View style={styles.brandMark}>
              <AppIcon name="trade" color={colors.onAccent} size={18} />
            </View>
            <Text style={styles.brandText}>
              FUTURES<Text style={styles.brandLight}> / PAPER</Text>
            </Text>
          </View>
          <Pressable
            accessibilityRole="button"
            onPress={() =>
              router.push(status === 'authenticated' ? '/account' : '/login')
            }
            style={styles.login}
          >
            <Text style={styles.loginText}>
              {status === 'authenticated' ? '내 자산' : '로그인'}
            </Text>
          </Pressable>
        </View>

        <Text style={styles.title}>BTCUSDT</Text>
        <Text style={styles.subtitle}>USDⓈ-M 무기한 · 모의 선물 거래</Text>

        <View style={styles.pricePanel}>
          <View>
            <Text style={styles.panelLabel}>비트코인 가격</Text>
            <Text style={styles.price}>{formatMarketPrice(currentPrice)}</Text>
            <Text
              style={[
                styles.change,
                { color: change >= 0 ? colors.buyText : colors.sellText },
              ]}
            >
              앱 수신 구간 {change >= 0 ? '+' : ''}
              {change.toFixed(3)}%
            </Text>
          </View>
          <View style={styles.quoteStats}>
            <Quote label="최우선 매수" value={formatMarketPrice(bestBid)} />
            <Quote label="최우선 매도" value={formatMarketPrice(bestAsk)} />
            <Quote label="스프레드" value={formatMarketPrice(spread)} />
          </View>
        </View>

        <View style={styles.statusRow}>
          <View
            style={[styles.statusDot, connected && styles.connectedStatusDot]}
          />
          <Text style={styles.statusText}>
            {connected ? 'Spring 실시간 호가 연결됨' : 'Spring 호가 연결 중'}
          </Text>
          {!connected ? (
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

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>BTCUSDT 차트</Text>
          <Pressable
            accessibilityRole="button"
            onPress={() => router.push('/chart')}
            style={styles.detailLink}
          >
            <Text style={styles.detailText}>상세 차트</Text>
            <AppIcon name="chart" color={colors.accent} size={18} />
          </Pressable>
        </View>
        <PriceChart
          compact
          liveConnected={connected}
          liveKlines={market.liveKlines}
        />

        <View style={styles.orderBookSection}>
          <OrderBook
            compact
            depth={market.depth}
            statusText={connected ? '실시간' : '갱신 지연'}
          />
        </View>

        <View style={styles.actions}>
          <AppButton
            label="상세 차트 보기"
            onPress={() => router.push('/chart')}
            style={styles.flex}
            variant="choice"
          />
          <AppButton
            label="선물 거래하기"
            onPress={() => router.navigate('/trade')}
            style={styles.flex}
            variant="primary"
          />
        </View>
        <Text style={styles.source}>
          과거 캔들: Binance REST · 진행 봉과 호가: Spring SSE
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}

function Quote({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.quote}>
      <Text style={styles.quoteLabel}>{label}</Text>
      <Text style={styles.quoteValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.background },
  content: { paddingHorizontal: 18, paddingTop: 12, paddingBottom: 28 },
  brandRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 28,
  },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  brandMark: { padding: 5, borderRadius: 6, backgroundColor: colors.accent },
  brandText: {
    color: colors.textPrimary,
    fontSize: 13,
    fontWeight: '900',
    letterSpacing: 0.7,
  },
  brandLight: { color: colors.textMuted, fontWeight: '500' },
  login: {
    minHeight: 38,
    justifyContent: 'center',
    paddingHorizontal: 14,
    borderRadius: 6,
    backgroundColor: colors.surfaceMuted,
  },
  loginText: { color: colors.accent, fontSize: 12, fontWeight: '700' },
  title: { color: colors.textPrimary, fontSize: 30, fontWeight: '800' },
  subtitle: { marginTop: 6, color: colors.textMuted, fontSize: 12 },
  pricePanel: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 20,
    marginTop: 24,
    paddingVertical: 20,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: colors.divider,
  },
  panelLabel: { color: colors.textMuted, fontSize: 10 },
  price: {
    marginTop: 8,
    color: colors.textPrimary,
    fontSize: 29,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  change: { marginTop: 8, fontSize: 10 },
  quoteStats: { flex: 1, justifyContent: 'space-between', maxWidth: 155 },
  quote: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
  quoteLabel: { color: colors.textMuted, fontSize: 9 },
  quoteValue: {
    color: colors.textSecondary,
    fontSize: 10,
    fontVariant: ['tabular-nums'],
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 45,
    gap: 8,
  },
  statusDot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    backgroundColor: colors.textMuted,
  },
  connectedStatusDot: { backgroundColor: colors.buyText },
  statusText: { flex: 1, color: colors.textMuted, fontSize: 10 },
  retry: { minHeight: 38, justifyContent: 'center', paddingHorizontal: 8 },
  retryText: { color: colors.accentText, fontSize: 10 },
  error: { marginBottom: 8, color: colors.sellText, fontSize: 11 },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 12,
    paddingBottom: 4,
  },
  sectionTitle: { color: colors.textPrimary, fontSize: 16, fontWeight: '700' },
  detailLink: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 40,
    gap: 6,
  },
  detailText: { color: colors.accentText, fontSize: 10 },
  orderBookSection: {
    marginTop: 12,
    paddingTop: 14,
    borderTopWidth: 5,
    borderTopColor: colors.surface,
  },
  actions: { flexDirection: 'row', gap: 10, marginTop: 22 },
  flex: { flex: 1 },
  source: {
    marginTop: 18,
    color: colors.textMuted,
    fontSize: 9,
    textAlign: 'center',
  },
});
