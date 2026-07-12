# CORS와 Vite 개발 프록시

이 문서는 React 개발 서버(Vite)가 `localhost:5173`에서 실행되고, Spring Boot 백엔드가 `localhost:8080`에서 실행되는 현재 프로젝트를 기준으로 설명합니다.

## 1. 먼저 알아둘 것: origin(출처)

브라우저에서 출처(origin)는 다음 세 가지가 모두 같아야 같은 출처입니다.

```text
프로토콜 + 호스트 + 포트
```

예를 들어 두 주소는 호스트가 모두 `localhost`여도 포트가 다르므로 서로 다른 출처입니다.

```text
http://localhost:5173  // Vite 개발 서버
http://localhost:8080  // Spring Boot 서버
```

## 2. CORS란?

CORS는 **Cross-Origin Resource Sharing**의 약자이며, 한국어로는 보통 **교차 출처 리소스 공유**라고 합니다.

브라우저는 보안상 웹페이지가 다른 출처의 서버에 자유롭게 요청하고 응답을 읽지 못하게 제한합니다. 이 기본 제한을 **Same-Origin Policy(동일 출처 정책)**라고 합니다. CORS는 다른 출처에 요청해야 할 때, 백엔드 서버가 특정 출처의 접근을 허용한다고 브라우저에 알려 주는 HTTP 규칙입니다.

따라서 CORS를 이해할 때는 먼저 “현재 화면의 출처와 API 요청의 출처가 같은가?”를 확인하면 됩니다.

### 1. 같은 출처라면 CORS가 필요 없습니다

CORS 없이 통신하려면, **현재 화면의 출처**와 **브라우저가 `fetch()`로 직접 요청하는 URL의 출처**가 같아야 합니다.

```text
현재 화면: https://example.com/products
```

```js
fetch('/api/users');
```

`/api/users`는 상대 경로이므로 브라우저는 이를 다음처럼 해석합니다.

```text
https://example.com/api/users
```

현재 화면과 `fetch()` 요청은 모두 `https://example.com`이므로 프로토콜(`https`), 호스트(`example.com`), 포트(`443`)가 같습니다. 따라서 같은 출처 요청이며 CORS 허용 헤더가 필요하지 않습니다.

### 2. 출처가 다르면 CORS 허용이 필요합니다

현재 화면의 출처와 `fetch()` 요청 대상의 프로토콜·호스트·포트 중 하나라도 다르면 교차 출처 요청입니다. 이 경우 백엔드는 어떤 출처를 허용할지 응답 헤더로 브라우저에 알려야 합니다.

예를 들어 Spring Boot가 다음 헤더를 응답에 넣으면 `http://localhost:5173`에서 온 요청을 허용한다는 뜻입니다.

```http
Access-Control-Allow-Origin: http://localhost:5173
```

### 3. CORS를 검사하고 차단하는 주체는 브라우저입니다

중요한 점은 CORS를 검사하고 차단하는 주체가 Spring Boot가 아니라 **브라우저**라는 것입니다. Spring Boot가 요청을 처리하고 응답을 브라우저에 반환했더라도, CORS 허용 헤더가 없으면 브라우저는 React 코드가 응답을 읽지 못하게 합니다.

### 4. preflight(사전 요청)가 필요한 경우도 있습니다

`POST` 요청에서 JSON을 보내거나 `Authorization` 헤더를 추가하는 경우처럼 일부 요청은 브라우저가 실제 API 요청 전에 `OPTIONS` 사전 요청을 보냅니다. 이 단계에서 CORS 허용을 받지 못하면 실제 API 요청이 Spring Boot Controller까지 도달하지 않을 수 있습니다.

### 5. 리다이렉트로 최종 출처가 바뀌는 경우를 주의합니다

처음 `fetch()` 요청이 같은 출처여도 서버가 다른 출처로 리다이렉트하면 최종 요청은 CORS 대상이 될 수 있습니다.

```text
https://example.com/api/users
  ↓ 302 리다이렉트
https://api.example.com/api/users
```

경로만 바뀌고 프로토콜·호스트·포트가 같으면 같은 출처이므로 CORS가 필요하지 않습니다.

## 3. 백엔드로 직접 요청할 때의 흐름

React 페이지가 `http://localhost:5173`에서 열려 있을 때 다음처럼 백엔드 주소를 직접 쓰면 교차 출처 요청입니다.

```js
fetch('http://localhost:8080/api/users');
```

```text
브라우저 (현재 페이지: localhost:5173)
  │  Origin: http://localhost:5173
  ▼
Spring Boot (localhost:8080)
  │  Access-Control-Allow-Origin: http://localhost:5173 가 필요한 응답
  ▼
브라우저
  └─ 허용 헤더가 없으면 React 코드에 CORS 오류를 발생시키고 응답 접근을 차단
```

`POST` 요청에서 JSON을 보내거나 `Authorization` 같은 사용자 정의 헤더를 추가하는 경우에는 실제 요청 전 브라우저가 `OPTIONS` 요청을 먼저 보낼 수 있습니다. 이를 **preflight(사전 요청)**라고 합니다.

```text
1. 브라우저 → Spring Boot: OPTIONS /api/users
2. Spring Boot → 브라우저: 허용할 출처, 메서드, 헤더를 응답
3. 허용되면 브라우저 → Spring Boot: 실제 POST /api/users
```

이 경우 Spring Boot는 `OPTIONS` 요청에도 적절한 CORS 응답을 제공해야 합니다.

## 4. 브라우저, React, Vite의 역할

React는 별도 서버에서 실행되는 프로그램이 아닙니다. 브라우저가 내려받은 React JavaScript 코드를 자신의 JavaScript 실행 엔진에서 실행합니다.

```text
브라우저 안
├─ HTML을 해석하는 기능
├─ DOM(실제 화면 구조)
├─ JavaScript 실행 엔진
│   ├─ JavaScript 코드 실행
│   ├─ Vite가 JavaScript로 변환한 TypeScript/TSX 코드 실행
│   └─ React 코드 실행
├─ 네트워크 기능
│   └─ fetch(), HTTP 요청, CORS 검사
└─ 화면 렌더링 기능
```

브라우저는 JavaScript만 실행할 수 있습니다. 따라서 TypeScript(`.ts`, `.tsx`)는 브라우저가 직접 실행하는 언어가 아닙니다. 개발 환경에서는 브라우저가 React 소스 파일을 요청할 때 Vite가 TypeScript와 JSX를 일반 JavaScript로 변환하여 전달합니다. TypeScript의 타입 정보는 이 과정에서 제거됩니다.

```text
브라우저 → Vite: /src/App.tsx 요청
Vite: TypeScript와 JSX를 JavaScript로 변환
Vite → 브라우저: 변환된 JavaScript 전달
브라우저의 JavaScript 실행 엔진: 변환된 코드와 React 코드 실행
```

즉, React는 화면을 계산하고 DOM 변경을 요청하는 JavaScript 라이브러리이고, 브라우저는 그 코드를 실행하고 실제 화면을 표시하는 환경입니다. Vite는 개발 중 소스 코드를 전달·변환하고 HMR(수정 내용을 빠르게 반영하는 기능)을 제공하는 개발 서버입니다.

## 5. Vite proxy를 사용할 때의 흐름

프론트엔드 코드에서는 백엔드의 전체 주소 대신 다음처럼 작성합니다.

```js
fetch('/api/users');
```

`/api/users`는 루트 기준 상대 URL입니다. 브라우저는 현재 페이지가 열린 출처를 자동으로 붙여 요청 URL을 완성합니다.

```text
현재 페이지: http://localhost:5173
fetch('/api/users')
→ http://localhost:5173/api/users
```

그 뒤 Vite의 개발 서버가 `vite.config.ts`의 `proxy` 설정을 보고 `/api`로 시작하는 요청을 Spring Boot로 전달합니다.

### 페이지 로딩부터 API 응답까지 전체 흐름

```text
0. 사용자가 http://localhost:5173 주소를 입력하거나 새로고침
   ↓

1. 브라우저 → Vite 개발 서버
   GET http://localhost:5173/
   ↓

2. Vite 개발 서버 → 브라우저
   index.html 응답
   ↓

3. 브라우저 → Vite 개발 서버
   React JavaScript, CSS 등의 파일 요청
   예) /src/main.jsx, /src/App.jsx
   ↓

4. 브라우저가 받은 React 코드를 실행하고 첫 화면을 그림
   ↓

5. API 요청 트리거 발생

   경우 A: 화면이 처음 그려진 후 useEffect 실행
   경우 B: 사용자가 “사용자 목록 조회” 버튼 클릭
   경우 C: 사용자가 검색어 입력 후 검색 버튼 클릭
   경우 D: 사용자가 React 화면에서 /users 같은 페이지로 이동
   ↓

6. React 코드의 useEffect 또는 이벤트 함수가 실행
   ↓

7. React 코드가 fetch('/api/users') 호출
   ↓

8. 브라우저가 상대 주소를 완전한 주소로 해석
   /api/users
   → http://localhost:5173/api/users
   ↓

9. 브라우저 → Vite 개발 서버
   GET http://localhost:5173/api/users
   ↓

10. Vite proxy가 /api 요청을 Spring Boot로 전달
   GET http://localhost:8080/api/users
   ↓

11. Spring Boot가 요청을 처리
    예) Controller → Service → Repository → DB 조회
    ↓

12. Spring Boot → Vite 개발 서버
    사용자 목록 등의 응답 전달
    ↓

13. Vite 개발 서버 → 브라우저
    Spring Boot 응답을 그대로 전달
    ↓

14. 브라우저가 응답 데이터를 React 코드에 전달
    ↓

15. React 코드가 응답 데이터를 state에 저장
    예) setUsers(users)
    ↓

16. React가 변경된 state를 기준으로 화면을 다시 그림
```

브라우저가 요청한 URL은 `http://localhost:5173/api/users`이므로 같은 출처 요청입니다. 따라서 이 개발 환경에서는 브라우저 CORS 검사가 필요하지 않습니다.

### 왜 Spring Boot 응답에 CORS 헤더가 필요하지 않을까?

브라우저가 Spring Boot(`localhost:8080`)에 직접 요청하는 것이 아니라 Vite(`localhost:5173`)에 요청하기 때문입니다. Vite가 받은 요청을 다시 Spring Boot로 전달하며, 이 두 통신은 서로 다른 연결입니다.

```text
브라우저 ── GET localhost:5173/api/users ──→ Vite
브라우저 ←────────── Vite의 응답 ──────────── Vite

Vite ── GET localhost:8080/api/users ──→ Spring Boot
Vite ←──────── Spring Boot의 응답 ──────── Spring Boot
```

Spring Boot의 JSON 응답은 먼저 Vite가 받고, Vite가 자신의 응답으로 브라우저에 전달합니다. 브라우저는 `localhost:8080`에 직접 연결하지 않았으므로 이를 교차 출처 요청으로 판단하지 않습니다. CORS는 브라우저 정책이므로 Vite와 Spring Boot 사이의 서버 간 통신에는 적용되지 않습니다.

반대로 아래처럼 브라우저가 Spring Boot에 직접 요청하면 CORS 허용 헤더가 필요합니다.

```js
fetch('http://localhost:8080/api/users');
```

Vite가 Spring Boot를 대신해 요청을 전달하므로, 현재 Vite 개발 서버는 **개발용 reverse proxy(역방향 프록시)** 역할도 합니다.

## 6. 현재 `vite.config.ts` 설정의 의미

```ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      configure: (proxy) => {
        proxy.on('proxyRes', (proxyRes) => {
          proxyRes.headers['x-accel-buffering'] = 'no';
        });
      },
    },
  },
}
```

| 설정 | 의미 |
| --- | --- |
| `'/api'` | `/api`로 시작하는 요청에만 프록시 규칙을 적용합니다. |
| `target` | 해당 요청을 전달할 실제 백엔드 주소입니다. |
| `changeOrigin: true` | Vite가 Spring Boot로 요청을 보낼 때 대상 서버에 맞게 `Host` 헤더를 변경합니다. 브라우저 주소창의 URL은 바뀌지 않습니다. |
| `configure` | 만들어진 프록시에 이벤트 처리를 추가합니다. |
| `proxyRes` | Spring Boot 응답을 Vite 프록시가 받은 시점의 이벤트입니다. |
| `x-accel-buffering: no` | SSE처럼 응답을 계속 전달해야 하는 상황에서 버퍼링하지 않도록 알리는 헤더입니다. 주로 Nginx가 인식합니다. |

## 7. URL 작성 방식의 차이

```js
// 현재 페이지의 origin을 사용합니다.
fetch('/api/users');
// 현재 페이지가 localhost:5173이면 → http://localhost:5173/api/users

// 현재 URL 경로를 기준으로 계산합니다.
fetch('api/users');

// 주소를 전부 지정합니다. localhost:8080은 Vite와 다른 출처입니다.
fetch('http://localhost:8080/api/users');
```

API 요청에는 현재 사이트의 루트부터 시작하는 `fetch('/api/...')` 형태를 주로 사용합니다. Vite proxy와 자연스럽게 연결되고, 개발·운영 환경의 백엔드 주소가 달라져도 React 코드를 바꿀 일이 줄어듭니다.

## 8. 운영 환경에서는?

`vite.config.ts`의 `server.proxy`는 `npm run dev`로 실행하는 **Vite 개발 서버에서만** 동작합니다. `npm run build`로 빌드한 뒤 운영 서버에 배포하면 Vite 개발 서버는 없습니다.

### 운영 배포의 핵심

Vite는 사라지지만, `/api` 요청을 Spring Boot에 전달하는 proxy 역할은 보통 Nginx 같은 운영 웹 서버가 대신 맡습니다. 따라서 React 코드의 상대 경로 요청은 그대로 유지할 수 있습니다.

```js
fetch('/api/users');
```

```text
개발 환경
브라우저 → Vite :5173 → Spring Boot :8080

운영 환경
브라우저 → Nginx :443 → Spring Boot :8080
```

운영 배포 전에는 Vite를 한 번 실행해 React의 TypeScript/JSX 코드를 브라우저용 JavaScript 파일로 빌드합니다.

```text
React 소스 코드 수정
  ↓ npm run build
dist 폴더에 HTML, JavaScript, CSS 생성
  ↓
Nginx 또는 Spring Boot에 dist 결과물 배포
```

Nginx를 사용하는 경우, Nginx는 빌드된 React 파일을 제공하고 `/api` 요청만 Spring Boot로 전달합니다.

```text
브라우저 → https://example.com/             → Nginx가 React 파일 반환
브라우저 → https://example.com/api/users    → Nginx가 Spring Boot로 전달
```

이 구조에서는 브라우저가 `example.com`에만 직접 요청하므로 CORS가 필요하지 않습니다. Vite proxy가 하던 개발용 중계 역할을 Nginx reverse proxy가 운영에서 맡는 것입니다.

또는 React의 빌드 결과물을 Spring Boot의 정적 파일 경로에 넣어 Spring Boot 하나가 화면 파일과 `/api`를 모두 제공할 수도 있습니다. 이 경우도 화면과 API의 출처가 같으므로 CORS가 필요하지 않습니다.

프론트엔드와 백엔드를 서로 다른 도메인에 별도로 배포하고 proxy를 두지 않는 경우에는, 브라우저가 백엔드에 직접 요청하므로 Spring Boot에서 허용할 origin을 명시적으로 CORS 설정해야 합니다.

따라서 Vite proxy는 CORS 보안 정책을 없애는 기능이 아니라, **개발 중에 같은 출처처럼 요청을 중계해 주는 기능**입니다. 운영에서는 Nginx, Spring Boot의 정적 파일 제공, 또는 별도 CORS 설정으로 같은 문제를 해결합니다.
