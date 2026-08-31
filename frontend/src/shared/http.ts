import { ensureCsrfToken } from './csrf';

// 안전한 HTTP 메서드 이름을 중복 없이 보관할 Set(집합) 객체를 만든다.
// 대괄호 []의 배열은 Set 생성자에 전달할 초기값 목록이며, 각 문자열이 Set의 원소가 된다.
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

// 브라우저에서 로그인·주문 등 우리 Spring 서버의 API 주소로 요청을 보낼 때,
// 세션 쿠키와 CSRF 헤더를 매번 직접 붙이지 않도록 만든 공통 fetch 함수다.
// 이 정보는 우리 Spring 서버의 WebSession·CSRF 검증용이므로, 인증 방식이 다른 Binance 같은 외부 API에는 사용하지 않는다.
// 세션 쿠키를 항상 포함하고, 데이터를 변경하는 요청에는 CSRF 헤더를 자동으로 붙인다.
export async function apiFetch(
  // input은 fetch의 첫 번째 인자다. 요청 주소 문자열·Request 객체·URL 객체처럼 요청 대상을 나타내는 값을 받는다.
  input: RequestInfo | URL,
  // init은 fetch의 두 번째 인자다. method, headers, body 같은 요청 설정 객체를 받으며, 생략하면 빈 객체를 사용한다.
  init: RequestInit = {},
): Promise<Response> {
  // ??(null 병합 연산자)는 왼쪽 값이 null 또는 undefined일 때만 오른쪽 기본값을 사용한다.
  const method = (init.method ?? 'GET').toUpperCase(); // toUpperCase()는 메서드 이름을 대문자로 통일한다.
  const headers = new Headers(init.headers);

  if (!SAFE_METHODS.has(method)) {
    const csrf = await ensureCsrfToken();
    headers.set(csrf.headerName, csrf.token);
  }

  return fetch(input, {
    ...init, // ...은 객체 펼침 문법으로 init의 속성을 이 객체 바깥으로 풀어 넣는다. 뒤에 같은 이름의 속성이 있으면 앞선 값을 덮어쓴다.
    headers,
    credentials: 'include',
  });

  // 예시: 주문 생성 요청이라면 위 fetch는 최종적으로 아래 형태로 호출된다.
  // fetch('/api/paper/orders', {
  //   method: 'POST',
  //   body: '{"symbol":"BTCUSDT"}',
  //   headers, // Content-Type, X-CSRF-TOKEN 등이 들어 있는 Headers 객체
  //   credentials: 'include',
  // });
}

// API 호출이 실패했을 때, 상태 코드와 오류 문구를 함께 전달할 Error 클래스다.
export class HttpError extends Error { // Error를 확장하므로 try/catch에서 일반 오류처럼 잡을 수 있다.
  constructor( // new HttpError(401, '로그인이 필요합니다.')처럼 객체를 만들 때 실행된다.
    public readonly status: number, // HTTP 상태 코드. 예: 401(로그인 필요), 400(잘못된 요청), 500(서버 오류).
    message: string, // 사용자에게 보여 줄 오류 문구.
  ) {
    super(message); // 부모 Error에 message를 전달해 error.message로 읽을 수 있게 한다.
    this.name = 'HttpError'; // 콘솔에 Error 대신 HttpError라는 이름으로 표시한다.
  }
}

// toHttpError의 to는 "~로 변환한다"는 뜻으로, 실패한 HTTP 응답(Response)을 HttpError로 바꾼다.
// 실패한 HTTP 응답(Response)을 HttpError로 바꾸는 이유: API 함수는 이 오류를 throw하고, 화면 훅은 catch한다.
// status가 401이면 세션 만료로 처리하고, 그 외에는 message를 화면에 표시한다.
export async function toHttpError(res: Response, fallback: string): Promise<HttpError> {
  try {
    const body: unknown = await res.json();
    if (
      typeof body === 'object' &&
      body !== null &&
      'message' in body &&
      typeof body.message === 'string'
    ) {
      return new HttpError(res.status, body.message);
    }
  } catch {
    // 본문이 JSON이 아니거나 비어 있으면, 아래 fallback 메시지를 사용한다.
  }

  // 응답 JSON에 { message: string }이 없으면 fallback을 사용한다.
  // fallback은 원래 사용할 서버 메시지를 얻지 못했을 때 대신 쓰는 "대체값"이다.
  return new HttpError(res.status, fallback);
}
