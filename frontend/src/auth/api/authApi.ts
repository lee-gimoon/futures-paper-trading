// 백엔드 인증 API 호출 모음.
// 쿠키 세션 방식이라 토큰을 직접 저장하는 코드는 없다.
// apiFetch가 SESSION 쿠키를 포함하고, 상태 변경 요청에는 CSRF 헤더도 자동으로 붙인다.
import type { User } from '../../shared/types';
import { apiFetch, toHttpError } from '../../shared/http';

// JSON_HEADERS는 요청 본문이 JSON 형식임을 서버에 알리는 HTTP 헤더를 담은 상수다.
const JSON_HEADERS = { 'Content-Type': 'application/json' };

// API(Application Programming Interface)란 서로 다른 프로그램이
// 정해진 규칙으로 요청과 응답을 주고받는 창구이다.
//
// 이 파일의 함수들이 사용하는 apiFetch()는 브라우저의 fetch()를 감싼 프로젝트 공통 함수이다.
// 인증 API는 요청 주소와 본문만 정하고, 세션 쿠키와 CSRF 정책은 apiFetch에 맡긴다.

// 회원가입: 성공 시 201 + 유저 정보. 단, 이것만으로는 로그인이 아니다(쿠키 미발급).
export async function signup(email: string, password: string, displayName: string): Promise<User> {
  const res = await apiFetch('/api/auth/signup', {
    method: 'POST',
    headers: JSON_HEADERS,
    // JSON.stringify(...)는 JavaScript 객체를 HTTP 요청 본문에 담을 JSON 문자열로 변환한다.
    body: JSON.stringify({
      email: email,
      password: password,
      displayName: displayName,
    }),
  });
  if (!res.ok) throw await toHttpError(res, '회원가입에 실패했습니다.');
  return res.json();
}

// 로그인 성공 응답: res는 200 상태와 {"message":"로그인 성공"}만 담고 사용자 정보는 담지 않는다.
// 백엔드가 응답의 Set-Cookie 헤더로 SESSION 쿠키를 보내면 브라우저가 저장하고 이후 요청에 자동으로 붙인다.
// 따라서 "누가 로그인했는지"는 로그인 직후 fetchMe()로 따로 가져온다.
export async function login(email: string, password: string): Promise<void> {
  const res = await apiFetch('/api/auth/login', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw await toHttpError(res, '로그인에 실패했습니다.');
}

// 로그아웃: 서버 세션을 무효화한다.
// 서버에 로그아웃 요청을 보내고 응답을 기다리는 네트워크 작업이므로 async 비동기 함수로 선언한다.
export async function logout(): Promise<void> {
  // 세션 쿠키를 포함해 /api/auth/logout에 POST 요청을 보내고, 서버 응답을 res에 담는다.
  const res = await apiFetch('/api/auth/logout', {
    method: 'POST',
  });
  // HTTP 응답이 실패면 응답의 오류 메시지로 HttpError(HTTP 오류)를 만든 뒤 예외를 던진다.
  if (!res.ok) throw await toHttpError(res, '로그아웃에 실패했습니다.');
}

// 현재 로그인한 사용자 정보를 조회한다. 로그인 상태가 아니면 null을 반환한다.
export async function fetchMe(): Promise<User | null> {
  // apiFetch()는 내부에서 브라우저의 fetch()를 호출하는 프로젝트 공통 함수다.
  // 여기서 method를 생략하면 내부 fetch()가 기본값인 GET 요청을 보낸다.
  const res = await apiFetch('/api/auth/me');
  if (res.status === 401) return null; // 401은 오류가 아닌 비로그인 상태로 처리한다.
  if (!res.ok) throw await toHttpError(res, '로그인 상태를 확인하지 못했습니다.'); // 그 외 HTTP 오류는 예외로 처리한다.
  return res.json(); // 성공 응답의 JSON을 User 객체로 반환한다.
}
