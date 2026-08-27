/**
 * 이 파일은 `app/index.tsx`처럼 화면 내용을 직접 작성하는 곳이 아니다.
 * Expo Router가 앱을 열 때 먼저 이 파일을 사용해 “화면을 어떻게 이동할지” 정한다.
 *
 * 아래의 `Stack`은 새 화면으로 이동하면 그 화면을 위에 쌓고,
 * 뒤로 가면 맨 위 화면을 꺼내 이전 화면을 다시 보여 준다.
 * `StatusBar`는 휴대폰 맨 위 시간·신호·배터리 아이콘의 색과 표시 방식을 설정한다.
 * 실제 첫 화면 내용은 `app/index.tsx`에 작성한다.
 */

// Expo Router가 제공하며, 화면들의 순서를 기억하고 화면 이동·뒤로 가기를 처리하는 React 컴포넌트다.
import { Stack } from 'expo-router';
// 휴대폰 상단 상태 표시줄의 아이콘·글자 색과 표시 방식을 설정한다.
import { StatusBar } from 'expo-status-bar';

// RootLayout은 앱 전체의 화면 이동 방식과 상태 표시줄 모양을 한곳에서 정하는 레이아웃 컴포넌트다.
export default function RootLayout() {
  return (
    <>
      {/* <>...</>는 화면에 보이지 않는 묶음으로, Stack과 StatusBar를 함께 반환한다. */}

      {/*
       * Expo Router는 현재 주소를 보고 표시할 화면 파일을 찾는다.
       * Stack은 찾은 화면들을 순서대로 쌓아 화면 이동·뒤로 가기·전환을 관리한다.
       * Stack이 없으면 이전 화면 기록, 뒤로 가기와 화면 전환을 앱 코드로 직접 만들어야 한다.
       */}
      {/* 모든 화면에 기본으로 표시되는 Stack 제목 표시줄을 숨긴다. */}
      <Stack screenOptions={{ headerShown: false }} />

      {/* style="light"는 운영체제가 그리는 상태 표시줄의 글자·아이콘 색을 밝게 설정한다. */}
      <StatusBar style="light" />
    </>
  );
}

/**
 * 앱 시작 시 화면이 준비되는 순서
 *
 * QR 코드로 앱을 처음 열면 기본 경로 /로 시작한다.
 * → expo-router/entry 실행
 * → Expo Router 시작
 * → Expo Router가 /를 app/index.tsx에 연결하고,
 *   app/_layout.tsx를 모든 화면에 공통으로 적용할 틀(루트 레이아웃)로 사용
 * → RootLayout() 함수 실행
 * → RootLayout이 반환한 <Stack />과 <StatusBar /> 렌더링
 * → Stack이 Expo Router가 연결한 app/index.tsx 화면 표시
 */

/**
 * 주문 화면을 나중에 추가했을 때의 화면 이동 예시
 *
 * app/orders.tsx 파일을 만들면 Expo Router가 이 파일을 내부 주소 /orders에 연결한다.
 *
 * app/index.tsx의 주문 버튼을 누를 때 router.push('/orders')를 실행
 * → Expo Router가 /orders에 연결된 app/orders.tsx 화면을 찾음
 * → Stack이 현재 첫 화면(index.tsx)은 아래에 둔 채 orders.tsx 화면을 그 위에 쌓음
 * → 주문 화면 표시
 *
 * 화면 순서: [index.tsx, orders.tsx]
 *
 * 사용자가 주문 화면에서 뒤로 가기
 * → Stack이 맨 위의 orders.tsx 화면을 꺼냄
 * → 아래에 있던 index.tsx 첫 화면을 다시 표시
 *
 * 화면 순서: [index.tsx]
 */
