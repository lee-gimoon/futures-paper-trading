import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import { colors } from '@/theme/colors';

type LoadingStateProps = {
  message?: string;
};

export function LoadingState({
  message = '데이터를 불러오는 중입니다.',
}: LoadingStateProps) {
  return (
    <View accessibilityLiveRegion="polite" style={styles.container}>
      <ActivityIndicator color={colors.accent} size="large" />
      <Text style={styles.message}>{message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 160,
    padding: 24,
  },
  message: {
    marginTop: 14,
    color: colors.textSecondary,
    fontSize: 13,
    textAlign: 'center',
  },
});
