/**
 * 이 파일은 `/login` 경로와 실제 로그인 화면 컴포넌트를 연결한다.
 * 입력 상태와 화면 JSX는 경로 파일에 작성하지 않고 LoginScreen이 담당한다.
 */

import { LoginScreen } from '@/features/auth/LoginScreen';

// LoginRoute는 Expo Router가 `/login` 경로에서 렌더링하는 경로 컴포넌트다.
export default function LoginRoute() {
  return <LoginScreen />;
}
