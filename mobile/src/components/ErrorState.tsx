import { StyleSheet, Text, View } from 'react-native';

import { AppButton } from '@/components/AppButton';
import { colors } from '@/theme/colors';

type ErrorStateProps = {
  message: string;
  onRetry?: () => void;
};

export function ErrorState({ message, onRetry }: ErrorStateProps) {
  return (
    <View accessibilityLiveRegion="assertive" style={styles.container}>
      <Text style={styles.title}>데이터를 표시하지 못했습니다</Text>
      <Text style={styles.message}>{message}</Text>
      {onRetry ? (
        <View style={styles.action}>
          <AppButton label="다시 시도" onPress={onRetry} variant="primary" />
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 180,
    padding: 24,
    borderWidth: 1,
    borderColor: colors.sell,
    borderRadius: 16,
    backgroundColor: colors.surface,
  },
  title: {
    color: colors.textPrimary,
    fontSize: 16,
    fontWeight: '800',
  },
  message: {
    marginTop: 10,
    color: colors.textSecondary,
    fontSize: 13,
    lineHeight: 20,
    textAlign: 'center',
  },
  action: {
    alignSelf: 'stretch',
    marginTop: 18,
  },
});
