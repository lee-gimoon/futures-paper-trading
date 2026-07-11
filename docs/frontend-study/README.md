# Frontend 구조와 파일 역할

이 문서는 이 프로젝트의 `frontend` 폴더 전체를 이해하기 위한 안내서입니다.

현재 프론트엔드는 React, TypeScript, Vite, `lightweight-charts`로 구성되어 있으며 Spring Boot 백엔드 API를 호출합니다.

## 1. 전체 구조

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

## 3. 루트 파일 역할

### `package.json`

프로젝트 이름, 실행 명령어, 설치할 라이브러리를 정의합니다.

```text
npm run dev       Vite 개발 서버 실행
npm run build     TypeScript 검사 후 Vite 빌드
npm run preview   빌드 결과 미리 보기
```

주요 라이브러리는 다음과 같습니다.

```text
react, react-dom       React 화면 구성
lightweight-charts     캔들 차트
typescript             TypeScript 컴파일러
vite                   개발 서버·빌드 도구
@vitejs/plugin-react   Vite의 React 플러그인
@types/react           React TypeScript 타입
@types/react-dom       React DOM TypeScript 타입
```

### `package-lock.json`

`package.json`의 라이브러리와 하위 의존성의 정확한 버전을 기록합니다. `npm ci`와 Docker 빌드가 동일한 의존성을 설치할 수 있게 하므로 유지해야 합니다.

### `index.html`

브라우저가 최초로 읽는 HTML입니다. `<div id="root">`가 React가 들어갈 자리이고, `main.tsx`를 모듈로 불러옵니다.

```text
index.html → main.tsx → App.tsx → 하위 컴포넌트
```

### `vite.config.ts`

Vite 개발 서버 설정입니다.

- React 플러그인 등록
- `/api` 요청을 `localhost:8080` Spring Boot로 전달
- SSE 응답 버퍼링을 줄이기 위한 프록시 설정

따라서 프론트에서는 `fetch('/api/auth/me')`처럼 요청하고, Vite가 백엔드로 전달합니다.

### `tsconfig.json`

React 애플리케이션 TypeScript 설정입니다. `strict: true`로 엄격한 타입 검사를 하고, `jsx: react-jsx`로 `.tsx`에서 JSX를 사용합니다. `include: ["src"]`는 `src`를 검사 대상으로 지정합니다.

### `tsconfig.node.json`

브라우저 React 코드가 아니라 Node.js 환경에서 실행되는 `vite.config.ts`를 TypeScript로 검사하기 위한 설정입니다.

### `.gitignore`

Git에 올리지 않을 파일을 지정합니다.

```text
node_modules/       설치된 npm 라이브러리
dist/               빌드 결과물
.vite/              Vite 캐시
*.log               개발 로그
```

`node_modules`는 로컬 개발에는 필요하지만 `npm install`로 다시 만들 수 있으므로 Git에는 저장하지 않습니다.

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

세부 API 구현보다 도메인 컴포넌트와 훅을 연결하는 역할이 중심입니다.

### `src/styles.css`

전체 레이아웃, 차트, 호가창, 거래 패널, 폼, 버튼, 주문 목록, 손익 색상 등의 전역 CSS입니다.

### `src/shared/types.ts`

백엔드 응답과 프론트엔드 데이터 구조를 정의합니다.

| 타입 | 의미 |
|---|---|
| `OrderBookLevel` | 호가 한 단계의 가격·수량 |
| `OrderBookSnapshot` | 전체 호가 스냅샷 |
| `User` | 로그인 사용자 |
| `Order` | 주문 |
| `Position` | 현재 포지션 |
| `Portfolio` | 계좌 요약 |
| `Fill` | 체결 내역 |

여러 도메인에서 함께 사용하므로 `shared`에 있습니다.

## 5. `auth` 도메인

회원가입, 로그인, 로그아웃, 현재 사용자 확인을 담당합니다.

### `auth/api/authApi.ts`

인증 관련 HTTP API를 호출합니다.

- `signup()`: 회원가입
- `login()`: 로그인
- `logout()`: 로그아웃
- `fetchMe()`: 현재 사용자 확인

세션 쿠키를 사용하므로 요청에 `credentials: 'include'`를 사용합니다. 화면은 그리지 않고 네트워크 통신과 오류 처리를 담당합니다.

### `auth/components/LoginForm.tsx`

이메일·비밀번호를 입력받아 로그인하는 화면입니다. controlled input, 폼 이벤트, 오류 메시지 표시를 포함합니다.

### `auth/components/SignupForm.tsx`

이메일·비밀번호·표시 이름을 입력받아 회원가입하는 화면입니다.

### `auth/hooks/useAuth.ts`

로그인 사용자인 `user`와 초기 확인 상태인 `loading`을 관리합니다.

```text
컴포넌트 마운트 → fetchMe() → user 설정
```

`login()`, `signup()`, `logout()` 함수를 컴포넌트에 제공합니다.

## 6. `market` 도메인

외부·백엔드 시세 데이터, 호가창, 캔들 차트를 담당합니다.

### `market/api/binanceKline.ts`

Binance Futures Kline REST API에서 과거 캔들 데이터를 가져옵니다.

- `Candle` 타입 정의
- 시간 간격 정의
- Binance API 요청
- 원본 배열 응답을 `Candle` 객체로 변환
- 시간 간격을 초 단위로 변환

### `market/components/OrderBook.tsx`

`OrderBookSnapshot`을 받아 매수·매도 호가를 표시합니다. 호가 정렬, 최우선 가격 표시, 가격 클릭 이벤트를 담당합니다.

### `market/components/CandleChart.tsx`

`lightweight-charts`로 캔들 차트를 표시합니다.

- 과거 캔들 표시
- 시간 간격 변경
- 실시간 중간 가격 반영
- 현재 포지션 가격선 표시
- 미체결 주문 가격선 표시
- 차트 생성·갱신·정리

외부 차트 객체를 React와 연결하기 위해 `useRef`와 `useEffect`를 사용합니다.

### `market/hooks/useOrderBookStream.ts`

백엔드 SSE 호가 스트림을 구독합니다.

```text
EventSource 연결
  → 호가 수신
  → snapshot state 변경
  → 호가창·차트 갱신
  → 언마운트 시 eventSource.close()
```

### `market/engine/quote.ts`

호가 스냅샷에서 다음 값을 계산하는 순수 TypeScript 로직입니다.

- `bestBid`: 가장 높은 매수 가격
- `bestAsk`: 가장 낮은 매도 가격
- `midPrice`: 두 가격의 평균
- `spread`: 매도 가격과 매수 가격의 차이

API, DOM, React state를 사용하지 않으므로 `engine`에 두었습니다.

## 7. `paper` 도메인

로그인한 사용자의 모의거래를 담당합니다.

### `paper/api/paperApi.ts`

모의거래 관련 Spring Boot API를 호출합니다.

- `createOrder()`: 시장가·지정가 주문 생성
- `listOrders()`: 주문 목록 조회
- `cancelOrder()`: 미체결 주문 취소
- `fetchPortfolio()`: 계좌·포지션 조회
- `listFills()`: 체결 내역 조회
- `setLeverage()`: 레버리지 변경

### `paper/hooks/useTrading.ts`

`portfolio`, `orders`, `fills` 상태를 관리합니다.

- 처음 화면이 열릴 때 데이터 조회
- `Promise.all`로 계좌·주문·체결 동시 조회
- 3초마다 데이터 갱신
- 주문·취소 직후 `refresh()` 실행
- 컴포넌트 종료 시 interval 정리

### `paper/components/AccountSummary.tsx`

현금 잔액, 실현·미실현 손익, 총 자산, 레버리지, 증거금, 현재 포지션을 표시합니다.

### `paper/components/ClosePositionButton.tsx`

현재 포지션 종료 API를 호출하고, 요청 중 상태와 종료 후 갱신을 처리합니다.

### `paper/components/FillHistory.tsx`

체결 내역 배열을 받아 목록으로 표시합니다.

### `paper/components/OrderForm.tsx`

매수·매도, 시장가·지정가, 수량, 가격, 레버리지 등을 입력받아 주문 API를 호출합니다. controlled input, 폼 이벤트, 숫자 변환, 비동기 처리를 함께 볼 수 있는 핵심 학습 파일입니다.

### `paper/components/OrderList.tsx`

주문 상태, 가격, 수량, 예상 손익을 표시하고 미체결 주문을 취소합니다.

### `paper/components/TradingPanel.tsx`

거래 관련 화면을 조합하는 상위 컴포넌트입니다.

```text
TradingPanel
├─ AccountSummary
├─ ClosePositionButton
└─ OrderForm
```

## 8. 주요 데이터 흐름

### 인증

```text
LoginForm.tsx
  → useAuth.ts
  → authApi.ts
  → Spring Boot 인증 API
```

### 실시간 호가

```text
Spring Boot SSE
  → useOrderBookStream.ts
  → OrderBookSnapshot
  → OrderBook.tsx / quote.ts / CandleChart.tsx
```

### 주문

```text
OrderForm.tsx
  → paperApi.ts
  → Spring Boot 주문 API
  → useTrading.ts의 refresh()
  → 계좌·주문·체결 화면 갱신
```

## 9. 로컬 실행과 Docker 실행

### 로컬 개발

```text
cd frontend
npm run dev
```

Vite가 프론트를 `localhost:5173`에서 실행합니다. `/api` 요청은 `vite.config.ts`의 proxy를 통해 Spring Boot `localhost:8080`으로 전달됩니다.

### Docker 빌드

프로젝트의 `Dockerfile`은 멀티 스테이지 빌드를 사용합니다.

```text
Node 빌드 단계
  → package.json과 package-lock.json 복사
  → Docker 내부에서 npm ci
  → Docker 내부에서 npm run build
  → Docker 내부 dist 생성

Java 빌드 단계
  → dist를 Spring Boot static 폴더로 복사
  → bootJar 생성

최종 실행 단계
  → Java app.jar 실행
```

로컬 `node_modules`는 Docker에 복사되지 않습니다. Docker가 내부에서 별도로 설치하며, 최종 Java 이미지에는 Node나 `node_modules`가 들어가지 않습니다.

## 10. `.ts`와 `.tsx`

```text
.ts   TypeScript 로직 파일
.tsx  TypeScript + JSX React 화면 파일
```

`OrderForm.tsx`는 JSX 화면을 반환하므로 `.tsx`이고, `useTrading.ts`, `paperApi.ts`, `quote.ts`는 화면 JSX 없이 로직만 작성하므로 `.ts`입니다.

## 11. 추천 학습 순서

1. `package.json`: 사용하는 기술 확인
2. `index.html`: React 진입점 확인
3. `main.tsx`: React 루트 생성 확인
4. `App.tsx`: 전체 화면 조합 확인
5. `shared/types.ts`: 데이터 구조 확인
6. `market/hooks/useOrderBookStream.ts`: SSE와 `useEffect` 확인
7. `market/components/OrderBook.tsx`: props와 렌더링 확인
8. `auth/hooks/useAuth.ts`: 인증 상태 확인
9. `paper/hooks/useTrading.ts`: 상태·polling 확인
10. `paper/components/OrderForm.tsx`: 폼과 API 요청 확인
11. `market/components/CandleChart.tsx`: 외부 라이브러리와 `useRef` 확인

각 파일을 읽을 때는 다음 질문을 반복하면 됩니다.

- 화면을 그리는 파일인가?
- state를 관리하는가?
- API를 호출하는가?
- 순수 계산만 하는가?
- 어떤 props를 받는가?
- 데이터가 바뀌면 어느 화면이 다시 렌더링되는가?
