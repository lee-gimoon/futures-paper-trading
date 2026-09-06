/**
 * 이 파일은 `/trade` 경로와 기존 거래 화면 컴포넌트를 연결한다.
 * 거래 화면의 상태와 JSX는 src/features/trade/TradeScreen.tsx에 그대로 유지한다.
 */

import { TradeScreen } from '@/features/trade/TradeScreen';

// TradeRoute는 거래 탭이 선택됐을 때 TradeScreen을 렌더링한다.
export default function TradeRoute() {
  return <TradeScreen />;
}
