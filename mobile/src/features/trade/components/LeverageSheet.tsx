/** 서버가 지원하는 배수만 선택하고 저장에 성공해야 선택 창을 닫는다. */
import { useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { AppButton } from '@/components/AppButton';
import { AppIcon } from '@/components/AppIcon';
import { LEVERAGES } from '@/features/trade/orderEstimate';
import { colors } from '@/theme/colors';

export function LeverageSheet({
  visible,
  leverage,
  saving,
  error,
  preview = false,
  onClose,
  onSave,
}: {
  visible: boolean;
  leverage: number;
  saving: boolean;
  error: string | null;
  preview?: boolean;
  onClose: () => void;
  onSave: (value: number) => Promise<boolean>;
}) {
  const [selected, setSelected] = useState(leverage);
  return (
    <Modal
      transparent
      animationType="slide"
      visible={visible}
      onRequestClose={() => {
        if (!saving) onClose();
      }}
    >
      <View style={styles.backdrop}>
        <Pressable
          style={StyleSheet.absoluteFill}
          disabled={saving}
          onPress={onClose}
          accessibilityLabel="레버리지 선택 닫기"
          accessibilityRole="button"
        />
        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <View style={styles.handle} />
          <View style={styles.header}>
            <Text style={styles.title}>레버리지 조정</Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="닫기"
              disabled={saving}
              onPress={onClose}
              style={styles.close}
            >
              <AppIcon name="close" />
            </Pressable>
          </View>
          <Text style={styles.symbol}>BTCUSDT · 격리</Text>
          <Text style={styles.value}>
            {selected}
            <Text style={styles.times}>x</Text>
          </Text>
          <View style={styles.options}>
            {LEVERAGES.map((value) => (
              <Pressable
                accessibilityRole="button"
                accessibilityState={{ selected: value === selected }}
                disabled={saving}
                key={value}
                onPress={() => setSelected(value)}
                style={[styles.option, selected === value && styles.selected]}
              >
                <Text
                  style={[
                    styles.optionText,
                    selected === value && { color: colors.accentText },
                  ]}
                >
                  {value}x
                </Text>
              </Pressable>
            ))}
          </View>
          <Text style={styles.description}>
            {preview
              ? '로그인 전에는 레버리지 설정을 미리 볼 수 있습니다. 로그인하면 내 계좌에 저장된 배수가 적용됩니다.'
              : '변경한 배수는 다음 신규 주문에 적용됩니다. 이미 열린 포지션의 레버리지는 유지됩니다.'}
          </Text>
          {error ? <Text style={styles.error}>{error}</Text> : null}
          <AppButton
            label={saving ? '적용 중...' : '확인'}
            disabled={saving}
            variant="primary"
            onPress={() => {
              void onSave(selected).then((saved) => {
                if (saved) onClose();
              });
            }}
          />
        </SafeAreaView>
      </View>
    </Modal>
  );
}
const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: '#00000099',
  },
  sheet: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 22,
  },
  handle: {
    height: 4,
    width: 32,
    borderRadius: 2,
    backgroundColor: colors.borderStrong,
    alignSelf: 'center',
    marginBottom: 15,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  title: { color: colors.textPrimary, fontSize: 20, fontWeight: '700' },
  close: {
    minHeight: 40,
    minWidth: 40,
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  symbol: { color: colors.textMuted, fontSize: 12, marginTop: 5 },
  value: {
    textAlign: 'center',
    color: colors.textPrimary,
    fontSize: 48,
    fontWeight: '700',
    paddingVertical: 22,
  },
  times: { color: colors.accentText, fontSize: 25 },
  options: { flexDirection: 'row', gap: 6 },
  option: {
    flex: 1,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 6,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  selected: { borderColor: colors.accent, backgroundColor: colors.accentMuted },
  optionText: { color: colors.textSecondary, fontSize: 12 },
  description: {
    color: colors.textMuted,
    fontSize: 12,
    lineHeight: 20,
    marginVertical: 22,
  },
  error: { color: colors.sellText, fontSize: 12, marginBottom: 12 },
});
