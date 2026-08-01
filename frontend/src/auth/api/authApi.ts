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
// 서버에 로그아웃 요청을 보내고 응답을 기다리는 네트워크 작업이므로 async 비동기 함수로 선언한다.
export async function logout(): Promise<void> {
  // 세션 쿠키를 포함해 /api/auth/logout에 POST 요청을 보내고, 서버 응답을 res에 담는다.
  const res = await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
  // HTTP 응답이 실패면 응답의 오류 메시지로 HttpError(HTTP 오류)를 만든 뒤 예외를 던진다.
  if (!res.ok) throw await toHttpError(res, '로그아웃에 실패했습니다.');
}

// 현재 로그인한 사용자 정보를 조회한다. 로그인 상태가 아니면 null을 반환한다.
export async function fetchMe(): Promise<User | null> {
  const res = await fetch('/api/auth/me', { credentials: 'include' }); // 세션 쿠키를 포함해 사용자 조회 요청을 보낸다.
  if (res.status === 401) return null; // 401은 오류가 아닌 비로그인 상태로 처리한다.
  if (!res.ok) throw await toHttpError(res, '로그인 상태를 확인하지 못했습니다.'); // 그 외 HTTP 오류는 예외로 처리한다.
  return res.json(); // 성공 응답의 JSON을 User 객체로 반환한다.
}
