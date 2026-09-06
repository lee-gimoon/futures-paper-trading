/**
 * 부모가 관리하는 주문 상태를 입력창·선택 버튼에 연결한다.
 * 수량 비율과 예상 증거금은 현재 잔고·레버리지·반대 포지션의 감소 수량을 반영한다.
 */
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { AppButton } from '@/components/AppButton';
import {
  estimateOrder,
  quantityForPercent,
} from '@/features/trade/orderEstimate';
import type { OrderFormController } from '@/features/trade/useOrderForm';
import { colors } from '@/theme/colors';
import type { CreateOrderInput, OrderSide, Portfolio } from '@/types/paper';
import { formatPrice } from '@/utils/format';

type OrderFormProps = {
  form: OrderFormController;
  side: OrderSide;
  onSideChange: (side: OrderSide) => void;
  portfolio: Portfolio | null;
  referencePrice: number | null;
  leverage: number;
  disabled: boolean;
  authenticated: boolean;
  onSignIn: () => void;
  onPrepare: (input: CreateOrderInput) => void;
};
export function OrderForm({
  form,
  side,
  onSideChange,
  portfolio,
  referencePrice,
  leverage,
  disabled,
  authenticated,
  onSignIn,
  onPrepare,
}: OrderFormProps) {
  const price =
    form.orderType === 'LIMIT'
      ? Number(form.limitPrice)
      : (referencePrice ?? 0);
  const estimate = estimateOrder(
    Number(form.quantity),
    price,
    leverage,
    side,
    portfolio,
  );
  const prepare = () => {
    if (!authenticated) {
      onSignIn();
      return;
    }
    const input = form.buildOrder(side);
    if (input) onPrepare(input);
  };
  return (
    <View style={styles.container}>
      <View style={styles.sideRow}>
        {(['BUY', 'SELL'] as const).map((value) => (
          <Pressable
            key={value}
            accessibilityRole="button"
            accessibilityState={{ selected: value === side }}
            onPress={() => onSideChange(value)}
            style={[
              styles.sideButton,
              side === value && {
                backgroundColor: value === 'BUY' ? colors.buy : colors.sell,
              },
            ]}
          >
            <Text
              style={[
                styles.sideText,
                side === value && { color: colors.buttonText },
              ]}
            >
              {value === 'BUY' ? '매수 / 롱' : '매도 / 숏'}
            </Text>
          </Pressable>
        ))}
      </View>
      <View style={styles.available}>
        <Text style={styles.caption}>주문 가능</Text>
        <Text style={styles.balance}>
          {formatPrice(portfolio?.availableBalance)}{' '}
          <Text style={styles.caption}>USDT</Text>
        </Text>
      </View>
      <View style={styles.typeRow}>
        {/* 두 버튼은 항상 함께 렌더링하며 orderType과 같은 버튼만 선택된 모양을 적용한다. */}
        {(['LIMIT', 'MARKET'] as const).map((type) => (
          <Pressable
            key={type}
            accessibilityRole="button"
            accessibilityState={{ selected: form.orderType === type }}
            onPress={() => form.setOrderType(type)}
            style={[
              styles.typeButton,
              form.orderType === type && styles.typeSelected,
            ]}
          >
            <Text
              style={[
                styles.typeText,
                form.orderType === type && { color: colors.textPrimary },
              ]}
            >
              {type === 'LIMIT' ? '지정가' : '시장가'}
            </Text>
          </Pressable>
        ))}
      </View>
      <View style={styles.inputShell}>
        {form.orderType === 'LIMIT' ? (
          <TextInput
            accessibilityLabel="지정가 주문 가격"
            keyboardType="decimal-pad"
            placeholder="가격"
            placeholderTextColor={colors.textMuted}
            value={form.limitPrice}
            onChangeText={form.setLimitPrice}
            style={styles.input}
          />
        ) : (
          <Text style={styles.marketPrice}>최적 시장가</Text>
        )}
        <Text style={styles.inputUnit}>USDT</Text>
      </View>
      <View style={styles.inputShell}>
        <TextInput
          accessibilityLabel="주문 수량 BTC"
          keyboardType="decimal-pad"
          placeholder="수량"
          placeholderTextColor={colors.textMuted}
          value={form.quantity}
          onChangeText={form.setQuantity}
          style={styles.input}
        />
        <Text style={styles.inputUnit}>BTC</Text>
      </View>
      <View style={styles.percentRow}>
        {[25, 50, 75, 100].map((value) => (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={'잔고 ' + value + '% 수량'}
            disabled={!portfolio || price <= 0 || disabled}
            onPress={() =>
              form.setQuantity(
                quantityForPercent(value, price, side, portfolio),
              )
            }
            key={value}
            style={({ pressed }) => [
              styles.percent,
              (!portfolio || price <= 0 || disabled) && { opacity: 0.4 },
              pressed && { backgroundColor: colors.borderStrong },
            ]}
          >
            <View style={styles.tick} />
            <Text style={styles.percentText}>{value}%</Text>
          </Pressable>
        ))}
      </View>
      <View style={styles.preview}>
        <Text style={styles.caption}>주문 금액</Text>
        <Text style={styles.previewValue}>
          {formatPrice(estimate.notional)} USDT
        </Text>
      </View>
      <View style={styles.preview}>
        <Text style={styles.caption}>예상 증거금</Text>
        <Text style={styles.previewValue}>
          {formatPrice(estimate.margin)} USDT
        </Text>
      </View>
      {estimate.closingQuantity > 0 ? (
        <Text style={styles.helper}>
          반대 포지션 {estimate.closingQuantity.toFixed(6)} BTC부터 감소합니다.
        </Text>
      ) : null}
      <AppButton
        label={
          authenticated
            ? side === 'BUY'
              ? '매수 / 롱'
              : '매도 / 숏'
            : '로그인 후 주문'
        }
        variant={side === 'BUY' ? 'buy' : 'sell'}
        disabled={disabled}
        onPress={prepare}
        style={{ marginTop: 8, paddingHorizontal: 5 }}
      />
      {form.error ? (
        <Text accessibilityLiveRegion="polite" style={styles.error}>
          {form.error}
        </Text>
      ) : null}
      <Text style={styles.helper}>모의 주문 · 실제 자산 사용 없음</Text>
    </View>
  );
}
const styles = StyleSheet.create({
  container: { gap: 10 },
  sideRow: {
    flexDirection: 'row',
    borderRadius: 6,
    backgroundColor: colors.surfaceMuted,
    padding: 2,
  },
  sideButton: {
    flex: 1,
    minHeight: 34,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 5,
  },
  sideText: { color: colors.textMuted, fontSize: 11, fontWeight: '700' },
  available: { gap: 5, paddingVertical: 3 },
  caption: { color: colors.textMuted, fontSize: 9 },
  balance: {
    color: colors.textPrimary,
    fontSize: 12,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  typeRow: {
    flexDirection: 'row',
    backgroundColor: colors.surface,
    borderRadius: 5,
    padding: 2,
  },
  typeButton: {
    flex: 1,
    minHeight: 34,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 4,
  },
  typeSelected: { backgroundColor: colors.borderStrong },
  typeText: { color: colors.textMuted, fontSize: 11, fontWeight: '600' },
  inputShell: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.input,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: 10,
    minHeight: 46,
    gap: 4,
  },
  input: {
    flex: 1,
    minWidth: 0,
    color: colors.textPrimary,
    fontSize: 13,
    paddingVertical: 12,
  },
  inputUnit: { color: colors.textSecondary, fontSize: 10 },
  marketPrice: { flex: 1, color: colors.textMuted, fontSize: 12 },
  percentRow: { flexDirection: 'row', justifyContent: 'space-between' },
  percent: {
    flex: 1,
    minHeight: 40,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  tick: {
    height: 4,
    width: '90%',
    backgroundColor: colors.borderStrong,
    borderRadius: 2,
  },
  percentText: { color: colors.textMuted, fontSize: 9 },
  preview: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    flexWrap: 'wrap',
    gap: 3,
  },
  previewValue: {
    color: colors.textSecondary,
    fontSize: 10,
    fontVariant: ['tabular-nums'],
  },
  helper: { color: colors.textMuted, fontSize: 9, lineHeight: 15 },
  error: { color: colors.sellText, fontSize: 11, lineHeight: 17 },
});
