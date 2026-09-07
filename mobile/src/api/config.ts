/**
 * 모바일 앱이 요청할 Spring 서버의 기본 주소를 한곳에서 관리한다.
 * 로컬 서버를 사용할 때는 `.env.local`의 `EXPO_PUBLIC_API_BASE_URL`만 바꾸면 된다.
 */

import { Platform } from 'react-native';

const PRODUCTION_API_BASE_URL =
  'https://futures-paper-trading-production.up.railway.app';

// 웹 빌드는 현재 접속한 Railway 서버를 그대로 사용해 쿠키가 같은 출처에서 동작하게 한다.
const DEFAULT_API_BASE_URL =
  Platform.OS === 'web' ? '' : PRODUCTION_API_BASE_URL;

// 끝의 `/`를 제거해 `${API_BASE_URL}/api/...`를 만들 때 슬래시가 겹치지 않게 한다.
export const API_BASE_URL = (
  process.env.EXPO_PUBLIC_API_BASE_URL ?? DEFAULT_API_BASE_URL
).replace(/\/$/, '');

export function apiUrl(path: string): string {
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
}
