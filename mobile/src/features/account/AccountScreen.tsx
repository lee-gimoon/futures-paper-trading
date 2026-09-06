/** 자산 탭은 평가 자산·가용 잔고·증거금·손익을 요약하고 거래 화면과 연결한다. */
import { useState } from 'react';
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
import { SignInNotice } from '@/components/SignInNotice';
import { usePaperAccount } from '@/features/account/usePaperAccount';
import { useAuth } from '@/features/auth/useAuth';
import { colors } from '@/theme/colors';
import { formatPnl, formatPrice } from '@/utils/format';

export function AccountScreen() {
  const auth = useAuth();
  return <AccountContent key={auth.status + ':' + auth.user?.id} />;
}

function AccountContent() {
  const { user, status, logout } = useAuth();
  const authenticated = status === 'authenticated';
  const account = usePaperAccount(authenticated);
  const [loggingOut, setLoggingOut] = useState(false);
  const [actionError, setActionError] = useState('');
  const [hidden, setHidden] = useState(false);
  const portfolio = authenticated ? account.portfolio : null;
  const amount = (value: number | null | undefined) =>
    hidden ? '••••••' : formatPrice(value);
  const submitLogout = async () => {
    setLoggingOut(true);
    setActionError('');
    try {
      await logout();
    } catch (caught) {
      setActionError(
        caught instanceof Error ? caught.message : '로그아웃하지 못했습니다.',
      );
    } finally {
      setLoggingOut(false);
    }
  };
  return (
    <SafeAreaView edges={['top', 'left', 'right']} style={styles.safeArea}>
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={
          authenticated ? (
            <RefreshControl
              refreshing={account.refreshing}
              onRefresh={() => void account.refresh()}
              tintColor={colors.accent}
            />
          ) : undefined
        }
      >
        <View style={styles.header}>
          <Text style={styles.title}>자산</Text>
          <Text style={styles.badge}>모의 계좌</Text>
        </View>
        <Text style={styles.user}>
          {authenticated ? user?.displayName || user?.email : '나의 선물 계좌'}
        </Text>
        <View style={styles.balance}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={hidden ? '잔고 표시' : '잔고 숨기기'}
            onPress={() => setHidden((value) => !value)}
            style={styles.balanceLabel}
          >
            <Text style={styles.label}>총 평가 자산 (USDT)</Text>
            <Text style={styles.hide}>{hidden ? '표시' : '숨기기'}</Text>
          </Pressable>
          <Text style={styles.total}>{amount(portfolio?.equity)}</Text>
          <Text style={styles.pnl}>
            미실현 손익{' '}
            <Text
              style={{
                color:
                  (portfolio?.unrealizedPnl ?? 0) >= 0
                    ? colors.buyText
                    : colors.sellText,
              }}
            >
              {hidden ? '••••' : formatPnl(portfolio?.unrealizedPnl)} USDT
            </Text>
          </Text>
          <View style={styles.grid}>
            <Summary
              label="주문 가능"
              value={amount(portfolio?.availableBalance)}
            />
            <Summary
              label="사용 증거금"
              value={amount(portfolio?.usedMargin)}
            />
            <Summary label="현금 잔고" value={amount(portfolio?.cashBalance)} />
            <Summary
              label="실현 손익"
              value={hidden ? '••••' : formatPnl(portfolio?.realizedPnl)}
            />
          </View>
        </View>
        {!authenticated ? (
          <SignInNotice
            message="로그인하고 나의 모의 자산과 거래 성과를 확인하세요."
            returnTo="/account"
          />
        ) : null}
        {account.loading && authenticated ? (
          <Text style={styles.info}>계좌 조회 중...</Text>
        ) : null}
        {authenticated && account.error ? (
          <Pressable
            accessibilityRole="button"
            onPress={() => void account.refresh()}
          >
            <Text style={styles.error}>{account.error} · 다시 조회</Text>
          </Pressable>
        ) : null}
        <AppButton
          label="선물 거래하기"
          variant="primary"
          onPress={() => router.navigate('/trade')}
        />
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>자산 구성</Text>
          <View style={styles.assetRow}>
            <View style={styles.usdt}>
              <Text style={styles.usdtText}>₮</Text>
            </View>
            <View style={styles.assetName}>
              <Text style={styles.assetTitle}>USDT</Text>
              <Text style={styles.label}>모의 선물 계좌</Text>
            </View>
            <View style={styles.assetAmount}>
              <Text style={styles.assetTitle}>{amount(portfolio?.equity)}</Text>
              <Text style={styles.label}>USDT</Text>
            </View>
          </View>
        </View>
        <Pressable
          accessibilityRole="button"
          onPress={() => router.navigate('/trade')}
          style={styles.link}
        >
          <View>
            <Text style={styles.linkTitle}>포지션 관리</Text>
            <Text style={styles.label}>
              {portfolio?.position
                ? portfolio.position.symbol +
                  ' · ' +
                  (portfolio.position.side === 'LONG' ? '롱' : '숏') +
                  ' · ' +
                  portfolio.position.leverage +
                  'x'
                : '선물 화면에서 포지션과 증거금을 확인하세요.'}
            </Text>
          </View>
          <AppIcon name="trade" />
        </Pressable>
        <Pressable
          accessibilityRole="button"
          onPress={() => router.navigate('/orders')}
          style={styles.link}
        >
          <Text style={styles.linkTitle}>주문 및 체결 내역</Text>
          <AppIcon name="orders" />
        </Pressable>
        <Text style={styles.info}>
          이 계좌의 잔고는 모의 자산이며 입출금되지 않습니다.
        </Text>
        {actionError ? <Text style={styles.error}>{actionError}</Text> : null}
        {authenticated ? (
          <AppButton
            label={loggingOut ? '로그아웃 중...' : '로그아웃'}
            disabled={loggingOut}
            onPress={() => void submitLogout()}
          />
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}
function Summary({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.gridCell}>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.gridValue}>{value}</Text>
    </View>
  );
}
const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.background },
  content: { padding: 20, paddingBottom: 30 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: { color: colors.textPrimary, fontSize: 28, fontWeight: '800' },
  badge: {
    color: colors.accentText,
    backgroundColor: colors.accentMuted,
    padding: 7,
    borderRadius: 5,
    fontSize: 10,
  },
  user: { color: colors.textMuted, marginTop: 8, fontSize: 12 },
  balance: { paddingVertical: 25 },
  balanceLabel: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    minHeight: 32,
  },
  label: { color: colors.textMuted, fontSize: 11, lineHeight: 18 },
  hide: { color: colors.textSecondary, fontSize: 10 },
  total: {
    color: colors.textPrimary,
    fontSize: 34,
    fontWeight: '700',
    marginTop: 8,
    fontVariant: ['tabular-nums'],
  },
  pnl: { color: colors.textMuted, fontSize: 11, marginTop: 13 },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginTop: 22,
    rowGap: 18,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    paddingTop: 18,
  },
  gridCell: { width: '50%' },
  gridValue: {
    color: colors.textPrimary,
    fontSize: 15,
    marginTop: 6,
    fontVariant: ['tabular-nums'],
  },
  section: { marginTop: 30 },
  sectionTitle: { color: colors.textPrimary, fontSize: 16, fontWeight: '700' },
  assetRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 22,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  usdt: {
    backgroundColor: colors.buyMuted,
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
  },
  usdtText: { color: colors.buyText, fontSize: 25, fontWeight: '700' },
  assetName: { flex: 1, gap: 4 },
  assetTitle: { color: colors.textPrimary, fontSize: 15, fontWeight: '700' },
  assetAmount: { alignItems: 'flex-end', gap: 4 },
  link: {
    paddingVertical: 20,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
  },
  linkTitle: { color: colors.textPrimary, fontSize: 13, marginBottom: 3 },
  info: {
    color: colors.textMuted,
    fontSize: 11,
    lineHeight: 18,
    marginVertical: 22,
  },
  error: {
    color: colors.sellText,
    fontSize: 12,
    lineHeight: 19,
    marginBottom: 15,
  },
});
