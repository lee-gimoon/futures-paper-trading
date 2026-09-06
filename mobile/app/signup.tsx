/**
 * 이 파일은 `/signup` 경로와 실제 회원가입 화면 컴포넌트를 연결한다.
 * 입력 상태와 화면 JSX는 경로 파일에 작성하지 않고 SignupScreen이 담당한다.
 */

import { SignupScreen } from '@/features/auth/SignupScreen';

// SignupRoute는 Expo Router가 `/signup` 경로에서 렌더링하는 경로 컴포넌트다.
export default function SignupRoute() {
  return <SignupScreen />;
}
