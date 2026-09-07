import type { ReactNode } from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import { colors } from '@/theme/colors';

type WebAppFrameProps = {
  children: ReactNode;
};

/**
 * 넓은 브라우저에서 앱을 휴대폰 너비로 보여주는 웹 전용 프레임이다.
 * Android와 iOS에서는 View를 추가하지 않아 Expo Go와 APK 레이아웃에 영향을 주지 않는다.
 */
export function WebAppFrame({ children }: WebAppFrameProps) {
  if (Platform.OS !== 'web') {
    return <>{children}</>;
  }

  return (
    <View style={styles.viewport}>
      <View style={styles.frame}>{children}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  viewport: {
    flex: 1,
    width: '100%',
    alignItems: 'center',
    backgroundColor: '#080a0d',
  },
  frame: {
    flex: 1,
    width: '100%',
    maxWidth: 430,
    backgroundColor: colors.background,
    borderLeftWidth: 1,
    borderRightWidth: 1,
    borderColor: colors.borderStrong,
  },
});
