# Vite 개발 서버와 Spring Boot 백엔드

이 문서는 현재 프로젝트에서 Vite 개발 서버와 Spring Boot 백엔드가 어떤 역할을 맡는지 설명합니다.

Vite는 개발할 때 사용하는 Node.js 기반 프론트엔드 개발 서버이자 빌드 도구입니다.
React, Vue 등의 코드를 브라우저에서 실행할 수 있는 JavaScript와 CSS로 변환하고, 개발 중에는 수정된 파일만 빠르게 반영하는 HMR(Hot Module Replacement)을 제공합니다.
또한 `/api`와 같은 특정 요청을 백엔드 서버로 전달하는 프록시 기능도 제공합니다.
개발 서버는 기본적으로 `localhost:5173`에서 실행되며, `vite build` 명령을 사용하면 프론트엔드 코드를 배포할 수 있는 정적 파일로 빌드합니다.

## 1. 개발 환경

로컬에서 프론트엔드와 백엔드를 실행하면 서버가 두 개 실행됩니다.

```text
브라우저
  ├─ localhost:5173  → Vite 개발 서버
  └─ localhost:8080  → Spring Boot 백엔드
```

### Vite 서버는 어떻게 실행되는가?

[`frontend/package.json`](../../frontend/package.json)의 `scripts`에 다음 명령이 정의되어 있습니다.

```json
"dev": "vite"
```

따라서 다음 명령을 실행하면:

```text
cd frontend
npm run dev
```

`vite` 명령이 실행되고 Vite 개발 서버가 시작됩니다.

Vite 개발 서버는 프론트엔드 파일을 브라우저에 제공하고, 개발에 필요한 처리를 담당합니다.

```text
브라우저
  ↓
Vite: localhost:5173
  ├─ index.html
  ├─ main.tsx
  ├─ App.tsx
  ├─ CSS
  └─ 이미지·기타 정적 파일
```

### Vite가 담당하는 일

- `index.html` 제공
- React 코드 제공
- TypeScript와 JSX 변환
- CSS 제공
- 코드 수정 시 브라우저 자동 갱신(HMR)
- `/api` 요청을 Spring Boot로 전달하는 프록시

즉, Vite는 프론트엔드 개발을 위한 서버입니다.

### Vite가 담당하지 않는 일

Vite는 다음과 같은 백엔드 업무를 처리하지 않습니다.

- 주문 생성·취소
- 로그인 검증
- 데이터베이스 접근
- 포지션 계산
- 거래 비즈니스 로직

이런 업무는 Spring Boot 백엔드가 담당합니다.

## 2. Spring Boot 백엔드

Spring Boot는 실제 애플리케이션의 서버 로직을 담당합니다.

```text
브라우저
  ↓
Spring Boot: localhost:8080
  ├─ Controller
  ├─ Service
  ├─ Repository
  └─ Database
```

현재 프로젝트에서 Spring Boot가 담당하는 주요 기능은 다음과 같습니다.

- 로그인·회원가입
- 주문 생성·취소
- 계좌 조회
- 데이터베이스 접근
- SSE 실시간 호가 제공
- 포지션·손익 등 거래 비즈니스 로직

## 3. `/api` 요청은 어떻게 전달되는가?

프론트엔드 코드에서는 백엔드 주소를 직접 적지 않고 다음처럼 요청합니다.

```ts
fetch('/api/auth/me')
```

개발 중에는 이 요청이 먼저 Vite 서버로 전송됩니다.

```text
브라우저
  → localhost:5173/api/auth/me
  → Vite가 localhost:8080/api/auth/me로 전달
  → Spring Boot가 요청 처리
```

이 전달 규칙은 [`frontend/vite.config.ts`](../../frontend/vite.config.ts)의 `server.proxy`에 설정되어 있습니다.

```ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
    },
  },
},
```

이 설정 덕분에 개발 중에도 프론트 코드에서는 다음처럼 간단한 상대 경로를 사용할 수 있습니다.

```ts
fetch('/api/paper/orders')
```

Vite가 실제 백엔드 주소를 대신 연결해줍니다.

## 4. 개발 환경의 전체 흐름

```text
브라우저
  │
  ├─ GET / ----------------------→ Vite
  │                                 └─ index.html, React 파일 제공
  │
  └─ GET /api/... ---------------→ Vite proxy
                                    └─ Spring Boot로 전달
                                        ├─ Controller
                                        ├─ Service
                                        ├─ Repository
                                        └─ Database
```

정리하면 개발 환경에서는 다음처럼 두 서버가 함께 동작합니다.

| 역할 | 서버 | 포트 |
|---|---|---:|
| 프론트엔드 파일 제공·개발 지원 | Vite | `5173` |
| API·인증·거래·DB 처리 | Spring Boot | `8080` |

## 5. Docker 실행 환경

Docker에서는 실행할 때 Vite 개발 서버를 사용하지 않습니다. Vite는 Docker 이미지 빌드 과정에서 프론트엔드를 빌드하는 용도로만 사용됩니다.

### Docker 빌드 단계

프로젝트의 [`Dockerfile`](../../Dockerfile)은 먼저 Node.js 이미지에서 프론트엔드를 빌드합니다.

```text
Node.js 빌드 단계
  ├─ frontend/package.json 복사
  ├─ npm ci
  ├─ frontend 소스 복사
  ├─ npm run build
  └─ dist 생성
```

`npm run build`가 끝나면 Docker 내부에 다음과 같은 결과물이 만들어집니다.

```text
/workspace/frontend/dist/
├─ index.html
└─ assets/
   ├─ index-xxxxx.js
   └─ index-xxxxx.css
```

### `dist`를 Spring Boot에 복사

Dockerfile의 다음 부분이 프론트 빌드 결과를 Spring Boot의 static 폴더로 복사합니다.

```dockerfile
COPY src/ ./src/
COPY --from=frontend-build /workspace/frontend/dist/ ./src/main/resources/static/
```

결과적으로 Docker 내부에서는 다음과 같은 구조가 됩니다.

```text
src/main/resources/static/
├─ index.html
└─ assets/
   ├─ index-xxxxx.js
   └─ index-xxxxx.css
```

그 다음 Spring Boot jar를 만들고, 최종 Docker 이미지에서는 Java 애플리케이션만 실행합니다.

```text
Spring Boot: localhost:8080
  ├─ /              → React index.html
  ├─ /assets/...    → React 빌드 파일
  └─ /api/...       → Spring Boot 백엔드 API
```

## 6. 개발 환경과 Docker 환경 비교

| 환경 | 프론트엔드 파일 제공 | 백엔드 로직 처리 |
|---|---|---|
| 로컬 개발 | Vite `:5173` | Spring Boot `:8080` |
| Docker 실행 | Spring Boot `:8080` | Spring Boot `:8080` |

로컬 개발에서는 Vite와 Spring Boot가 각각 실행됩니다.

```text
로컬 개발
프론트 파일 → Vite
API 처리   → Spring Boot
```

Docker 실행에서는 Vite가 실행되지 않습니다.

```text
Docker 실행
프론트 파일 → Spring Boot가 제공
API 처리   → Spring Boot가 처리
```

## 7. 핵심 정리

> Vite는 프론트엔드 개발용 서버입니다. React 파일을 제공하고 TypeScript·JSX를 변환하며, 개발 중 `/api` 요청을 Spring Boot로 전달합니다.

> Spring Boot는 실제 백엔드 서버입니다. 인증, 주문, 데이터베이스, 거래 로직, 실시간 호가 API를 처리합니다.

> Docker 배포에서는 Vite가 실행되지 않습니다. Vite가 빌드한 React 파일을 Spring Boot의 static 폴더에 넣고, Spring Boot가 프론트엔드와 백엔드를 함께 제공합니다.
