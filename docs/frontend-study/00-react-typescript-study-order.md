# React + TypeScript 학습 순서 — 이 프로젝트를 교재로 사용하기

React을 처음 배우는 경우, 모든 파일을 위에서 아래로 읽으면 금방 복잡해집니다. 아래 **순서대로 한 파일씩** 보세요. 각 파일에서 적힌 핵심만 이해한 뒤 다음 파일로 넘어가면 됩니다.

처음에는 코드를 완전히 해석하려 하지 않아도 됩니다. “이 파일은 무슨 역할인가?”와 “React가 어떤 값을 받아 화면을 어떻게 바꾸는가?”만 잡는 것이 목표입니다.

## 시작 전

```powershell
cd frontend
npm install
npm run dev
```

브라우저에서 화면을 열어 둔 채 파일을 읽으세요. `npm run build`는 수정 후 TypeScript 오류가 없는지 확인할 때 사용합니다.

## 꼭 지킬 읽는 순서

| 순서 | 파일 | 이 파일에서 배울 핵심 | 지금은 넘어가도 되는 것 |
|---:|---|---|---|
| 1 | `frontend/package.json` | React, TypeScript, Vite가 이 프로젝트에 설치되어 있다는 사실과 `npm run dev`/`npm run build` 명령 | 각 라이브러리의 세부 버전 |
| 2 | `frontend/src/main.tsx` | React 앱의 시작점, `root`에 `<App />`을 그리는 방법 | `StrictMode`의 내부 동작 |
| 3 | `frontend/src/App.tsx` | 컴포넌트를 조립하는 방법, `useState`, 조건부 렌더링, props를 전달하는 모습 | 차트·주문 전체 로직 |
| 4 | `frontend/src/auth/components/LoginForm.tsx` | JSX, 컴포넌트, props, `input`, `button`, `onClick`/`onSubmit` 이벤트 | API가 실제로 요청되는 세부 과정 |
| 5 | `frontend/src/shared/types.ts` | TypeScript `type`, 문자열 유니온, `null`을 포함한 타입 | 모든 도메인 타입의 의미 |
| 6 | `frontend/src/auth/api/authApi.ts` | `fetch`, `async`/`await`, 서버 요청과 응답 | 쿠키·HTTP 헤더의 상세 규칙 |
| 7 | `frontend/src/auth/hooks/useAuth.ts` | 커스텀 Hook, 상태를 여러 컴포넌트에서 쓰기 좋게 묶는 방법 | 로그인 API의 모든 예외 처리 |
| 8 | `frontend/src/market/components/OrderBook.tsx` | 배열을 `map`으로 화면 목록으로 바꾸기, props로 함수 전달하기 | SSE 연결 방식 |
| 9 | `frontend/src/market/hooks/useOrderBookStream.ts` | `useEffect`, 실시간 데이터 구독, cleanup(연결 해제) | EventSource의 프로토콜 세부 사항 |
| 10 | `frontend/src/market/engine/quote.ts` | React 밖의 순수 함수와 화면 코드의 분리 | 호가 계산 공식 자체 |
| 11 | `frontend/src/paper/components/OrderForm.tsx` | 폼 입력 상태, 선택값, 부모에게 주문 요청을 전달하는 방법 | 주문 도메인 규칙 전체 |
| 12 | `frontend/src/paper/hooks/useTrading.ts` | 여러 API 호출·로딩·오류·데이터 상태를 하나의 Hook으로 관리 | 각 API의 구현 세부 |
| 13 | `frontend/src/paper/components/TradingPanel.tsx` | 작은 컴포넌트를 조합해 기능 화면을 만드는 방법 | CSS 레이아웃 미세 조정 |
| 14 | `frontend/src/market/components/CandleChart.tsx` | 외부 차트 라이브러리를 React 컴포넌트 안에서 사용하는 방법 | 차트 라이브러리 전체 API |

> `CandleChart.tsx`는 가장 마지막에 보세요. React의 기본 개념보다 외부 라이브러리 코드가 많아서 처음 읽기에 적합하지 않습니다.

---

## 파일별로 무엇을 보면 되는가

### 1. `frontend/package.json`

먼저 이 프로젝트가 무엇으로 실행되는지 확인합니다.

```json
"scripts": {
  "dev": "vite",
  "build": "tsc && vite build"
}
```

핵심:

- `npm run dev`: Vite 개발 서버 실행
- `npm run build`: TypeScript 검사 후 배포용 파일 생성
- `react`, `react-dom`: 화면을 만드는 라이브러리
- `typescript`: 코드의 타입 오류를 미리 찾는 도구
- `vite`: 개발 서버와 빌드 도구

여기서는 **Vite가 React 자체가 아니라 React 프로젝트를 실행하고 빌드하는 도구**라는 것만 기억하면 됩니다.

### 2. `frontend/src/main.tsx`

이 파일은 React의 입구입니다.

```tsx
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

핵심:

- `index.html`에 있는 `<div id="root">`를 찾는다.
- 그 빈 공간에 `<App />` 컴포넌트를 그린다.
- 즉, 화면은 `App.tsx`부터 시작한다.

다음 한 줄로 기억하면 충분합니다.

```text
index.html의 root → main.tsx → App.tsx → 하위 컴포넌트
```

### 3. `frontend/src/App.tsx`

이 파일은 여러 기능을 한 화면으로 **조립하는 부모 컴포넌트**입니다. 처음에는 위에서부터 아래로 읽되, 아래 네 가지만 찾으세요.

```tsx
const [form, setForm] = useState<FormMode>(null);
```

핵심:

- `useState`: 화면이 기억해야 하는 값을 만든다.
- `form`: 로그인 폼/회원가입 폼을 열지 저장한다.
- `setForm(...)`: 상태를 바꾸고 화면을 다시 그리게 한다.
- `<LoginForm />`, `<OrderBook />`처럼 다른 컴포넌트에 값(props)을 전달한다.

아래 코드는 React에서 가장 자주 나오는 패턴입니다.

```tsx
{form === 'login' && <LoginForm onLogin={login} onClose={() => setForm(null)} />}
```

뜻: `form`이 `'login'`일 때만 로그인 화면을 보여 준다.

### 4. `frontend/src/auth/components/LoginForm.tsx`

처음 읽을 컴포넌트입니다. `App.tsx`보다 작아서 React의 기본을 보기 좋습니다.

핵심:

- 컴포넌트는 `return (...)`으로 JSX 화면을 돌려주는 함수다.
- `onLogin`, `onClose`는 부모(`App.tsx`)가 전달한 props다.
- `<input>`은 사용자 입력을 받는다.
- `<form onSubmit={...}>`은 제출 이벤트를 처리한다.
- 버튼 클릭/폼 제출 뒤에는 부모가 준 함수를 호출해 부모에게 알려 준다.

여기서 스스로 답해 보세요.

> 로그인 버튼을 누르면 어떤 함수가 호출되고, 그 함수는 어느 파일에 있는가?

답의 흐름은 `LoginForm.tsx → App.tsx → useAuth.ts`입니다.

### 5. `frontend/src/shared/types.ts`

이 파일은 TypeScript 타입을 모아 둔 곳입니다. React보다 먼저 아래 모양을 익히세요.

```ts
export type User = {
  id: number;
  email: string;
  displayName: string;
};
```

핵심:

- `type User = { ... }`: `User` 데이터가 가져야 할 모양을 설명한다.
- 이것은 실제 데이터가 아니라 **검사용 설계도**다.
- `export`: 다른 파일에서도 이 타입을 쓸 수 있게 내보낸다.
- `string | null`: 문자열이거나 값이 없을 수 있다는 뜻이다.
- `'BUY' | 'SELL'`: 둘 중 하나만 가능한 값이라는 뜻이다.

`Order`, `Portfolio`는 지금 다 외우지 말고, 화면에서 해당 이름이 나올 때마다 다시 찾아보면 됩니다.

### 6. `frontend/src/auth/api/authApi.ts`

이 파일은 화면이 아니라 서버와 통신하는 코드입니다.

핵심:

- `fetch(...)`: 백엔드 API에 요청을 보낸다.
- `async`/`await`: 서버 응답이 올 때까지 기다린다.
- API 함수는 데이터를 받아서 반환하고, 화면을 직접 그리지 않는다.

구분을 기억하세요.

```text
컴포넌트: 화면을 그림
api 파일: 서버와 통신함
```

### 7. `frontend/src/auth/hooks/useAuth.ts`

`useAuth`는 로그인과 관련된 상태·API 호출을 한곳에 모아 둔 **커스텀 Hook**입니다.

핵심:

- `user`, `loading`, `error`처럼 화면에서 필요한 상태를 관리한다.
- `login`, `signup`, `logout` 함수를 밖으로 반환한다.
- `App.tsx`는 Hook의 내부 구현을 몰라도 반환값만 받아 쓸 수 있다.

```text
App.tsx
  → useAuth() 호출
  → user / login / logout 등을 받음
  → 받은 값으로 화면을 결정함
```

### 8. `frontend/src/market/components/OrderBook.tsx`

호가 목록을 보여 주는 컴포넌트입니다. React에서 배열을 화면으로 바꾸는 방식을 배웁니다.

핵심:

- `bids.map(...)`, `asks.map(...)`: 배열 항목마다 JSX를 하나씩 만든다.
- `key`: React가 목록 항목을 구별하도록 붙이는 값이다.
- `onPriceClick(price)`: 자식 컴포넌트가 부모에게 “이 가격이 클릭되었다”고 알리는 방법이다.

중요한 흐름:

```text
부모가 onPriceClick 함수를 props로 전달
  → OrderBook에서 가격을 클릭
  → OrderBook이 onPriceClick(가격)을 호출
  → App.tsx의 상태가 바뀜
```

### 9. `frontend/src/market/hooks/useOrderBookStream.ts`

이 파일에서 `useEffect`를 배웁니다. `useEffect`는 화면을 그리는 것 외에 서버 연결, 타이머, 구독 같은 작업을 할 때 사용합니다.

핵심:

- `useEffect(() => { ... }, [])`: 컴포넌트가 처음 화면에 나타날 때 작업한다.
- `EventSource`: 서버에서 오는 실시간 메시지를 받는다.
- `setSnapshot(...)`: 새 메시지를 상태에 저장해 화면을 갱신한다.
- `return () => eventSource.close()`: 화면이 사라질 때 연결을 정리한다.

```text
서버 메시지 수신
  → setSnapshot으로 상태 변경
  → App.tsx가 새 데이터를 props로 전달
  → OrderBook/CandleChart가 새 화면을 그림
```

### 10. `frontend/src/market/engine/quote.ts`

이 파일은 React 코드가 아닌 일반 TypeScript 계산 함수입니다.

핵심:

- 화면과 상관없는 계산은 컴포넌트 밖의 함수로 분리한다.
- 입력이 같으면 항상 같은 결과가 나오는 함수를 **순수 함수**라고 한다.
- 이렇게 분리하면 테스트와 재사용이 쉬워진다.

React 컴포넌트에 모든 계산을 넣지 않는 이유를 이해하는 단계입니다.

### 11. `frontend/src/paper/components/OrderForm.tsx`

이제 폼을 한 단계 더 자세히 봅니다.

핵심:

- `useState`로 입력값을 관리한다.
- 사용자의 선택/입력을 주문 요청 데이터로 만든다.
- 제출 성공 뒤 부모가 준 `onChanged` 같은 함수를 호출해 화면 갱신을 요청한다.

`LoginForm`과 비교하면서 “입력 → 상태 → 제출”의 공통 구조를 찾아보세요.

### 12. `frontend/src/paper/hooks/useTrading.ts`

`useAuth.ts`를 이해한 뒤에 보세요. 구조가 더 복잡하지만 역할은 비슷합니다.

핵심:

- 주문, 잔고, 체결 내역 같은 여러 데이터를 관리한다.
- API 요청 뒤 최신 데이터를 다시 가져온다.
- `loading`, `error` 상태를 화면 컴포넌트에 제공한다.

여기서는 모든 함수를 외우지 말고, `refresh`가 어떤 데이터를 다시 불러오는지만 따라가면 됩니다.

### 13. `frontend/src/paper/components/TradingPanel.tsx`

작은 화면 조각을 하나로 묶는 방법을 봅니다.

핵심:

- `OrderForm`, `AccountSummary`, `OrderList` 등을 import해서 배치한다.
- 큰 화면은 작고 역할이 분명한 컴포넌트로 나눈다.
- 필요한 데이터와 함수를 props로 각각 전달한다.

`App.tsx`와 비교하면, 둘 다 “조립 담당 컴포넌트”라는 점을 알 수 있습니다.

### 14. `frontend/src/market/components/CandleChart.tsx`

마지막 파일입니다. React 기본기를 익힌 뒤에만 읽으세요.

핵심:

- `lightweight-charts` 같은 외부 라이브러리를 React에 연결하는 방법
- `useEffect`에서 차트를 만들고, cleanup에서 제거하는 방법
- props가 바뀌면 차트 데이터를 갱신하는 방법

이 파일에서 이해가 안 되는 차트 전용 코드는 당장 건너뛰어도 됩니다. 목표는 **외부 객체의 생성·갱신·정리를 React 생명주기와 연결하는 방식**을 보는 것입니다.

---

## 오늘 처음 볼 파일 4개

처음 시작하는 날에는 여기까지만 보세요.

1. `frontend/package.json`
2. `frontend/src/main.tsx`
3. `frontend/src/App.tsx`
4. `frontend/src/auth/components/LoginForm.tsx`

마지막으로 `App.tsx`의 제목 아래에 아래 한 줄을 잠시 넣고 화면이 바뀌는지 확인해 보세요.

```tsx
<p>React 학습 중입니다.</p>
```

이 작은 변경이 보이면, 이미 “컴포넌트 수정 → Vite 갱신 → React 화면 변경” 흐름을 경험한 것입니다.
