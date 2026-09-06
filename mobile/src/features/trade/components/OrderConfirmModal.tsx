/** 서버로 주문을 보내기 전에 사용자가 종류, 방향, 가격과 수량을 다시 확인하는 창이다. */

import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';

import { AppButton } from '@/components/AppButton';
import { colors } from '@/theme/colors';
import type { CreateOrderInput } from '@/types/paper';
import { formatPrice, formatQuantity, sideLabel } from '@/utils/format';

type OrderConfirmModalProps = {
  input: CreateOrderInput | null;
  leverage?: number;
  referencePrice?: number | null;
  submitting: boolean;
  error: string | null;
  onCancel: () => void;
  onConfirm: () => void;
};

export function OrderConfirmModal({
  input,
  leverage,
  referencePrice,
  submitting,
  error,
  onCancel,
  onConfirm,
}: OrderConfirmModalProps) {
  return (
    <Modal
      animationType="fade"
      onRequestClose={onCancel}
      transparent
      visible={input !== null}
    >
      <Pressable
        disabled={submitting}
        onPress={onCancel}
        style={styles.backdrop}
      >
        <Pressable onPress={() => undefined} style={styles.dialog}>
          <Text style={styles.eyebrow}>BTCUSDT · 모의 주문</Text>
          <Text style={styles.title}>
            {input?.side === 'BUY' ? '매수 / 롱' : '매도 / 숏'} 주문 확인
          </Text>
          {input ? (
            <View style={styles.details}>
              <Detail
                label="마진 / 레버리지"
                value={'격리 · ' + (leverage ?? '—') + 'x'}
              />
              <Detail label="방향" value={sideLabel(input.side)} />
              <Detail
                label="방식"
                value={input.type === 'MARKET' ? '시장가' : '지정가'}
              />
              <Detail
                label="수량"
                value={`${formatQuantity(input.quantity)} BTC`}
              />
              {input.type === 'LIMIT' ? (
                <Detail
                  label="가격"
                  value={`${formatPrice(input.limitPrice)} USDT`}
                />
              ) : (
                <Detail
                  label="참고 호가"
                  value={`${formatPrice(referencePrice)} USDT`}
                />
              )}
            </View>
          ) : null}

          <Text style={styles.notice}>
            모의 계좌에서 처리됩니다. 시장가의 실제 체결 가격은 서버 호가와 체결
            수량에 따라 달라질 수 있습니다.
          </Text>
          {error ? <Text style={styles.error}>{error}</Text> : null}

          <View style={styles.actions}>
            <AppButton
              disabled={submitting}
              label="돌아가기"
              onPress={onCancel}
              style={styles.flex}
              variant="choice"
            />
            <AppButton
              disabled={submitting}
              label={submitting ? '전송 중...' : '주문 전송'}
              onPress={onConfirm}
              style={styles.flex}
              variant={input?.side === 'BUY' ? 'buy' : 'sell'}
            />
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.detailRow}>
      <Text style={styles.detailLabel}>{label}</Text>
      <Text style={styles.detailValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    backgroundColor: 'rgba(0,0,0,0.72)',
  },
  dialog: {
    width: '100%',
    maxWidth: 420,
    padding: 20,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: 20,
    backgroundColor: colors.surface,
  },
  eyebrow: {
    color: colors.accentText,
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1,
  },
  title: {
    marginTop: 8,
    color: colors.textPrimary,
    fontSize: 20,
    fontWeight: '900',
  },
  details: { marginTop: 18, gap: 10 },
  detailRow: { flexDirection: 'row', justifyContent: 'space-between' },
  detailLabel: { color: colors.textMuted, fontSize: 12 },
  detailValue: {
    color: colors.textPrimary,
    fontSize: 13,
    fontWeight: '800',
    fontVariant: ['tabular-nums'],
  },
  notice: {
    marginTop: 18,
    color: colors.textSecondary,
    fontSize: 11,
    lineHeight: 17,
  },
  error: {
    marginTop: 10,
    color: colors.sellText,
    fontSize: 12,
    lineHeight: 18,
  },
  actions: { flexDirection: 'row', marginTop: 18, gap: 10 },
  flex: { flex: 1 },
});
