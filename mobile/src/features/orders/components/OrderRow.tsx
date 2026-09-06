import { StyleSheet, Text, View } from 'react-native';

import { AppButton } from '@/components/AppButton';
import { colors } from '@/theme/colors';
import type { Order } from '@/types/paper';
import {
  formatPrice,
  formatQuantity,
  orderStatusLabel,
  sideLabel,
} from '@/utils/format';

type OrderRowProps = {
  order: Order;
  canceling: boolean;
  onCancel: (id: number) => void;
};

export function OrderRow({ order, canceling, onCancel }: OrderRowProps) {
  const canCancel = order.status === 'OPEN';

  return (
    <View style={styles.row}>
      <View style={styles.heading}>
        <Text
          style={[styles.side, order.side === 'BUY' ? styles.buy : styles.sell]}
        >
          {sideLabel(order.side)} ·{' '}
          {order.type === 'MARKET' ? '시장가' : '지정가'}
        </Text>
        <Text style={styles.status}>{orderStatusLabel(order.status)}</Text>
      </View>
      <Text style={styles.detail}>
        {order.symbol} · 주문 #{order.id}
      </Text>
      <Text style={styles.detail}>
        {formatQuantity(order.quantity)} BTC ·{' '}
        {order.type === 'MARKET'
          ? '시장가'
          : '주문가 ' + formatPrice(order.limitPrice)}
      </Text>
      <Text style={styles.detail}>
        체결 {formatQuantity(order.filledQuantity)} BTC · 평균{' '}
        {formatPrice(order.avgPrice)} USDT
      </Text>
      {canCancel ? (
        <View style={styles.action}>
          <AppButton
            disabled={canceling}
            label={canceling ? '취소 중...' : '대기 주문 취소'}
            onPress={() => onCancel(order.id)}
            variant="choice"
          />
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    padding: 15,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 14,
    backgroundColor: colors.surface,
  },
  heading: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  side: { fontSize: 14, fontWeight: '900' },
  buy: { color: colors.buyText },
  sell: { color: colors.sellText },
  status: { color: colors.textSecondary, fontSize: 11, fontWeight: '700' },
  detail: {
    marginTop: 7,
    color: colors.textSecondary,
    fontSize: 12,
    fontVariant: ['tabular-nums'],
  },
  action: { marginTop: 12 },
});
