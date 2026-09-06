/** 로그인 URL의 반환 경로는 앱 내부의 정해진 탭만 허용한다. */
export function authReturn(
  value: unknown,
): '/market' | '/trade' | '/orders' | '/account' {
  return value === '/trade' || value === '/orders' || value === '/account'
    ? value
    : '/market';
}
