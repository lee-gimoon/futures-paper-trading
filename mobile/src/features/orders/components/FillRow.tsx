import { StyleSheet, Text, View } from 'react-native';

import { colors } from '@/theme/colors';
import type { Fill } from '@/types/paper';
import { formatPrice, formatQuantity, sideLabel } from '@/utils/format';

export function FillRow({ fill }: { fill: Fill }) {
  return (
    <View style={styles.row}>
      <View style={styles.heading}>
        <Text
          style={[styles.side, fill.side === 'BUY' ? styles.buy : styles.sell]}
        >
          {sideLabel(fill.side)} 체결
        </Text>
        <Text style={styles.orderId}>주문 #{fill.orderId}</Text>
      </View>
      <Text style={styles.detail}>
        {formatQuantity(fill.quantity)} BTC · {formatPrice(fill.price)} USDT
      </Text>
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
  orderId: { color: colors.textMuted, fontSize: 10, fontWeight: '700' },
  detail: {
    marginTop: 7,
    color: colors.textSecondary,
    fontSize: 12,
    fontVariant: ['tabular-nums'],
  },
});
