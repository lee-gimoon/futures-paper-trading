/**
 * 이 파일은 `/market` 경로와 실제 시세 화면 컴포넌트를 연결한다.
 * `(tabs)`는 화면들을 묶는 Route Group 이름이므로 실제 주소에는 포함되지 않는다.
 */

import { MarketScreen } from '@/features/market/MarketScreen';

// MarketRoute는 시세 탭이 선택됐을 때 MarketScreen을 렌더링한다.
export default function MarketRoute() {
  return <MarketScreen />;
}
