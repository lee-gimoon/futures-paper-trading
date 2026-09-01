# 세션 인증 기반 CSRF: 만들면서 배우는 구현 가이드

이 문서는 현재 프로젝트의 Spring WebFlux 세션 인증과 React 웹에 CSRF 보호를 **직접 구현하면서 이해하기 위한 실습서**다.

번호가 붙은 단계에는 반드시 새 파일 생성 또는 기존 파일 수정이 있다. 개념은 별도 이론 단계로 분리하지 않고, 방금 작성한 코드 바로 아래에서 설명한다.

각 단계는 다음 순서로 진행한다.

```text
1. 이번 단계의 파일을 연다.
2. 제시된 코드를 직접 작성한다.
3. 작성한 코드가 맡은 역할을 읽는다.
4. 완료 기준을 확인하고 다음 단계로 간다.
```

> 중요: 3단계에서 서버 CSRF를 켜는 순간 기존 React의 로그인·회원가입·주문 요청은 토큰이 없으면 `403 Forbidden`이 된다. 학습을 위해 파일은 단계별로 작성하되, **1~8단계는 한 작업 단위로 모두 끝낸 후 애플리케이션을 실행한다.** 서버와 React 중 한쪽만 따로 배포하지 않는다.

---

## 완성 목표 — 읽기 안내이며 구현 단계가 아니다

현재 변경 요청은 `SESSION` 쿠키만 보낸다.

```http
POST /api/paper/orders
Cookie: SESSION=S-1234
Content-Type: application/json
```

적용 후에는 같은 세션의 CSRF 토큰도 헤더로 보낸다.

```http
POST /api/paper/orders
Cookie: SESSION=S-1234
X-CSRF-TOKEN: T-9876
Content-Type: application/json
```

Spring Security는 서버 WebSession의 토큰과 요청 헤더의 토큰을 비교한다.

```text
세션 S-1234에 저장된 토큰: T-9876
요청 헤더의 토큰:          T-9876

일치   → 요청 허용
불일치 → 403 Forbidden
```

CSRF 토큰은 로그인 자격 증명이 아니다. Spring Security가 만든 임의의 요청 검증 값이며, 로그인 전 익명 세션에도 존재할 수 있다.

---

## 구현 지도 — 단계 진행 상황을 확인하는 표

| 구현 단계 | 실제로 만드는 것 | 구현하면서 배우는 것 |
|---|---|---|
| 1 | `CsrfTokenResponse.java` | DTO, `record`, 토큰 응답 구조 |
| 2 | `CsrfController.java` | 토큰 발급 API, `ServerWebExchange`, `Mono` |
| 3 | `SecurityConfig.java` 수정 | `CsrfWebFilter`, 세션 토큰 저장소, `permitAll` |
| 4 | `frontend/src/shared/csrf.ts` | 탭 메모리, 타입 가드, 동시 요청 공유 |
| 5 | `frontend/src/shared/http.ts` 수정 | 안전한 메서드, 공통 `apiFetch` |
| 6 | `frontend/src/auth/api/authApi.ts` 수정 | 인증 요청에 CSRF 자동 적용 |
| 7 | `frontend/src/auth/hooks/useAuth.ts` 수정 | 세션 생명주기와 토큰 갱신 |
| 8 | `frontend/src/paper/api/paperApi.ts` 수정 | 주문·취소·레버리지 요청 보호 |
| 9 | `build.gradle` 수정 | Spring Security 테스트 지원 |
| 10 | `CsrfSecurityTest.java` | 성공·실패 경로 자동 검증 |
| 11 | `README.md` 수정 | 웹 사용 방법 문서화 |
| 12 | `mobile/REACT_NATIVE_STUDY_PLAN.md` 수정 | 모바일 후속 구현 계획 반영 |

로그인·주문 컨트롤러 자체는 수정하지 않는다. `CsrfWebFilter`가 컨트롤러보다 먼저 요청을 검사하기 때문이다.

---

# 백엔드 구현

## 구현 1단계 — CSRF 응답 DTO를 만든다

### 이번 단계에서 만드는 것

새 파일:

```text
src/main/java/com/example/futurespapertrading/auth/dto/CsrfTokenResponse.java
```

### 구현 코드

```java
package com.example.futurespapertrading.auth.dto;

// Spring Security가 만든 CSRF 토큰을 클라이언트에 JSON으로 전달하는 응답 DTO다.
// 응답 JSON 예시:
// {
//   "headerName": "X-CSRF-TOKEN",
//   "token": "abc123"
// }
public record CsrfTokenResponse(
        String headerName,
        String token
) {
}
```

### 방금 만든 코드에서 배울 개념

- DTO는 계층 사이에서 데이터를 운반하는 객체다. 여기서는 서버의 CSRF 정보를 HTTP JSON 응답으로 운반한다.
- `record`는 생성자, 접근자, `equals`, `hashCode`, `toString`을 컴파일러가 만들어 주는 읽기 전용 데이터 표현이다.
- `headerName`까지 서버가 내려주므로 React가 헤더 이름을 하드코딩하지 않아도 된다.
- 토큰은 사용자를 증명하는 로그인 토큰이 아니다. 사용자 ID, 이메일, 비밀번호도 담지 않는다.

직렬화된 응답은 다음 모양이다.

```json
{
  "headerName": "X-CSRF-TOKEN",
  "token": "T-9876"
}
```

### 완료 기준

- [x] 정확한 패키지에 파일을 만들었다.
- [x] 필드가 `headerName`, `token` 두 개뿐이다.
- [ ] “인증 토큰”과 “CSRF 요청 검증 토큰”의 차이를 설명할 수 있다.

---

## 구현 2단계 — 현재 세션의 CSRF 토큰을 발급하는 API를 만든다

### 이번 단계에서 만드는 것

새 파일:

```text
src/main/java/com/example/futurespapertrading/auth/controller/CsrfController.java
```

### 구현 코드

```java
package com.example.futurespapertrading.auth.controller;

import com.example.futurespapertrading.auth.dto.CsrfTokenResponse;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// 브라우저가 React 정적 파일을 실행하면 사용자 컴퓨터의 RAM에 JavaScript 실행 메모리가 생긴다.
// React는 이 API에서 받은 CSRF 토큰을 그 메모리의 csrfState 변수에 저장하며, 새로고침하면 사라지므로 다시 받아야 한다.
//
// GET /api/auth/csrf 요청이 오면 현재 WebSession의 CSRF 토큰 정보를 JSON으로 응답하는 컨트롤러다.
// 응답 예시: {"headerName":"X-CSRF-TOKEN","token":"abc123"}
//
// 사용자가 API 요청을 보내면 Spring WebFlux가 그 요청 1건을 처리하기 위한 객체들을 만들고, 그 묶음이 ServerWebExchange다.
//
// 브라우저: GET /api/auth/csrf
//        ↓
// Spring WebFlux: 이 요청 전용 ServerWebExchange 생성
//        ↓
// CsrfWebFilter: exchange.attributes에 CSRF 관련 정보를 붙임
//        ↓
// CsrfController: 같은 exchange를 전달받아 attributes의 정보를 읽음
//        ↓
// 응답 완료: 이 exchange와 attributes는 사라짐
@RestController
@RequestMapping("/api/auth")
public class CsrfController {

    // 로그인 전에도 호출해야 하므로 SecurityConfig에서 이 경로는 permitAll로 공개한다.
    // 이 엔드포인트는 클라이언트가 이후 변경 요청의 헤더에 포함할
    // CSRF 토큰 정보(headerName, token)를 JSON으로 응답한다.
    @GetMapping("/csrf")
    public Mono<CsrfTokenResponse> csrf(ServerWebExchange exchange) {
        // 이 줄은 CSRF 토큰을 만들거나 WebSession에 저장하는 코드가 아니다.
        // CSRF가 활성화되면 CsrfWebFilter가 현재 요청에 등록한 Mono<CsrfToken>을 가져온다.
        // 이 Mono를 구독하면 CSRF 토큰 객체(CsrfToken)를 0개 또는 1개 전달한다.
        Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());

        if (csrfToken == null) {
            // 요청 속성 자체가 없으면 CSRF 필터가 꺼져 있거나 이 요청에 적용되지 않은 설정 오류다.
            return Mono.error(new IllegalStateException("CSRF 토큰 Mono가 요청 속성에 없습니다."));
        }

        // CsrfToken을 클라이언트용 응답 DTO로 변환하는 Mono를 만든다.
        // WebFlux가 반환된 Mono를 구독하면 토큰을 조회하고,
        // 토큰이 없을 때만 새 토큰 생성과 WebSession 저장도 수행된다.
        return csrfToken.map(token -> new CsrfTokenResponse(
                token.getHeaderName(),
                token.getToken()
        ));
    }

    // ── 홈페이지 진입부터 CSRF 토큰 응답까지의 전체 흐름 ──
    //
    // 1) 사용자가 브라우저에서 홈페이지를 열면 먼저 GET / 요청으로 React 앱을 받는다.
    //    단순히 GET / 요청을 했다는 이유만으로 WebSession이 반드시 시작되는 것은 아니다.
    //
    // 2) React 앱이 실행되면 POST·PUT·PATCH·DELETE 같은 상태 변경 요청에 사용할 토큰을 준비하기 위해
    //    GET /api/auth/csrf를 호출한다. GET은 조회 전용이므로 토큰 제출 대상에서 제외한다.
    //
    // 3) SESSION 쿠키가 있으면 기존 WebSession을 사용한다. 쿠키가 없으면 새 WebSession을 준비하고,
    //    CSRF 저장소가 토큰을 WebSession 속성에 넣을 때 실제 세션을 시작한다.
    //
    // 4) 새 세션을 시작한 경우 응답에 세션 ID와 CSRF 토큰 정보가 함께 담긴다.
    //    Set-Cookie: SESSION=S-1234
    //    {"headerName":"X-CSRF-TOKEN","token":"T-9876"}
    //
    // 5) 이 WebSession에는 CSRF 토큰만 있고 SecurityContext는 아직 없으므로 사용자는 비로그인 상태다.
    //    즉, 세션이 있다는 것과 로그인했다는 것은 서로 다른 의미다.
    //
    // 브라우저는 우리 서버로 요청할 때 SESSION 쿠키를 조건에 따라 자동으로 붙인다.
    // 그래서 악성 사이트가 우리 서버로 요청을 유도해도 로그인 세션 쿠키가 함께 전송될 수 있다.
    // 반면 CSRF 토큰은 브라우저가 자동으로 붙이지 않고, 우리 React 코드가 요청 헤더에 직접 넣는다.
    // 서버는 SESSION 쿠키와 CSRF 토큰이 모두 맞아야 변경 요청을 허용하므로,
    // 세션 쿠키만 있고 CSRF 토큰이 없는 악성 요청은 403으로 거부된다.
    //
    // 브라우저는 아래 쿠키 전체 정보로 전송 여부를 판단하고, 조건이 맞으면 요청 헤더에 이름과 값만 자동으로 붙인다.
    // 실제 요청 예시: Cookie: SESSION=S-1234
    //
    // 브라우저 쿠키 저장소에 들어 있는 쿠키 한 개의 정보:
    // 이름:        SESSION
    // 값:          S-1234
    // 대상 호스트: trading.example.com
    // Path:        /
    // SameSite:    Lax
    // HttpOnly:    true
    // Secure:      true 또는 false
    // 만료시간:     브라우저 종료 시 또는 지정된 시간
}
```

### 방금 만든 코드에서 배울 개념

- `ServerWebExchange`는 현재 WebFlux HTTP 요청과 응답, 세션, 속성을 함께 다루는 객체다.
- `CsrfWebFilter`는 토큰을 바로 만들지 않고 `Mono<CsrfToken>`을 요청 속성에 넣는다.
- 컨트롤러가 그 `Mono`를 반환 파이프라인에 연결해야 토큰 생성과 세션 저장이 실행된다. 이것이 리액티브 코드의 지연 실행이다.
- 익명 WebSession에 CSRF 토큰이 생겨도 로그인된 것은 아니다. 인증정보와 요청 검증 값은 역할이 다르다.

요청 흐름:

```text
GET /api/auth/csrf
        ↓
CsrfWebFilter가 현재 WebSession 확인
        ↓
세션이 없으면 익명 WebSession 준비
        ↓
CSRF 토큰 생성·세션 저장
        ↓
SESSION 쿠키 + 토큰 JSON 응답
```

### 완료 기준

- [x] `GET /api/auth/csrf` 메서드를 만들었다.
- [x] `Mono<CsrfToken>`을 `map`으로 `CsrfTokenResponse`에 변환한다.
- [ ] 이 단계만으로는 아직 필터가 꺼져 있어 API가 정상 발급되지 않는 이유를 안다.

---

## 구현 3단계 — Spring Security의 CSRF 필터를 켠다

### 이번 단계에서 만드는 것

수정 파일:

```text
src/main/java/com/example/futurespapertrading/auth/config/SecurityConfig.java
```

### 구현 코드

먼저 import를 추가한다.

```java
import org.springframework.security.web.server.csrf.WebSessionServerCsrfTokenRepository;
```

현재 비활성화 코드를 찾는다.

```java
.csrf(ServerHttpSecurity.CsrfSpec::disable)
```

다음 설정으로 교체한다.

```java
.csrf(csrf -> csrf
        .csrfTokenRepository(new WebSessionServerCsrfTokenRepository())
)
```

`authorizeExchange`에서 토큰 발급 API를 공개한다.

```java
.authorizeExchange(ex -> ex
        .pathMatchers("/", "/index.html", "/assets/**", "/*.ico", "/*.png", "/*.svg").permitAll()
        .pathMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
        .pathMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
        .pathMatchers("/api/binance-futures/**").permitAll()
        .anyExchange().authenticated())
```

기존 주석의 “CSRF off” 설명도 현재 동작에 맞게 바꾼다.

```java
// 세션 인증을 사용하므로 변경 요청은 WebSession에 저장된 CSRF 토큰으로 검증한다.
// 폼 로그인·HTTP Basic·기본 로그아웃은 끄고 커스텀 JSON 인증 API를 사용한다.
```

### 방금 만든 코드에서 배울 개념

`WebSessionServerCsrfTokenRepository`는 다음 역할을 한다.

```text
CsrfWebFilter
├─ CSRF 토큰 생성
├─ WebSession에 기대하는 토큰 저장
├─ POST·PUT·DELETE·PATCH 요청의 토큰 확인
└─ 누락·불일치 요청을 403으로 차단
```

`permitAll`과 CSRF 검사는 서로 다른 질문에 답한다.

```text
permitAll
→ 로그인하지 않아도 이 경로에 접근할 수 있는가?

CSRF 검사
→ 변경 요청이 현재 세션에 맞는 토큰을 보냈는가?
```

따라서 로그인과 회원가입이 `permitAll`이어도 `POST` 요청이므로 CSRF 토큰이 필요하다.

| 요청 | 기능 | CSRF |
|---|---|---|
| `GET /api/auth/csrf` | 토큰 발급 | 불필요 |
| `POST /api/auth/signup` | 회원가입 | 필요 |
| `POST /api/auth/login` | 로그인 | 필요 |
| `POST /api/auth/logout` | 로그아웃 | 필요 |
| `GET /api/auth/me` | 사용자 조회 | 불필요 |
| `POST /api/paper/orders` | 주문 생성 | 필요 |
| `GET /api/paper/orders` | 주문 조회 | 불필요 |
| `DELETE /api/paper/orders/{id}` | 주문 취소 | 필요 |
| `GET /api/paper/account` | 계좌 조회 | 불필요 |
| `GET /api/paper/fills` | 체결 조회 | 불필요 |
| `PUT /api/paper/account/leverage` | 레버리지 변경 | 필요 |
| 시세·SSE `GET` | 공개 시세 | 불필요 |

### 완료 기준

- [x] `.csrf(...disable)`을 제거했다.
- [x] WebSession 토큰 저장소를 설정했다.
- [x] `GET /api/auth/csrf`를 `permitAll`로 공개했다.
- [x] 아직 React 연결이 끝나지 않았으므로 애플리케이션 실행은 8단계 뒤로 미룬다.

---

# React 웹 구현

## 구현 4단계 — CSRF 토큰을 탭 메모리에서 관리한다

### 이번 단계에서 만드는 것

새 파일:

```text
frontend/src/shared/csrf.ts
```

### 구현 코드

```ts
// 현재 브라우저 탭이 사용하는 CSRF 헤더 이름과 토큰 값이다.
export type CsrfState = {
  headerName: string;
  token: string;
};

// 현재 탭의 JavaScript 실행 메모리에만 존재한다.
let csrfState: CsrfState | null = null;

// 동시에 여러 요청이 토큰을 요구해도 /api/auth/csrf를 한 번만 호출하게 한다.
let csrfLoadPromise: Promise<CsrfState> | null = null;

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

// 로그인처럼 세션 상태가 바뀐 뒤 최신 토큰을 다시 받는다.
export async function refreshCsrfToken(): Promise<CsrfState> {
  csrfState = null;
  return ensureCsrfToken();
}

// 로그아웃·세션 만료 시 현재 탭이 기억한 토큰을 제거한다.
export function clearCsrfToken(): void {
  csrfState = null;
}
```

### 방금 만든 코드에서 배울 개념

- 모듈 변수는 현재 탭의 JavaScript 메모리에만 남는다. 새로고침하거나 탭을 닫으면 사라진다.
- 토큰을 `localStorage`에 영구 저장하지 않는다. 새 실행 환경에서는 현재 세션의 토큰을 다시 받는다.
- `isCsrfState`는 서버 JSON이 기대한 모양인지 런타임에 검사하는 TypeScript 타입 가드다.
- `csrfLoadPromise`는 여러 컴포넌트가 동시에 토큰을 요구할 때 같은 네트워크 요청을 공유한다.
- `ensure`, `refresh`, `clear`는 각각 준비, 재발급, 폐기라는 생명주기를 표현한다.

### 완료 기준

- [x] 토큰 값을 쿠키나 `localStorage`가 아닌 메모리에 보관한다.
- [x] 동시에 호출해도 `/api/auth/csrf` 요청 하나를 공유한다.
- [x] 갱신 함수와 제거 함수가 있다.

---

## 구현 5단계 — 세션 쿠키와 CSRF 헤더를 자동으로 붙이는 `apiFetch`를 만든다

### 이번 단계에서 만드는 것

수정 파일:

```text
frontend/src/shared/http.ts
```

### 구현 코드

파일 맨 위에 import를 추가한다.

```ts
import { ensureCsrfToken } from './csrf';
```

안전한 HTTP 메서드 목록과 `apiFetch`를 추가한다. 기존 `HttpError`, `toHttpError`는 유지한다.

```ts
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

// 우리 Spring API 전용 fetch 함수다.
// 세션 쿠키를 항상 포함하고, 데이터를 변경하는 요청에는 CSRF 헤더를 자동으로 붙인다.
export async function apiFetch(
  input: RequestInfo | URL,
  init: RequestInit = {},
): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);

  if (!SAFE_METHODS.has(method)) {
    const csrf = await ensureCsrfToken();
    headers.set(csrf.headerName, csrf.token);
  }

  return fetch(input, {
    ...init,
    headers,
    credentials: 'include',
  });
}
```

### 방금 만든 코드에서 배울 개념

```text
apiFetch(..., { method: 'GET' })
→ SESSION 쿠키만 전송

apiFetch(..., { method: 'POST' })
→ SESSION 쿠키 + X-CSRF-TOKEN 전송
```

- `GET`, `HEAD`, `OPTIONS`, `TRACE`는 서버 상태를 변경하지 않는 안전한 메서드로 취급한다.
- `Headers` 객체로 기존 헤더를 보존하면서 CSRF 헤더만 추가한다.
- `credentials: 'include'`가 쿠키를 보내고 받을 수 있게 한다. CSRF 헤더와 세션 쿠키가 같은 요청에 있어야 한다.
- 공통 함수 하나에 정책을 모으면 로그인, 주문, 취소마다 헤더 코드를 반복하지 않아도 된다.
- `apiFetch`는 우리 Spring API에만 사용한다. Binance 외부 요청은 기존 `fetch`를 유지한다.
- 변경 요청이 `403`이라고 자동 재시도하지 않는다. 응답만 유실된 경우 중복 주문이 생길 수 있다.

### 완료 기준

- [x] 안전하지 않은 메서드에만 CSRF 헤더를 추가한다.
- [x] 모든 내부 API 요청에 세션 쿠키를 포함할 수 있다.
- [x] 기존 오류 변환 코드가 그대로 남아 있다.

---

## 구현 6단계 — 인증 API를 `apiFetch`에 연결한다

### 이번 단계에서 만드는 것

수정 파일:

```text
frontend/src/auth/api/authApi.ts
```

### 구현 코드

import를 바꾼다.

```ts
import { apiFetch, toHttpError } from '../../shared/http';
```

회원가입, 로그인, 로그아웃, 현재 사용자 요청의 직접 `fetch`를 다음처럼 교체한다.

```ts
// 회원가입
const res = await apiFetch('/api/auth/signup', {
  method: 'POST',
  headers: JSON_HEADERS,
  body: JSON.stringify({ email, password, displayName }),
});

// 로그인
const res = await apiFetch('/api/auth/login', {
  method: 'POST',
  headers: JSON_HEADERS,
  body: JSON.stringify({ email, password }),
});

// 로그아웃
const res = await apiFetch('/api/auth/logout', {
  method: 'POST',
});

// 현재 사용자
const res = await apiFetch('/api/auth/me');
```

각 함수에 있던 중복 `credentials: 'include'`는 제거한다.

### 방금 만든 코드에서 배울 개념

- 회원가입과 로그인은 공개 API지만 `POST`이므로 CSRF 검사가 필요하다.
- 로그인 허용 여부인 인가와, 요청 위조 여부인 CSRF는 별도 보안 관문이다.
- API 함수는 “어떤 URL에 어떤 데이터로 요청하는가”만 담당하고, 쿠키·CSRF 정책은 공통 함수에 위임한다.
- `GET /api/auth/me`에는 CSRF 헤더가 필요 없지만 일관된 쿠키 처리를 위해 `apiFetch`를 사용한다.

### 완료 기준

- [x] `authApi.ts`의 네 요청이 모두 `apiFetch`를 사용한다.
- [x] 개별 `credentials: 'include'` 중복을 제거했다.
- [ ] 로그인 요청에도 CSRF가 필요한 이유를 설명할 수 있다.

---

## 구현 7단계 — 인증 생명주기에 CSRF 준비·갱신·제거를 연결한다

### 이번 단계에서 만드는 것

수정 파일:

```text
frontend/src/auth/hooks/useAuth.ts
```

### 구현 코드

import를 추가한다.

```ts
import {
  clearCsrfToken,
  ensureCsrfToken,
  refreshCsrfToken,
} from '../../shared/csrf';
```

앱 시작 시 토큰을 먼저 준비한 뒤 현재 사용자를 조회한다.

```ts
useEffect(() => {
  let cancelled = false;

  const initializeAuth = async () => {
    try {
      await ensureCsrfToken();
      const currentUser = await authApi.fetchMe();

      if (!cancelled) {
        setUser(currentUser);
        setError(null);
      }
    } catch (err) {
      if (!cancelled) {
        setUser(null);
        setError(err instanceof Error ? err.message : '로그인 상태를 확인하지 못했습니다.');
      }
    } finally {
      if (!cancelled) setLoading(false);
    }
  };

  void initializeAuth();

  return () => {
    cancelled = true;
  };
}, []);
```

로그인 성공 뒤 현재 세션의 토큰을 다시 받는다.

```ts
await authApi.login(email, password);
await refreshCsrfToken();
const authenticatedUser = await authApi.fetchMe();
```

회원가입 후 자동 로그인 흐름도 같은 순서로 바꾼다.

```ts
await authApi.signup(email, password, displayName);
await authApi.login(email, password);
await refreshCsrfToken();
const authenticatedUser = await authApi.fetchMe();
```

로그아웃 성공 뒤 메모리의 토큰을 제거한다.

```ts
await authApi.logout();
clearCsrfToken();
setUser(null);
```

세션 만료 처리에도 제거를 연결한다.

```ts
const expireSession = useCallback(() => {
  clearCsrfToken();
  setUser(null);
  setError('로그인 세션이 만료되었습니다. 다시 로그인해주세요.');
}, []);
```

### 방금 만든 코드에서 배울 개념

- 앱 시작 때 `ensureCsrfToken()`과 `fetchMe()`를 병렬 실행하지 않는다. 쿠키가 없는 두 요청이 서로 다른 익명 세션 응답을 만들 수 있기 때문이다.
- `WebSessionServerSecurityContextRepository`는 로그인 정보를 저장할 때 세션 고정 공격 방지를 위해 세션 ID를 바꾼다. 그래서 로그인 후 현재 세션의 토큰을 다시 준비한다.
- 로그아웃 요청 자체는 아직 로그인 세션을 사용하므로 먼저 전송하고, 성공한 뒤 토큰을 제거한다.
- 화면의 `user` 상태, 서버의 WebSession, React 메모리의 CSRF 토큰은 서로 다른 상태이므로 생명주기를 명시적으로 맞춘다.

### 완료 기준

- [ ] 앱 시작은 CSRF 준비 후 사용자 조회 순서다.
- [ ] 로그인과 자동 로그인 뒤 토큰을 갱신한다.
- [ ] 로그아웃과 세션 만료 때 토큰을 제거한다.

---

## 구현 8단계 — 거래 API를 `apiFetch`에 연결한다

### 이번 단계에서 만드는 것

수정 파일:

```text
frontend/src/paper/api/paperApi.ts
```

### 구현 코드

import를 바꾼다.

```ts
import { apiFetch, toHttpError } from '../../shared/http';
```

각 요청을 교체한다.

```ts
// 주문 생성
const res = await apiFetch('/api/paper/orders', {
  method: 'POST',
  headers: JSON_HEADERS,
  body: JSON.stringify({ symbol: 'BTCUSDT', ...input }),
});

// 주문 목록
const res = await apiFetch('/api/paper/orders');

// 주문 취소
const res = await apiFetch(`/api/paper/orders/${id}`, {
  method: 'DELETE',
});

// 계좌 조회
const res = await apiFetch('/api/paper/account');

// 체결 조회
const res = await apiFetch('/api/paper/fills');

// 레버리지 변경
const res = await apiFetch('/api/paper/account/leverage', {
  method: 'PUT',
  headers: JSON_HEADERS,
  body: JSON.stringify({ leverage }),
});
```

개별 `credentials: 'include'`는 제거한다.

### 방금 만든 코드에서 배울 개념

- `POST`, `DELETE`, `PUT`은 서버 상태를 바꾸므로 CSRF 헤더가 자동 추가된다.
- `GET`은 조회만 하므로 세션 쿠키는 보내지만 CSRF 헤더는 붙이지 않는다.
- CSRF 검사는 컨트롤러보다 먼저 실행된다. 토큰이 없으면 주문 서비스와 DB 로직까지 도달하지 않는다.
- 공통 클라이언트를 사용하면 새 변경 API를 추가할 때 CSRF 헤더를 빼먹을 가능성이 줄어든다.

### 완료 기준

- [ ] `paperApi.ts`의 모든 Spring API 요청이 `apiFetch`를 사용한다.
- [ ] 주문, 취소, 레버리지 변경에 CSRF 헤더가 자동으로 붙는다.
- [ ] 1~8단계 구현이 모두 끝났으므로 백엔드와 React를 함께 실행할 준비가 됐다.

---

# 자동 테스트 구현

## 구현 9단계 — Spring Security 테스트 도구를 추가한다

### 이번 단계에서 만드는 것

수정 파일:

```text
build.gradle
```

### 구현 코드

`dependencies`에 다음 한 줄을 추가한다.

```gradle
testImplementation 'org.springframework.security:spring-security-test'
```

### 방금 만든 코드에서 배울 개념

- `testImplementation` 의존성은 테스트 컴파일과 실행에만 사용되고 운영 애플리케이션에는 포함되지 않는다.
- `spring-security-test`는 WebTestClient 요청에 가짜 인증 사용자와 CSRF 토큰을 붙이는 도구를 제공한다.
- “토큰을 직접 문자열로 만들어 넣는 테스트”보다 Spring의 테스트 도구를 사용해야 실제 필터 계약에 맞는 요청을 만들 수 있다.

### 완료 기준

- [ ] 의존성을 `implementation`이 아니라 `testImplementation`으로 추가했다.
- [ ] Gradle 동기화에서 의존성 오류가 없다.

---

## 구현 10단계 — CSRF 성공·실패 경로를 자동 테스트한다

### 이번 단계에서 만드는 것

새 파일:

```text
src/test/java/com/example/futurespapertrading/auth/CsrfSecurityTest.java
```

### 구현 코드

현재 프로젝트의 Spring Boot 4 WebFlux 테스트 구조에 맞춘 통합 테스트를 작성한다.

```java
package com.example.futurespapertrading.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.example.futurespapertrading.market.stream.BinanceFuturesRawDepthStreamer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(properties = {
        "spring.r2dbc.url=r2dbc:h2:mem:///csrf_security_test?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql"
})
@AutoConfigureWebTestClient
class CsrfSecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private BinanceFuturesRawDepthStreamer binanceFuturesRawDepthStreamer;

    @Test
    void csrfTokenEndpointReturnsHeaderNameAndToken() {
        webTestClient.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.headerName").isEqualTo("X-CSRF-TOKEN")
                .jsonPath("$.token").isNotEmpty();
    }

    @Test
    void loginWithoutCsrfIsForbidden() {
        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"missing@example.com","password":"wrong-password"}
                        """)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void loginWithCsrfPassesCsrfFilterAndReachesAuthentication() {
        webTestClient.mutateWith(csrf())
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"missing@example.com","password":"wrong-password"}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void authenticatedOrderWithoutCsrfIsForbidden() {
        webTestClient.mutateWith(mockUser("missing@example.com"))
                .post()
                .uri("/api/paper/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"symbol":"BTCUSDT","side":"BUY","type":"MARKET","quantity":0.001}
                        """)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void authenticatedOrderWithCsrfPassesCsrfFilter() {
        HttpStatusCode status = webTestClient
                .mutateWith(mockUser("missing@example.com"))
                .mutateWith(csrf())
                .post()
                .uri("/api/paper/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"symbol":"BTCUSDT","side":"BUY","type":"MARKET","quantity":0.001}
                        """)
                .exchange()
                .returnResult(Void.class)
                .getStatus();

        assertThat(status).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void safeGetDoesNotRequireCsrf() {
        HttpStatusCode status = webTestClient
                .mutateWith(mockUser("missing@example.com"))
                .get()
                .uri("/api/paper/account")
                .exchange()
                .returnResult(Void.class)
                .getStatus();

        assertThat(status).isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
```

### 방금 만든 코드에서 배울 개념

- `403` 테스트는 토큰이 없거나 틀렸을 때 필터가 요청을 거부하는지 확인한다.
- 잘못된 계정 + CSRF 요청이 `401`이면 CSRF 필터는 통과했고 실제 인증 로직까지 도달했다는 뜻이다.
- `mockUser`는 인증 조건을 만들고 `csrf()`는 CSRF 조건을 만든다. 둘은 서로 대체할 수 없다.
- 주문 성공 자체는 주문 도메인 테스트의 책임이다. 여기서는 응답이 `403`이 아닌지 확인해 CSRF 필터 통과 여부만 검사한다.
- 안전한 `GET`에는 CSRF가 없어도 되지만 보호 경로이므로 인증은 필요하다.

### 완료 기준

- [ ] 토큰 발급 API 응답을 검사한다.
- [ ] CSRF 없는 로그인과 주문이 `403`인지 검사한다.
- [ ] CSRF가 있으면 필터를 통과하는지 검사한다.
- [ ] `GET` 요청은 CSRF 없이 필터를 통과하는지 검사한다.

---

# 사용 문서 구현

## 구현 11단계 — README에 CSRF 사용 방법을 기록한다

### 이번 단계에서 만드는 것

수정 파일:

```text
README.md
```

### 구현 코드

API 표에 토큰 발급 API를 추가하고 변경 요청의 요구사항을 표시한다.

```markdown
| GET | `/api/auth/csrf` | – | 현재 세션의 CSRF 헤더 이름과 토큰 발급 |
| POST | `/api/auth/signup` | CSRF | 회원가입 |
| POST | `/api/auth/login` | CSRF | 로그인 (세션 발급) |
| POST | `/api/auth/logout` | 세션 + CSRF | 로그아웃 |
| POST | `/api/paper/orders` | 세션 + CSRF | 주문 생성 (시장가/지정가) |
| DELETE | `/api/paper/orders/{id}` | 세션 + CSRF | 대기 지정가 주문 취소 |
| PUT | `/api/paper/account/leverage` | 세션 + CSRF | 레버리지 변경 |
```

API 표 아래에 다음 설명을 추가한다.

```markdown
### 세션 인증과 CSRF 요청 순서

1. 앱 시작 시 `GET /api/auth/csrf`를 호출해 `SESSION` 쿠키와 CSRF 토큰을 준비한다.
2. `POST`, `PUT`, `PATCH`, `DELETE` 요청에는 응답으로 받은 `headerName`과 `token`을 헤더로 보낸다.
3. 로그인 성공 뒤 세션 ID가 바뀌므로 현재 세션의 CSRF 토큰을 다시 받는다.
4. 로그아웃과 세션 만료 뒤에는 클라이언트 메모리의 토큰을 제거한다.

CSRF 토큰과 `SESSION` 값은 로그, URL, README 예제의 실제 값으로 남기지 않는다.
```

`SecurityConfig.java` 설명의 “csrf를 끈다” 문구도 “WebSession 기반 CSRF를 검증한다”로 수정한다.

### 방금 만든 문서에서 배울 개념

- 보안 기능은 코드만 켜는 것으로 끝나지 않는다. API 사용자가 토큰 발급과 갱신 순서를 알아야 올바르게 호출할 수 있다.
- 조회 API의 “세션 필요”와 변경 API의 “세션 + CSRF 필요”를 구분해 문서화한다.
- 실제 토큰 값을 예제나 로그에 남기지 않고 자리표시자만 사용한다.

### 완료 기준

- [ ] API 표에 `/api/auth/csrf`가 있다.
- [ ] 변경 요청에 CSRF가 필요하다고 표시했다.
- [ ] 기존 “CSRF off” 설명을 현재 구현에 맞게 고쳤다.

---

## 구현 12단계 — 모바일 인증 계획에 같은 CSRF 생명주기를 반영한다

### 이번 단계에서 만드는 것

수정 파일:

```text
mobile/REACT_NATIVE_STUDY_PLAN.md
```

### 구현 코드

모바일 학습 계획의 `5단계. 로그인·회원가입과 인증 상태를 구현한다`에 다음 항목을 추가한다.

````markdown
### 세션 CSRF도 함께 구현한다

- 앱 시작 시 `GET /api/auth/csrf`를 먼저 호출한다.
- 응답의 CSRF 토큰은 React Native JavaScript 메모리에 저장한다.
- 로그인·회원가입·로그아웃 요청에 서버가 알려 준 CSRF 헤더를 추가한다.
- 로그인 뒤 현재 세션의 CSRF 토큰을 다시 받고, 로그아웃·세션 만료 뒤에는 제거한다.
- `SESSION` 쿠키와 CSRF 토큰이 같은 서버 세션에 연결되는지 실기기에서 확인한다.

예정 파일:

```text
mobile/src/api/csrf.ts
mobile/src/api/client.ts
mobile/src/api/authApi.ts
mobile/src/features/auth/AuthProvider.tsx
mobile/src/api/paperApi.ts
```

현재 모바일은 1단계이므로 파일은 미리 만들지 않고 모바일 5단계에서 구현한다.
````

### 방금 만든 문서에서 배울 개념

- 모바일도 서버의 세션 쿠키를 사용한다면 웹과 같은 CSRF 생명주기를 고려해야 한다.
- 다만 모바일의 현재 학습 단계보다 앞선 코드를 미리 만들지는 않는다. 지금은 후속 구현 위치와 순서만 확정한다.
- 웹의 모듈 이름과 모바일의 API 계층 이름을 비슷하게 두면 같은 보안 흐름을 비교하며 학습하기 쉽다.

### 완료 기준

- [ ] 모바일 5단계에 토큰 준비·갱신·제거 흐름이 있다.
- [ ] 예정 파일 위치가 적혀 있다.
- [ ] 현재 모바일 단계에서는 실제 모바일 코드를 만들지 않는다고 명시했다.

---

# 구현 완료 후 검증 — 별도 구현 단계가 아니다

## 자동 빌드 검사

프로젝트 루트에서 백엔드 테스트를 실행한다.

```powershell
.\gradlew.bat test
```

React 빌드를 실행한다.

```powershell
cd frontend
npm run build
```

두 명령이 모두 성공한 뒤 브라우저 검증으로 넘어간다.

## 브라우저 Network 탭 검사

앱 최초 실행:

```text
GET /api/auth/csrf
→ 200
→ 응답 JSON에 headerName, token 존재
→ 첫 세션이면 Set-Cookie: SESSION=... 존재 가능
```

로그인:

```http
POST /api/auth/login
Cookie: SESSION=...
X-CSRF-TOKEN: ...
```

주문:

```http
POST /api/paper/orders
Cookie: SESSION=...
X-CSRF-TOKEN: ...
Content-Type: application/json
```

실패 경로는 자동 테스트에서 확인한다.

```text
POST·PUT·DELETE + 토큰 없음
→ 403 Forbidden
→ 컨트롤러 비즈니스 로직 실행 안 됨
```

실제 주문 API를 대상으로 CSRF 실패 요청을 자동 재시도하지 않는다.

---

# 완성 흐름 복습

## 앱 시작

```text
React 실행
  ↓
GET /api/auth/csrf
  ↓
브라우저: SESSION 쿠키 저장
React: CSRF 토큰 메모리 저장
  ↓
GET /api/auth/me
  ↓
기존 로그인 상태 복원 또는 비로그인 표시
```

## 로그인

```text
POST /api/auth/login
├─ SESSION 쿠키: 브라우저가 자동 첨부
├─ CSRF 헤더: apiFetch가 자동 첨부
└─ 이메일·비밀번호: JSON 본문
  ↓
Spring CSRF 검사
  ↓
이메일·비밀번호 인증
  ↓
SecurityContext를 WebSession에 저장
  ↓
CSRF 토큰 다시 요청
  ↓
GET /api/auth/me
```

## 주문

```text
매수·매도 버튼
  ↓
paperApi.createOrder()
  ↓
apiFetch()
  ├─ SESSION 쿠키 포함
  └─ X-CSRF-TOKEN 추가
  ↓
Spring CSRF 검사
  ↓
Spring 인증 검사
  ↓
주문 컨트롤러 실행
```

## 로그아웃

```text
POST /api/auth/logout
├─ SESSION 쿠키
└─ X-CSRF-TOKEN
  ↓
서버 WebSession 무효화
  ↓
React 사용자 상태 제거
React CSRF 메모리 제거
```

---

# 전체 완료 체크리스트

## 백엔드

- [x] 1단계 `CsrfTokenResponse.java`
- [x] 2단계 `CsrfController.java`
- [x] 3단계 WebSession CSRF 활성화와 토큰 API 공개

## React 웹

- [x] 4단계 `csrf.ts`
- [x] 5단계 `apiFetch`
- [x] 6단계 인증 API 연결
- [ ] 7단계 인증 생명주기 연결
- [ ] 8단계 거래 API 연결

## 테스트와 문서

- [ ] 9단계 테스트 의존성
- [ ] 10단계 CSRF 보안 테스트
- [ ] 11단계 README
- [ ] 12단계 모바일 계획
- [ ] `.\gradlew.bat test` 통과
- [ ] `frontend`의 `npm run build` 통과
- [ ] 브라우저 Network 탭 확인

---

# 구현 중 지킬 규칙

- CSRF 토큰이나 `SESSION` 값을 로그에 출력하지 않는다.
- 주문 요청을 CSRF 오류 뒤 자동 재시도하지 않는다.
- `apiFetch`는 우리 Spring API에만 사용한다.
- CSRF 토큰을 URL 쿼리 문자열에 넣지 않는다.
- CSRF 토큰을 `localStorage`에 영구 저장하지 않는다.
- 서버와 React 변경을 나눠서 배포하지 않는다.
- CORS를 모든 출처에 허용하지 않는다.
- CSRF는 XSS를 막지 않으므로 사용자 입력 출력과 스크립트 삽입 방어는 별도로 유지한다.

공식 참고:

- [Spring WebFlux CSRF](https://docs.spring.io/spring-security/reference/7.0/reactive/exploits/csrf.html)
- [Spring WebFlux CSRF 테스트](https://docs.spring.io/spring-security/reference/reactive/test/web/csrf.html)
