// 백엔드 인증 API 호출 모음.
// 쿠키 세션 방식이라 토큰을 직접 저장하는 코드는 없다.
// credentials:'include' 만 붙이면 SESSION 쿠키가 자동으로 오가고, JS는 그 쿠키를 만지지 않는다.
import type { User } from '../../shared/types';
import { toHttpError } from '../../shared/http';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 회원가입: 성공 시 201 + 유저 정보. 단, 이것만으로는 로그인이 아니다(쿠키 미발급).
export async function signup(email: string, password: string, displayName: string): Promise<User> {
  const res = await fetch('/api/auth/signup', {
    method: 'POST',
    headers: JSON_HEADERS,
    credentials: 'include',
    body: JSON.stringify({ email, password, displayName }),
  });
  if (!res.ok) throw await toHttpError(res, '회원가입에 실패했습니다.');
  return res.json();
}

// 로그인 성공 응답: res는 200 상태와 {"message":"로그인 성공"}만 담고 사용자 정보는 담지 않는다.
// 백엔드가 응답의 Set-Cookie 헤더로 SESSION 쿠키를 보내면 브라우저가 저장하고 이후 요청에 자동으로 붙인다.
// 따라서 "누가 로그인했는지"는 로그인 직후 fetchMe()로 따로 가져온다.
export async function login(email: string, password: string): Promise<void> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: JSON_HEADERS,
    credentials: 'include',
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw await toHttpError(res, '로그인에 실패했습니다.');
}

// 로그아웃: 서버 세션을 무효화한다.
export async function logout(): Promise<void> {
  const res = await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
  if (!res.ok) throw await toHttpError(res, '로그아웃에 실패했습니다.');
}

// 내 정보: 로그인 상태면 User, 아니면 null.
// 비로그인일 때 오는 401은 에러가 아니라 "로그인 안 됨"이라는 정상 신호이므로 null로 처리한다.
export async function fetchMe(): Promise<User | null> {
  // JavaScript 코드가 fetch(...)를 호출한다.
  // → 브라우저가 제공하는 fetch 기능이 실행되어 이 URL(/api/auth/me)로 HTTP 요청을 보낸다.
  // 개발 중에는 Vite proxy가 이 /api 요청을 Spring 서버(localhost:8080)로 전달한다.
  // 두 번째 인자는 요청 옵션 객체다.
  // credentials: 'include'는 조건에 맞는 SESSION 쿠키를 브라우저가 이 요청에 자동으로 붙이게 한다.
  const res = await fetch('/api/auth/me', { credentials: 'include' });
  if (res.status === 401) return null;
  if (!res.ok) throw await toHttpError(res, '로그인 상태를 확인하지 못했습니다.');
  return res.json();
}
