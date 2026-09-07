import type { ReactNode } from 'react';
import { Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { colors } from '@/theme/colors';

type WebAppFrameProps = {
  children: ReactNode;
};

/**
 * 넓은 브라우저에서 앱을 휴대폰 너비로 보여주는 웹 전용 프레임이다.
 * Android와 iOS에서는 View를 추가하지 않아 Expo Go와 APK 레이아웃에 영향을 주지 않는다.
 */
export function WebAppFrame({ children }: WebAppFrameProps) {
  const { width } = useWindowDimensions();

  if (Platform.OS !== 'web') {
    return <>{children}</>;
  }

  const hasDesktopGutter = width >= 600;

  return (
    <View style={[styles.viewport, hasDesktopGutter && styles.desktopViewport]}>
      <View style={[styles.frame, hasDesktopGutter && styles.desktopFrame]}>
        {children}
      </View>
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
  desktopViewport: {
    paddingHorizontal: 12,
    paddingVertical: 12,
  },
  frame: {
    flex: 1,
    width: '100%',
    maxWidth: 560,
    backgroundColor: colors.background,
    borderLeftWidth: 1,
    borderRightWidth: 1,
    borderColor: colors.borderStrong,
  },
  desktopFrame: {
    borderTopWidth: 1,
    borderBottomWidth: 1,
  },
});
