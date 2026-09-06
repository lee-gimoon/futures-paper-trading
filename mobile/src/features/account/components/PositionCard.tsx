import { StyleSheet, Text, View } from 'react-native';

import { AppButton } from '@/components/AppButton';
import { colors } from '@/theme/colors';
import type { Position } from '@/types/paper';
import {
  formatPnl,
  formatPrice,
  formatQuantity,
  sideLabel,
} from '@/utils/format';

type PositionCardProps = {
  position: Position;
  closing: boolean;
  onClose: () => void;
};

export function PositionCard({
  position,
  closing,
  onClose,
}: PositionCardProps) {
  const positivePnl = position.unrealizedPnl >= 0;

  return (
    <View style={styles.card}>
      <View style={styles.titleRow}>
        <View>
          <Text style={styles.label}>현재 포지션</Text>
          <Text style={styles.symbol}>{position.symbol}</Text>
        </View>
        <View
          style={[
            styles.sideBadge,
            position.side === 'LONG' ? styles.longBadge : styles.shortBadge,
          ]}
        >
          <Text
            style={
              position.side === 'LONG' ? styles.longText : styles.shortText
            }
          >
            {sideLabel(position.side)} {position.leverage}x
          </Text>
        </View>
      </View>

      <View style={styles.grid}>
        <Value
          label="수량"
          value={`${formatQuantity(position.quantity)} BTC`}
        />
        <Value
          label="진입가"
          value={`${formatPrice(position.averageEntryPrice)} USDT`}
        />
        <Value
          label="평가 가격"
          value={`${formatPrice(position.markPrice)} USDT`}
        />
        <Value
          label="추정 청산가"
          value={`${formatPrice(position.liquidationPrice)} USDT`}
        />
      </View>

      <View style={styles.pnlRow}>
        <Text style={styles.label}>미실현 손익</Text>
        <Text
          style={[styles.pnl, positivePnl ? styles.positive : styles.negative]}
        >
          {formatPnl(position.unrealizedPnl)} USDT
        </Text>
      </View>

      <AppButton
        disabled={closing}
        label={closing ? '포지션 종료 중...' : '시장가로 포지션 종료'}
        onPress={onClose}
        variant={position.side === 'LONG' ? 'sell' : 'buy'}
      />
    </View>
  );
}

function Value({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.valueCell}>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.value}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    marginTop: 14,
    padding: 18,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 16,
    backgroundColor: colors.surface,
    gap: 16,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  label: { color: colors.textMuted, fontSize: 11, fontWeight: '700' },
  symbol: {
    marginTop: 4,
    color: colors.textPrimary,
    fontSize: 18,
    fontWeight: '900',
  },
  sideBadge: { paddingHorizontal: 10, paddingVertical: 7, borderRadius: 999 },
  longBadge: { backgroundColor: '#0d382c' },
  shortBadge: { backgroundColor: '#471d2a' },
  longText: { color: colors.buyText, fontSize: 11, fontWeight: '800' },
  shortText: { color: colors.sellText, fontSize: 11, fontWeight: '800' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', rowGap: 14 },
  valueCell: { width: '50%', paddingRight: 8 },
  value: {
    marginTop: 5,
    color: colors.textPrimary,
    fontSize: 13,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  pnlRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  pnl: { fontSize: 15, fontWeight: '900', fontVariant: ['tabular-nums'] },
  positive: { color: colors.buyText },
  negative: { color: colors.sellText },
});
