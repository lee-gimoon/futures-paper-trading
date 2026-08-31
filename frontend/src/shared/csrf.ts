
// 인증과 거래 등 여러 기능의 변경 요청이 함께 사용하는 CSRF 관리 코드이므로 shared 폴더에 둔다.
// 이 파일은 React 컴포넌트 안이 아니라 모듈의 최상위에 있으므로, 같은 브라우저 탭 안에서 csrf.ts를 여러 파일이 import해도 모두 같은 csrfState 변수 하나를 공유한다.
//
// 브라우저 탭 하나에서 React 앱을 실행한 경우
// useAuth.ts ─┐
// http.ts    ─┼→ csrf.ts의 csrfState 하나를 함께 사용
// paperApi.ts┘

// CSRF(Cross-Site Request Forgery): 사이트 간 요청 위조 → 현재 세션의 토큰을 변경 요청 헤더로 보내 악성 사이트의 위조 요청을 차단한다.

// 현재 브라우저 탭이 사용하는 CSRF 헤더 이름과 토큰 값이다.
export type CsrfState = {
  headerName: string;
  token: string;
};

// 브라우저가 React 앱의 JavaScript를 내려받아 실행하면 csrfState가 현재 탭의 JavaScript 실행 메모리에 만들어진다.
// 새로고침하거나 탭을 닫으면 사라지며 쿠키나 localStorage에는 저장하지 않는다.
// 새로고침하면 페이지의 JS 코드가 처음부터 다시 실행됩니다. 그래서 `let csrfState = null`이 다시 실행되어 값이 초기화됩니다.
// 탭을 닫으면 해당 페이지와 JS 실행 환경 자체가 종료되어, 메모리에 있던 변수도 함께 사라집니다.
// 쿠키·`localStorage`·`sessionStorage`·서버 세션 등에 따로 저장하지 않았으므로, 다음 페이지 로드에서 복구할 곳도 없습니다.
let csrfState: CsrfState | null = null;

// `requestCsrfToken()`을 호출해 `/api/auth/csrf` 요청을 시작하면, 응답이 오기 전에 반환되는 Promise를 저장한다.
// 요청이 끝날 때까지 다른 `ensureCsrfToken()` 호출도 이 Promise를 반환해 같은 응답을 기다린다.
// null이면 현재 진행 중인 토큰 요청이 없다.
let csrfLoadPromise: Promise<CsrfState> | null = null;

// 서버에서 받은 JSON이 CsrfState 구조인지 실행 중에 확인하는 타입 가드다.
// 반환 타입 `value is CsrfState`는 이 함수가 true를 반환하면,
// 호출한 쪽에서 전달한 값을 TypeScript가 CsrfState로 판단하도록 하는 타입 가드 문법이다.
function isCsrfState(value: unknown): value is CsrfState {
  return (
    typeof value === 'object' && // 객체인가?
    value !== null && // null은 아닌가?
    'headerName' in value && // headerName 필드가 있는가?
    typeof value.headerName === 'string' && // 그것이 문자열인가?
    'token' in value && // token 필드가 있는가?
    typeof value.token === 'string' // 그것이 문자열인가?
  );
}

// `/api/auth/csrf`에 GET 요청을 보내 현재 WebSession의 CSRF 헤더 이름과 토큰을 받고,
// 그 값을 `csrfState`에 저장한 뒤 반환한다.
//
// 참고: 이 프로젝트에서 실제 WebSession은 로그인 자체가 아니라 서버가 저장할 데이터가 생길 때 시작된다.
// 이 요청에서 CSRF 토큰을 WebSession에 저장하는 일이 일반적으로 세션을 처음 시작하는 계기다.
async function requestCsrfToken(): Promise<CsrfState> {
  // fetch는 브라우저에서 HTTP 요청을 보내고 응답을 Promise로 반환한다.
  // method를 생략했으므로 /api/auth/csrf에 GET 요청을 보낸다.
  const response = await fetch('/api/auth/csrf', {
    // `credentials: 'include'`를 지정하면 브라우저가 전송 가능한 SESSION 같은 쿠키를 요청에 자동으로 포함한다.
    // 서버에서는 WebFlux가 SESSION 쿠키의 세션 ID로 세션 저장소에서 기존 WebSession을 조회해 현재 요청에 연결한다.
    // 이어서 Spring Security의 CSRF 필터가 연결된 WebSession에서 CSRF 토큰을 조회하고, 토큰이 없으면 새로 생성해 저장한다.
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('CSRF 토큰을 받지 못했습니다.');
  }

  // response.json()은 서버 응답 본문(JSON)을 비동기로 읽어 JavaScript 값으로 변환하는 Promise를 반환한다.
  const body: unknown = await response.json();
  if (!isCsrfState(body)) {
    throw new Error('CSRF 토큰 응답 형식이 올바르지 않습니다.');
  }

  csrfState = body;
  // body는 나중에 얻은 CsrfState 값이지만, async 함수 호출자는 이 값을 즉시 받을 수 없다.
  // 따라서 함수 호출 직후에는 Promise<CsrfState>를 받고, await한 뒤에야 CsrfState를 받는다.
  return body;
}

// CSRF 토큰을 매 요청마다 새로 받지 않고 재사용하기 위해 만든 함수다.
// JavaScript 메모리에 CSRF 토큰이 있으면 반환하고, 없으면 requestCsrfToken()을 호출해 서버에서 받은 뒤 반환한다.
//
// 참고: 토큰 발급이 진행 중인 동안 다른 호출도 같은 Promise를 반환하므로, 동시에 호출되어도 /api/auth/csrf 요청은 한 번만 전송된다.
export async function ensureCsrfToken(): Promise<CsrfState> {
  // 응답 후 재사용: 이미 받은 토큰이 메모리에 있으면 서버에 다시 요청하지 않는다.
  if (csrfState) return csrfState;

  // 응답 전 중복 방지: 다른 호출이 이미 토큰 요청을 시작했다면 그 Promise를 함께 기다린다.
  // 첫 호출은 requestCsrfToken()이 반환한 아직 완료되지 않은 Promise를 csrfLoadPromise에 저장한다.
  // 이후 호출은 csrfLoadPromise가 null이 아니므로 if 문 내부를 실행하지 않아 새 요청을 시작하지 않고,
  // 아래 return으로 같은 Promise를 받는다.
  if (!csrfLoadPromise) {
    // .finally(...) 메서드는 지금 바로 실행되어, 안의 람다를 요청 완료 뒤 실행할 함수로 등록한다.
    // 그리고 새 Promise<CsrfState>를 즉시 반환하므로, 람다가 실행되기 전에 그 Promise가 csrfLoadPromise에 대입된다.
    csrfLoadPromise = requestCsrfToken().finally(() => {
      csrfLoadPromise = null;
    });
  }

  return csrfLoadPromise;
}

// 로그인처럼 세션 상태가 바뀐 뒤 기존 값을 버리고 현재 세션의 토큰을 다시 받는다.
export async function refreshCsrfToken(): Promise<CsrfState> {
  csrfState = null;
  return ensureCsrfToken();
}

// 로그아웃·세션 만료 시 현재 탭이 기억한 토큰을 제거한다.
export function clearCsrfToken(): void {
  csrfState = null;
}
