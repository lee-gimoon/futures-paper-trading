/**
 * 모바일 앱이 요청할 Spring 서버의 기본 주소를 한곳에서 관리한다.
 * 로컬 서버를 사용할 때는 `.env.local`의 `EXPO_PUBLIC_API_BASE_URL`만 바꾸면 된다.
 */

const PRODUCTION_API_BASE_URL =
  'https://futures-paper-trading-production.up.railway.app';

// 끝의 `/`를 제거해 `${API_BASE_URL}/api/...`를 만들 때 슬래시가 겹치지 않게 한다.
export const API_BASE_URL = (
  process.env.EXPO_PUBLIC_API_BASE_URL ?? PRODUCTION_API_BASE_URL
).replace(/\/$/, '');

export function apiUrl(path: string): string {
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
}
