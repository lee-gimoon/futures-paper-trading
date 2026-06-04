// 백엔드 인증 API 호출 모음.
// 쿠키 세션 방식이라 토큰을 직접 저장하는 코드는 없다.
// credentials:'include' 만 붙이면 SESSION 쿠키가 자동으로 오가고, JS는 그 쿠키를 만지지 않는다.
import type { User } from '../types';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 응답이 실패면 백엔드가 준 {"message": ...}를 꺼내 Error로 만든다. (없으면 fallback 문구)
async function toError(res: Response, fallback: string): Promise<Error> {
  try {
    const body = await res.json();
    if (body && typeof body.message === 'string') return new Error(body.message);
  } catch {
    // 본문이 JSON이 아닐 수도 있음(검증 400 등) → fallback 사용
  }
  return new Error(fallback);
}

// 회원가입: 성공 시 201 + 유저 정보. 단, 이것만으로는 로그인이 아니다(쿠키 미발급).
export async function signup(email: string, password: string, displayName: string): Promise<User> {
  const res = await fetch('/api/auth/signup', {
    method: 'POST',
    headers: JSON_HEADERS,
    credentials: 'include',
    body: JSON.stringify({ email, password, displayName }),
  });
  if (!res.ok) throw await toError(res, '회원가입에 실패했습니다.');
  return res.json();
}

// 로그인: 성공 시 SESSION 쿠키가 발급된다. 응답 본문엔 유저 정보가 없어서
// "누가 로그인했는지"는 직후 fetchMe()로 따로 가져온다.
export async function login(email: string, password: string): Promise<void> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: JSON_HEADERS,
    credentials: 'include',
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw await toError(res, '로그인에 실패했습니다.');
}

// 로그아웃: 서버 세션을 무효화한다.
export async function logout(): Promise<void> {
  await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
}

// 내 정보: 로그인 상태면 User, 아니면 null.
// 비로그인일 때 오는 401은 에러가 아니라 "로그인 안 됨"이라는 정상 신호이므로 null로 처리한다.
export async function fetchMe(): Promise<User | null> {
  const res = await fetch('/api/auth/me', { credentials: 'include' });
  if (!res.ok) return null;
  return res.json();
}
