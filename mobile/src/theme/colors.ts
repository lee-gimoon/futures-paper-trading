/**
 * 이 파일은 여러 화면과 컴포넌트에서 반복해서 사용하는 색상을 한곳에 모은다.
 * 화면마다 색상 코드를 직접 쓰지 않고 `colors.background`, `colors.buy`처럼 가져다 쓰면
 * 같은 의미의 색상을 일관되게 유지하고 나중에 전체 테마도 한 파일에서 바꿀 수 있다.
 */
export const colors = {
  // 앱의 기본 배경, 카드, 선택 영역과 입력창에 사용하는 어두운 바탕색이다.
  background: '#07111f',
  surface: '#0d1b2a',
  surfaceMuted: '#12233a',
  input: '#0a1727',
  // 카드·입력창의 경계와 호가 행 사이를 구분하는 선 색상이다.
  border: '#1d3048',
  borderStrong: '#2a4667',
  divider: '#14263b',
  // 중요도에 따라 사용하는 기본 글자색과 버튼 글자색이다.
  textPrimary: '#f8fafc',
  textSecondary: '#9fb0c5',
  textMuted: '#64758b',
  buttonText: '#ffffff',
  // 선택된 주문 방식과 안내 메시지를 표현하는 강조 색상이다.
  accent: '#2388ff',
  accentMuted: '#102f52',
  accentBorder: '#20568c',
  accentText: '#82bdff',
  // 매수는 초록 계열, 매도는 빨강 계열로 구분한다.
  buy: '#168a62',
  buyText: '#45d5a0',
  sell: '#d94b62',
  sellText: '#ff7c8e',
  // as const는 각 속성을 변경할 수 없는 구체적인 문자열 값으로 추론하게 한다.
} as const;
