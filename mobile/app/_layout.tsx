/**
 * Expo Router가 app의 경로 파일들을 이 루트 Stack의 화면으로 연결한다.
 * AuthProvider는 로그인 상태를 공유하며 공개 화면의 렌더링을 막지 않는다.
 * 주문 전송과 개인 계좌 조회는 각 기능에서 인증을 확인하고 서버에서도 권한을 검증한다.
 */
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { WebAppFrame } from '@/components/WebAppFrame';
import { AuthProvider } from '@/features/auth/AuthProvider';
import { colors } from '@/theme/colors';

export default function RootLayout() {
  return (
    <AuthProvider>
      <WebAppFrame>
        {/* Stack은 현재 경로 파일의 기본 export를 화면으로 렌더링하고 뒤로 가기 기록을 관리한다. */}
        <Stack
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: colors.background },
          }}
        >
          <Stack.Screen name="index" />
          <Stack.Screen name="(tabs)" />
          <Stack.Screen name="chart" />
          <Stack.Screen name="login" options={{ presentation: 'modal' }} />
          <Stack.Screen name="signup" options={{ presentation: 'modal' }} />
        </Stack>
        <StatusBar style="light" />
      </WebAppFrame>
    </AuthProvider>
  );
}
