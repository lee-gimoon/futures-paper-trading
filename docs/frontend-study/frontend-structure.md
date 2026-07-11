# Frontend 구조 개요

이 문서는 이 프로젝트의 `frontend` 폴더 전체 구조와 각 영역의 역할을 파악하기 위한 안내서입니다.

현재 프론트엔드는 React, TypeScript, Vite, `lightweight-charts`로 구성되어 있으며 Spring Boot 백엔드 API를 호출합니다.

## 1. 전체 폴더 구조

```text
frontend/
├─ node_modules/                    설치된 npm 라이브러리 폴더
├─ src/                             실제 React + TypeScript 소스 코드
│  ├─ auth/                         인증 도메인
│  │  ├─ api/
│  │  │  └─ authApi.ts              인증 API 호출
│  │  ├─ components/
│  │  │  ├─ LoginForm.tsx           로그인 화면
│  │  │  └─ SignupForm.tsx          회원가입 화면
│  │  └─ hooks/
│  │     └─ useAuth.ts              인증 상태 관리
│  ├─ market/                       시세·호가·차트 도메인
│  │  ├─ api/
│  │  │  └─ binanceKline.ts         Binance 캔들 API
│  │  ├─ components/
│  │  │  ├─ CandleChart.tsx         캔들 차트
│  │  │  └─ OrderBook.tsx            호가창
│  │  ├─ engine/
│  │  │  └─ quote.ts                호가 계산 로직
│  │  └─ hooks/
│  │     └─ useOrderBookStream.ts   실시간 호가 SSE
│  ├─ paper/                        모의거래 도메인
│  │  ├─ api/
│  │  │  └─ paperApi.ts             주문·계좌·체결 API
│  │  ├─ components/
│  │  │  ├─ AccountSummary.tsx      계좌 요약
│  │  │  ├─ ClosePositionButton.tsx 포지션 종료
│  │  │  ├─ FillHistory.tsx         체결 내역
│  │  │  ├─ OrderForm.tsx           주문 입력
│  │  │  ├─ OrderList.tsx           주문 목록
│  │  │  └─ TradingPanel.tsx        거래 화면 조합
│  │  └─ hooks/
│  │     └─ useTrading.ts           거래 데이터 상태 관리
│  ├─ shared/
│  │  └─ types.ts                   공통 TypeScript 타입
│  ├─ App.tsx                       최상위 화면 조합
│  ├─ main.tsx                      React 시작점
│  └─ styles.css                    전역 CSS
├─ .gitignore                       Git 제외 규칙
├─ index.html                       브라우저 진입 HTML
├─ package.json                     프로젝트·의존성·명령어 정의
├─ package-lock.json                npm 의존성 버전 기록
├─ tsconfig.json                    애플리케이션 TypeScript 설정
├─ tsconfig.node.json               Vite 설정용 TypeScript 설정
└─ vite.config.ts                   Vite 서버·프록시 설정
```

`npm run build`를 실행하면 `dist/`가 생성될 수 있습니다. `dist/`는 소스 코드가 아니라 빌드 결과물이며, 삭제해도 다시 생성됩니다. 현재 Docker 빌드에서는 Docker 내부에서 생성되므로 로컬에 없어도 됩니다.

## 2. 폴더를 나눈 기준

먼저 기능·도메인으로 나누고, 각 도메인 안에서 역할을 세분화했습니다.

```text
auth, market, paper  기능·도메인
components            화면을 그리는 React 컴포넌트
api                   백엔드 또는 외부 API 통신
hooks                 React state와 effect를 관리하는 커스텀 훅
engine                React와 무관한 순수 계산·도메인 로직
shared                여러 도메인이 함께 사용하는 코드
```

Spring Boot의 계층 구조와 완전히 같지는 않지만, 역할을 기준으로 파일을 찾는다는 점은 비슷합니다.

```text
components → 화면
hooks      → 상태와 화면 흐름
api        → 외부 통신
engine     → 순수 계산
types      → 데이터 형태
```

## 3. 루트 설정 파일 역할

루트 파일들은 화면을 그리는 코드가 아닙니다. 프론트엔드를 어떻게 설치하고, 실행하고, 검사하고, 빌드할지 정하는 개발 환경 파일입니다.

### `package.json` — 프로젝트 사용 설명서

프로젝트의 실행 명령어와 사용하는 라이브러리를 적어둔 파일입니다.

| 구분 | 역할 |
|---|---|
| `scripts` | `npm run ...`으로 실행할 명령어 |
| `dependencies` | 실제 애플리케이션 실행에 필요한 라이브러리 |
| `devDependencies` | 개발·검사·빌드에 필요한 라이브러리 |

현재 주요 명령어는 다음과 같습니다.

```text
npm run dev       개발 서버 실행
npm run build     TypeScript 검사 + 배포용 빌드
npm run preview   빌드 결과 확인
```

주요 라이브러리는 다음과 같습니다.

```text
react, react-dom       React 화면 구성
lightweight-charts     캔들 차트
typescript             TypeScript 검사
vite                   개발 서버·빌드
@vitejs/plugin-react   Vite에서 React 사용
@types/react           React 타입 정보
@types/react-dom       React DOM 타입 정보
```

### `package-lock.json` — 의존성 버전 기록

`package.json`에 적힌 라이브러리와 그 하위 라이브러리의 정확한 버전을 기록합니다.

```text
npm install 또는 npm ci
  ↓
package.json + package-lock.json
  ↓
node_modules 설치
```

팀원과 Docker가 같은 버전의 라이브러리를 설치할 수 있게 해주므로 직접 수정하거나 삭제하지 않는 것이 좋습니다.

### `index.html` — 브라우저의 시작 HTML

브라우저가 가장 먼저 읽는 HTML 파일입니다. React가 그려질 빈 공간인 `root`를 만들고 `main.tsx`를 불러옵니다.

```text
index.html
  ↓
main.tsx
  ↓
App.tsx
  ↓
하위 컴포넌트
```

### `vite.config.ts` — 개발 서버 설정

Vite가 개발 서버를 실행하는 방법을 설정합니다.

- React 플러그인 등록
- `/api` 요청을 Spring Boot `localhost:8080`으로 전달
- 실시간 SSE 응답이 늦게 전달되지 않도록 프록시 설정

따라서 소스 코드에서는 다음처럼 간단히 요청할 수 있습니다.

```ts
fetch('/api/auth/me')
```

개발 중에는 Vite가 이 요청을 백엔드로 전달합니다.

```text
브라우저 → Vite(localhost:5173) → Spring Boot(localhost:8080)
```

### `tsconfig.json` — 애플리케이션 TypeScript 규칙

`src` 안의 React·TypeScript 코드를 어떻게 검사할지 정합니다.

- `strict: true`: 타입을 엄격하게 검사
- `jsx: react-jsx`: `.tsx`에서 JSX 사용
- `include: ["src"]`: `src`를 검사 대상으로 지정

### `tsconfig.node.json` — Vite 설정용 TypeScript 규칙

브라우저에서 실행되는 React 코드가 아니라 Node.js 환경의 `vite.config.ts`를 검사하기 위한 별도 설정입니다.

### `.gitignore` — Git에서 제외할 대상

Git에 저장하지 않을 파일과 폴더를 지정합니다.

```text
node_modules/       설치된 라이브러리
dist/               빌드 결과물
.vite/              Vite 캐시
*.log               개발 로그
```

`node_modules`는 로컬 실행에는 필요하지만 `npm install`로 다시 만들 수 있으므로 Git에는 저장하지 않습니다. 이 문서에서는 폴더 역할만 설명하고 내부 파일은 다루지 않습니다.

## 4. `src` 공통 파일

### `src/main.tsx`

React 애플리케이션의 진입점입니다.

1. HTML의 `root` 요소를 찾습니다.
2. React 루트를 생성합니다.
3. `App`을 렌더링합니다.
4. `styles.css`를 불러옵니다.
5. `React.StrictMode`를 적용합니다.

### `src/App.tsx`

전체 화면을 조합하는 최상위 컴포넌트입니다.

- 로그인 상태에 따라 화면을 분기합니다.
- 로그인·회원가입 폼을 열고 닫습니다.
- 실시간 호가 데이터를 구독합니다.
- 호가 가격 클릭을 주문 가격과 연결합니다.
- 차트, 호가창, 거래 패널을 배치합니다.

세부 API 구현보다 여러 도메인의 컴포넌트와 훅을 연결하는 역할이 중심입니다.

### `src/styles.css`

전체 레이아웃, 차트, 호가창, 거래 패널, 폼, 버튼, 주문 목록, 손익 색상 등의 전역 CSS입니다.

### `src/shared/types.ts`

백엔드 응답과 프론트엔드 데이터 구조를 TypeScript 타입으로 정의합니다.

| 타입 | 의미 |
|---|---|
| `OrderBookLevel` | 호가 한 단계의 가격·수량 |
| `OrderBookSnapshot` | 전체 호가 스냅샷 |
| `User` | 로그인 사용자 |
| `Order` | 주문 |
| `Position` | 현재 포지션 |
| `Portfolio` | 계좌 요약 |
| `Fill` | 체결 내역 |

여러 도메인에서 함께 사용하는 타입이므로 `shared`에 있습니다.
