/**
 * 이 파일은 앱을 처음 열었을 때 사용할 첫 화면 경로를 작성하는 곳이다.
 * Expo Router는 `app` 폴더의 파일 이름을 내부 주소와 연결하므로,
 * `app/index.tsx`는 앱의 기본 주소 `/`에 표시되는 화면이 된다.
 *
 * 이 파일은 첫 화면의 세부 UI와 상태를 직접 가지지 않는다.
 * 실제 거래 화면은 `TradeScreen`에 맡기고,
 * `HomeScreen`은 Expo Router의 `/` 경로와 그 화면 컴포넌트를 연결한다.
 */

// `@/`는 tsconfig.json에서 `src/` 폴더를 가리키도록 설정한 경로 별칭이다.
import { TradeScreen } from '@/features/trade/TradeScreen';

// HomeScreen은 기본 주소 `/`에서 Expo Router가 렌더링하는 첫 화면 컴포넌트다.
export default function HomeScreen() {
  // JSX의 <TradeScreen />은 함수를 직접 호출하는 코드가 아니라,
  // React에게 이 위치에 TradeScreen 컴포넌트를 렌더링하라고 전달하는 요소다.
  return <TradeScreen />;
}

/**
 * `app/`과 `src/`를 나눈 이유
 *
 * `app/`은 Expo Router가 파일 이름을 화면 경로로 인식하는 폴더다.
 * `src/`는 실제 화면 내용, 상태와 공통 컴포넌트처럼 화면 경로가 아닌 일반 소스 코드를 보관하는 폴더다.
 *
 * 따라서 `app/index.tsx`는 `/` 경로 연결을 담당하고,
 * 실제 화면은 `src/features/trade/TradeScreen.tsx`에 작성한다.
 */
