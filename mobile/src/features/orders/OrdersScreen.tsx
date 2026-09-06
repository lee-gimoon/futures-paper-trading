/** 미체결·주문 이력·체결 내역을 선택해서 조회하고 취소 결과를 갱신한다. */
import { useState } from 'react';
import {
  FlatList,
  Modal,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { AppButton } from '@/components/AppButton';
import { AppIcon } from '@/components/AppIcon';
import { SignInNotice } from '@/components/SignInNotice';
import { useAuth } from '@/features/auth/useAuth';
import { FillRow } from '@/features/orders/components/FillRow';
import { OrderRow } from '@/features/orders/components/OrderRow';
import { usePaperOrders } from '@/features/orders/usePaperOrders';
import { colors } from '@/theme/colors';
import type { Fill, Order } from '@/types/paper';

type HistoryItem =
  | { id: number; fill: Fill; order: null }
  | { id: number; order: Order; fill: null };

export function OrdersScreen() {
  const auth = useAuth();
  return <OrdersContent key={auth.status + ':' + auth.user?.id} />;
}

function OrdersContent() {
  const authenticated = useAuth().status === 'authenticated';
  const state = usePaperOrders(authenticated);
  const [tab, setTab] = useState<'open' | 'orders' | 'fills'>('open');
  const [cancelId, setCancelId] = useState<number | null>(null);
  const items =
    tab === 'fills'
      ? state.fills.map((fill) => ({ id: fill.id, fill, order: null }))
      : state.orders
          .filter(
            (order) =>
              tab !== 'open' ||
              order.status === 'OPEN' ||
              order.status === 'NEW',
          )
          .map((order) => ({ id: order.id, order, fill: null }));
  return (
    <SafeAreaView edges={['top', 'left', 'right']} style={styles.safeArea}>
      <View style={styles.header}>
        <Text style={styles.title}>주문</Text>
        <Text style={styles.caption}>USDⓈ-M · 모의 거래</Text>
      </View>
      <View style={styles.tabs}>
        {(
          [
            { value: 'open', label: '미체결' },
            { value: 'orders', label: '주문 이력' },
            { value: 'fills', label: '체결 내역' },
          ] as const
        ).map((item) => (
          <Pressable
            key={item.value}
            accessibilityRole="button"
            accessibilityState={{ selected: tab === item.value }}
            onPress={() => setTab(item.value)}
            style={[styles.tab, tab === item.value && styles.selected]}
          >
            <Text
              style={[
                styles.tabText,
                tab === item.value && { color: colors.textPrimary },
              ]}
            >
              {item.label}
            </Text>
          </Pressable>
        ))}
      </View>
      {!authenticated ? (
        <SignInNotice
          message="로그인하면 미체결 주문과 거래 내역을 확인할 수 있습니다."
          returnTo="/orders"
        />
      ) : (
        <FlatList<HistoryItem>
          // 선택한 내역의 전체 배열을 전달하면 FlatList가 각 항목을 renderItem의 item 인자로 제공한다.
          data={items}
          keyExtractor={(item) => tab + item.id}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl
              refreshing={state.refreshing}
              onRefresh={() => void state.refresh()}
              tintColor={colors.accent}
            />
          }
          ListHeaderComponent={
            <>
              <Text style={styles.filter}>BTCUSDT · {items.length}건</Text>
              {state.error ? (
                <Pressable
                  accessibilityRole="button"
                  onPress={() => void state.refresh()}
                >
                  <Text style={styles.error}>{state.error} · 다시 조회</Text>
                </Pressable>
              ) : null}
            </>
          }
          ListEmptyComponent={
            <View style={styles.empty}>
              <AppIcon name="orders" size={36} />
              <Text style={styles.emptyTitle}>
                {state.loading
                  ? '내역을 불러오는 중입니다.'
                  : state.error
                    ? '내역을 확인하지 못했습니다.'
                    : tab === 'open'
                      ? '미체결 주문이 없습니다.'
                      : '거래 내역이 없습니다.'}
              </Text>
              <Text style={styles.caption}>
                {!state.loading && !state.error
                  ? '주문과 체결 결과가 이곳에 기록됩니다.'
                  : ''}
              </Text>
            </View>
          }
          ItemSeparatorComponent={() => <View style={{ height: 10 }} />}
          renderItem={({ item }) =>
            item.order ? (
              <OrderRow
                order={item.order}
                canceling={state.cancelingId === item.order.id}
                onCancel={setCancelId}
              />
            ) : item.fill ? (
              <FillRow fill={item.fill} />
            ) : null
          }
        />
      )}
      <Modal
        transparent
        visible={authenticated && cancelId !== null}
        animationType="fade"
        onRequestClose={() => {
          if (state.cancelingId === null) setCancelId(null);
        }}
      >
        <View style={styles.backdrop}>
          <View style={styles.dialog}>
            <Text style={styles.dialogTitle}>주문 취소</Text>
            <Text style={styles.caption}>
              주문 #{cancelId}의 미체결 수량을 취소할까요?
            </Text>
            {state.error ? (
              <Text style={styles.error}>{state.error}</Text>
            ) : null}
            <View style={styles.actions}>
              <AppButton
                label="돌아가기"
                disabled={state.cancelingId !== null}
                onPress={() => setCancelId(null)}
                style={styles.flex}
              />
              <AppButton
                label={state.cancelingId !== null ? '취소 중...' : '취소 확인'}
                disabled={state.cancelingId !== null}
                variant="primary"
                onPress={() => {
                  if (cancelId !== null)
                    void state.cancel(cancelId).then((success) => {
                      if (success) setCancelId(null);
                    });
                }}
                style={styles.flex}
              />
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.background },
  header: { padding: 20, gap: 8 },
  title: { color: colors.textPrimary, fontSize: 28, fontWeight: '800' },
  caption: { color: colors.textMuted, fontSize: 11, lineHeight: 18 },
  tabs: {
    flexDirection: 'row',
    paddingHorizontal: 20,
    gap: 26,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  tab: {
    paddingVertical: 15,
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  selected: { borderBottomColor: colors.accent },
  tabText: { color: colors.textMuted, fontSize: 13, fontWeight: '600' },
  list: { padding: 16, flexGrow: 1 },
  filter: { color: colors.textMuted, fontSize: 11, marginBottom: 15 },
  empty: { paddingTop: 80, alignItems: 'center', gap: 15 },
  emptyTitle: { color: colors.textSecondary, fontSize: 13 },
  error: {
    color: colors.sellText,
    fontSize: 12,
    lineHeight: 18,
    marginBottom: 12,
  },
  backdrop: {
    flex: 1,
    justifyContent: 'center',
    padding: 24,
    backgroundColor: '#000000aa',
  },
  dialog: {
    padding: 22,
    borderRadius: 12,
    backgroundColor: colors.surface,
    gap: 12,
  },
  dialogTitle: { color: colors.textPrimary, fontSize: 20, fontWeight: '700' },
  actions: { flexDirection: 'row', gap: 10, marginTop: 10 },
  flex: { flex: 1 },
});
