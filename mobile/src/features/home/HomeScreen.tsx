/** 첫 경로에 들어오면 별도 소개 화면을 거치지 않고 공개 시세 탭을 연다. */
import { Redirect } from 'expo-router';

export function HomeScreen() {
  return <Redirect href="/market" />;
}
