// Expo Router가 제공하는 화면 전환 컨테이너다.
import { Stack } from 'expo-router';
// 휴대폰 상단의 시간, 배터리 등이 표시되는 상태 표시줄을 설정한다.
import { StatusBar } from 'expo-status-bar';

// app/_layout.tsx는 Expo Router가 개별 화면보다 먼저 실행하는 최상위 파일이다.
// 앞으로 app/에 화면 파일을 추가하면 이 Stack 안에서 화면 이동이 관리된다.
export default function RootLayout() {
  return (
    <>
      {/* headerShown: false는 Expo Router의 기본 상단 제목 표시줄을 숨긴다. */}
      <Stack screenOptions={{ headerShown: false }} />
      {/* 어두운 앱 배경에서 잘 보이도록 상태 표시줄 아이콘을 밝게 표시한다. */}
      <StatusBar style="light" />
    </>
  );
}
