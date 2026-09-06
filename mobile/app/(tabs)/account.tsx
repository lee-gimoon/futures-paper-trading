/**
 * 이 파일은 `/account` 경로와 실제 계정 화면 컴포넌트를 연결한다.
 * 화면 내용은 경로 파일에 작성하지 않고 AccountScreen이 담당한다.
 */

import { AccountScreen } from '@/features/account/AccountScreen';

// AccountRoute는 계정 탭이 선택됐을 때 AccountScreen을 렌더링한다.
export default function AccountRoute() {
  return <AccountScreen />;
}
