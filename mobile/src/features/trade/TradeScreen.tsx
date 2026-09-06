/**
 * BTCUSDT 주문 화면 전체를 조합하고 화면 상태를 관리한다.
 * 과거 캔들은 Binance REST로 한 번 받고 진행 봉·호가·계좌·주문은 Spring API를 사용한다.
 * 작은 컴포넌트는 전달받은 값을 표시하고, 이 화면이 주문 방향과 확인 창 상태를 연결한다.
 */
import { useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppIcon } from '@/components/AppIcon';
import { OrderBook } from '@/components/OrderBook';
import { SignInNotice } from '@/components/SignInNotice';
import { PositionCard } from '@/features/account/components/PositionCard';
import { usePaperAccount } from '@/features/account/usePaperAccount';
import { useAuth } from '@/features/auth/useAuth';
import { PriceChart } from '@/features/market/chart/PriceChart';
import { useDepthStream } from '@/features/market/useDepthStream';
import { OrderRow } from '@/features/orders/components/OrderRow';
import { usePaperOrders } from '@/features/orders/usePaperOrders';
import { LeverageSheet } from '@/features/trade/components/LeverageSheet';
import { OrderConfirmModal } from '@/features/trade/components/OrderConfirmModal';
import { OrderForm } from '@/features/trade/components/OrderForm';
import { useOrderForm } from '@/features/trade/useOrderForm';
import { useSubmitOrder } from '@/features/trade/useSubmitOrder';
import { colors } from '@/theme/colors';
import type { CreateOrderInput, OrderSide } from '@/types/paper';
import { formatMarketPrice } from '@/utils/format';

export function TradeScreen() {
  const params = useLocalSearchParams<{ side?: string; intent?: string }>();
  const { status } = useAuth();
  const authenticated = status === 'authenticated';
  const market = useDepthStream();
  const account = usePaperAccount(authenticated);
  const orders = usePaperOrders(authenticated);
  const form = useOrderForm();
  const submission = useSubmitOrder();
  const initialSide: OrderSide = params.side === 'SELL' ? 'SELL' : 'BUY';
  const [sideSelection, setSideSelection] = useState({
    side: initialSide,
    routeIntent: params.intent,
  });
  const [pendingOrder, setPendingOrder] = useState<CreateOrderInput | null>(
    null,
  );
  const [leverageOpen, setLeverageOpen] = useState(false);
  const [previewLeverage, setPreviewLeverage] = useState(10);
  const [message, setMessage] = useState<string | null>(null);

  const referencePrice = market.depth?.midPrice ?? null;
  const leverage = account.portfolio?.leverage ?? previewLeverage;
  const connected = market.status === 'connected';
  const requestedSide =
    params.side === 'BUY' || params.side === 'SELL' ? params.side : null;
  const side =
    requestedSide && sideSelection.routeIntent !== params.intent
      ? requestedSide
      : sideSelection.side;

  const confirmOrder = async () => {
    if (!pendingOrder) return;
    const created = await submission.submit(pendingOrder);
    if (!created) return;

    setPendingOrder(null);
    form.clearAfterSubmit();
    setMessage(`주문 #${created.id}이 Spring 서버에 접수되었습니다.`);
    await Promise.all([account.refresh(false), orders.refresh(false)]);
  };

  return (
    <SafeAreaView edges={['top', 'left', 'right']} style={styles.safeArea}>
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardDismissMode="on-drag"
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.header}>
          <View>
            <Text style={styles.symbol}>BTCUSDT</Text>
            <Text style={styles.caption}>USDⓈ-M 무기한 · 모의 선물</Text>
          </View>
          <View style={styles.headerActions}>
            <Pressable
              accessibilityRole="button"
              onPress={() => setLeverageOpen(true)}
              style={styles.headerButton}
            >
              <Text style={styles.headerButtonText}>격리 {leverage}x</Text>
            </Pressable>
            <Pressable
              accessibilityLabel="상세 차트"
              accessibilityRole="button"
              onPress={() => router.push('/chart')}
              style={styles.iconButton}
            >
              <AppIcon name="chart" color={colors.accentText} size={20} />
            </Pressable>
          </View>
        </View>

        <View style={styles.priceStrip}>
          <View>
            <Text style={styles.caption}>비트코인 가격</Text>
            <Text style={styles.price}>
              {formatMarketPrice(referencePrice)}
            </Text>
          </View>
          <View style={styles.connection}>
            <View style={[styles.dot, connected && styles.connectedDot]} />
            <Text style={styles.connectionText}>
              {connected ? '호가 실시간' : '호가 연결 중'}
            </Text>
          </View>
        </View>
        {market.error ? (
          <Pressable accessibilityRole="button" onPress={market.reconnect}>
            <Text style={styles.error}>{market.error} · 다시 연결</Text>
          </Pressable>
        ) : null}

        <PriceChart
          compact
          liveConnected={connected}
          liveKlines={market.liveKlines}
        />

        <View style={styles.tradingArea}>
          <View style={styles.orderColumn}>
            <OrderForm
              authenticated={authenticated}
              disabled={
                submission.submitting ||
                (authenticated && (!connected || account.loading))
              }
              form={form}
              leverage={leverage}
              onPrepare={(input) => {
                submission.clearError();
                setMessage(null);
                setPendingOrder(input);
              }}
              onSideChange={(value) =>
                setSideSelection({ side: value, routeIntent: params.intent })
              }
              onSignIn={() =>
                router.push({
                  pathname: '/login',
                  params: { returnTo: '/trade' },
                })
              }
              portfolio={account.portfolio}
              referencePrice={referencePrice}
              side={side}
            />
          </View>
          <View style={styles.bookColumn}>
            <OrderBook
              compact
              depth={market.depth}
              onSelectPrice={form.selectLimitPrice}
              statusText={connected ? 'Spring 실시간' : '갱신 지연'}
            />
          </View>
        </View>

        {message ? (
          <Text accessibilityLiveRegion="polite" style={styles.success}>
            {message}
          </Text>
        ) : null}

        <View style={styles.personalSection}>
          <View style={styles.sectionHeading}>
            <Text style={styles.sectionTitle}>내 포지션과 주문</Text>
            {authenticated ? (
              <Pressable
                accessibilityRole="button"
                onPress={() => router.navigate('/orders')}
                style={styles.allOrders}
              >
                <Text style={styles.allOrdersText}>전체 주문 보기</Text>
              </Pressable>
            ) : null}
          </View>

          {!authenticated ? (
            <SignInNotice
              message="로그인하면 Spring 서버의 내 계좌로 주문하고 포지션을 확인할 수 있습니다."
              returnTo="/trade"
            />
          ) : (
            <>
              {account.error ? (
                <Text style={styles.error}>{account.error}</Text>
              ) : null}
              {account.portfolio?.position ? (
                <PositionCard
                  closing={account.closingPosition}
                  onClose={() => void account.closePosition()}
                  position={account.portfolio.position}
                />
              ) : (
                <Text style={styles.empty}>현재 열린 포지션이 없습니다.</Text>
              )}
              {orders.orders.slice(0, 2).map((order) => (
                <View key={order.id} style={styles.orderRow}>
                  <OrderRow
                    canceling={orders.cancelingId === order.id}
                    onCancel={(id) => void orders.cancel(id)}
                    order={order}
                  />
                </View>
              ))}
            </>
          )}
        </View>
      </ScrollView>

      <LeverageSheet
        error={account.error}
        leverage={leverage}
        onClose={() => setLeverageOpen(false)}
        onSave={async (value) => {
          if (!authenticated) {
            setPreviewLeverage(value);
            return true;
          }
          return account.changeLeverage(value);
        }}
        preview={!authenticated}
        saving={account.changingLeverage}
        visible={leverageOpen}
      />
      <OrderConfirmModal
        error={submission.error}
        input={pendingOrder}
        leverage={leverage}
        onCancel={() => {
          if (!submission.submitting) {
            submission.clearError();
            setPendingOrder(null);
          }
        }}
        onConfirm={() => void confirmOrder()}
        referencePrice={referencePrice}
        submitting={submission.submitting}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.background },
  content: { paddingHorizontal: 14, paddingTop: 10, paddingBottom: 34 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  symbol: { color: colors.textPrimary, fontSize: 20, fontWeight: '900' },
  caption: { marginTop: 4, color: colors.textMuted, fontSize: 9 },
  headerActions: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  headerButton: {
    minHeight: 38,
    justifyContent: 'center',
    paddingHorizontal: 12,
    borderRadius: 5,
    backgroundColor: colors.surfaceMuted,
  },
  headerButtonText: {
    color: colors.accentText,
    fontSize: 11,
    fontWeight: '700',
  },
  iconButton: {
    minWidth: 40,
    minHeight: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  priceStrip: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 18,
    paddingVertical: 15,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: colors.divider,
  },
  price: {
    marginTop: 5,
    color: colors.textPrimary,
    fontSize: 24,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  connection: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  dot: {
    width: 7,
    height: 7,
    borderRadius: 4,
    backgroundColor: colors.textMuted,
  },
  connectedDot: { backgroundColor: colors.buyText },
  connectionText: { color: colors.textMuted, fontSize: 9 },
  error: {
    marginVertical: 8,
    color: colors.sellText,
    fontSize: 11,
    lineHeight: 17,
  },
  tradingArea: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
    marginTop: 8,
    paddingTop: 16,
    borderTopWidth: 5,
    borderTopColor: colors.surface,
  },
  orderColumn: { flex: 1.06, minWidth: 0 },
  bookColumn: { flex: 0.94, minWidth: 0 },
  success: {
    marginTop: 16,
    padding: 12,
    borderWidth: 1,
    borderColor: colors.accentBorder,
    borderRadius: 6,
    backgroundColor: colors.accentMuted,
    color: colors.accentText,
    fontSize: 11,
    lineHeight: 17,
    textAlign: 'center',
  },
  personalSection: {
    marginTop: 24,
    paddingTop: 20,
    borderTopWidth: 5,
    borderTopColor: colors.surface,
  },
  sectionHeading: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  sectionTitle: { color: colors.textPrimary, fontSize: 15, fontWeight: '800' },
  allOrders: { minHeight: 38, justifyContent: 'center', paddingHorizontal: 6 },
  allOrdersText: { color: colors.accentText, fontSize: 10 },
  empty: {
    paddingVertical: 28,
    color: colors.textMuted,
    fontSize: 11,
    textAlign: 'center',
  },
  orderRow: { marginTop: 10 },
});
