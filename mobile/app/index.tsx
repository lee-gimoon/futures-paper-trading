/**
 * Expo Router는 이 파일의 기본 export인 IndexRoute를 기본 경로 /에 연결한다.
 * RootLayout의 Stack → IndexRoute → HomeScreen 순서로 구성되며,
 * HomeScreen의 Redirect가 공개 시세 탭을 첫 화면으로 연다.
 *
 * app/은 Expo Router가 읽는 화면 경로 폴더이고,
 * src/는 실제 화면 내용·상태·공통 컴포넌트를 보관하는 일반 소스 코드 폴더다.
 */
import { HomeScreen } from '@/features/home/HomeScreen';

export default function IndexRoute() {
  return <HomeScreen />;
}
