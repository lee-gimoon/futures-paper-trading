import { StyleSheet, Text, View } from 'react-native';

import { colors } from '@/theme/colors';

type EmptyStateProps = {
  title: string;
  description: string;
};

export function EmptyState({ title, description }: EmptyStateProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.icon}>≡</Text>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.description}>{description}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 180,
    padding: 24,
  },
  icon: {
    color: colors.accentText,
    fontSize: 30,
    fontWeight: '800',
  },
  title: {
    marginTop: 12,
    color: colors.textPrimary,
    fontSize: 16,
    fontWeight: '800',
  },
  description: {
    marginTop: 8,
    color: colors.textMuted,
    fontSize: 13,
    lineHeight: 20,
    textAlign: 'center',
  },
});
