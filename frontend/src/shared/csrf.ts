
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
let csrfState: CsrfState | null = null;

// 동시에 여러 요청이 토큰을 요구해도 /api/auth/csrf를 한 번만 호출하도록 진행 중인 Promise를 공유한다.
let csrfLoadPromise: Promise<CsrfState> | null = null;

// 서버에서 받은 JSON이 CsrfState 구조인지 실행 중에 확인하는 타입 가드다.
function isCsrfState(value: unknown): value is CsrfState {
  return (
    typeof value === 'object' &&
    value !== null &&
    'headerName' in value &&
    typeof value.headerName === 'string' &&
    'token' in value &&
    typeof value.token === 'string'
  );
}

// 현재 WebSession의 CSRF 토큰을 서버에서 받아 JavaScript 메모리에 저장한다.
async function requestCsrfToken(): Promise<CsrfState> {
  const response = await fetch('/api/auth/csrf', {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('CSRF 토큰을 받지 못했습니다.');
  }

  const body: unknown = await response.json();
  if (!isCsrfState(body)) {
    throw new Error('CSRF 토큰 응답 형식이 올바르지 않습니다.');
  }

  csrfState = body;
  return body;
}

// 현재 토큰이 있으면 재사용하고, 없으면 서버에서 받는다.
export async function ensureCsrfToken(): Promise<CsrfState> {
  if (csrfState) return csrfState;

  if (!csrfLoadPromise) {
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
