/**
 * 이 파일은 여러 화면과 컴포넌트에서 반복해서 사용하는 색상을 한곳에 모은다.
 * 화면마다 색상 코드를 직접 쓰지 않고 `colors.background`, `colors.buy`처럼 가져다 쓰면
 * 같은 의미의 색상을 일관되게 유지하고 나중에 전체 테마도 한 파일에서 바꿀 수 있다.
 */
export const colors = {
  // 앱의 기본 배경, 카드, 선택 영역과 입력창에 사용하는 어두운 바탕색이다.
  background: '#101216',
  surface: '#181b21',
  surfaceMuted: '#232730',
  input: '#20242c',
  // 카드·입력창의 경계와 호가 행 사이를 구분하는 선 색상이다.
  border: '#2a2e36',
  borderStrong: '#414752',
  divider: '#22262e',
  // 중요도에 따라 사용하는 기본 글자색과 버튼 글자색이다.
  textPrimary: '#f1f3f5',
  textSecondary: '#a6adb8',
  textMuted: '#7d8796',
  buttonText: '#ffffff',
  // 선택된 주문 방식과 안내 메시지를 표현하는 강조 색상이다.
  accent: '#f0c75e',
  accentMuted: '#332d1f',
  accentBorder: '#756039',
  accentText: '#f0c75e',
  onAccent: '#171a20',
  // 매수는 초록 계열, 매도는 빨강 계열로 구분한다.
  buy: '#087f61',
  buyText: '#2dcc9a',
  sell: '#c83e59',
  sellText: '#f36780',
  buyMuted: '#13342d',
  sellMuted: '#3a212a',
  // as const는 각 속성을 변경할 수 없는 구체적인 문자열 값으로 추론하게 한다.
} as const;
