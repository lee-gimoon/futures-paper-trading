# React + TypeScript 학습 순서 — 이 프로젝트를 교재로 사용하기

이 문서는 React을 처음 배우는 사람이 이 저장소의 `frontend` 코드를 따라가며 공부하기 위한 순서입니다.

목표는 처음부터 모든 코드를 외우는 것이 아닙니다. **작은 화면을 React가 어떻게 그리는지 이해한 뒤**, 이 프로젝트의 로그인, 시세, 모의 거래 기능을 한 단계씩 읽는 것입니다.

## 시작 전: 프로젝트를 실행해 보기

터미널에서 `frontend` 폴더로 이동한 뒤 실행합니다.

```powershell
cd frontend
npm install
npm run dev
```

표시되는 주소(보통 `http://localhost:5173`)를 브라우저에서 엽니다. 실행 중에는 파일을 저장할 때 화면이 자동으로 갱신됩니다.

다음 명령도 기억해 둡니다.

```powershell
npm run build
```

이 명령은 TypeScript 오류를 검사하고 배포용 파일을 만듭니다. 학습 중 코드를 수정한 뒤 오류가 없는지 확인할 때 사용합니다.

> 백엔드가 실행되지 않은 상태에서는 로그인, 주문, 실시간 데이터 같은 API 기능이 실패할 수 있습니다. 이때도 React 화면 구조를 읽고 연습하는 데는 문제가 없습니다.

## 전체 학습 지도

```text
JavaScript/TypeScript 기초
        ↓
Vite가 React 앱을 시작하는 위치 이해
        ↓
컴포넌트와 JSX
        ↓
props · useState · 이벤트 · 조건부 렌더링
        ↓
useEffect와 서버 데이터
        ↓
커스텀 Hook · API · TypeScript 타입
        ↓
차트/호가/주문 기능을 하나의 흐름으로 읽기
```

## 1단계: JavaScript와 TypeScript 기초

React보다 먼저 아래 문법을 익힙니다. React 코드는 JavaScript 위에 TypeScript 타입을 더한 코드이기 때문입니다.

1. [`01-object-literals-and-arrow-functions.md`](01-object-literals-and-arrow-functions.md)
   - 객체 `{}`
   - 화살표 함수 `() => {}`
   - `type`과 `export`/`import`
2. [`02-export-and-import.md`](02-export-and-import.md)
   - 파일을 나누고 다른 파일의 코드를 가져오는 방법
3. `frontend/src/shared/types.ts`
   - `User`, `Order`, `Portfolio` 같은 실제 타입을 읽습니다.

이 단계에서는 다음을 말로 설명할 수 있으면 충분합니다.

```ts
type User = { email: string };

const greet = (user: User) => `안녕하세요, ${user.email}`;
```

- `User`는 값이 아니라 객체의 모양을 설명하는 타입이다.
- `greet`는 `User`를 받아 문자열을 돌려주는 함수다.

## 2단계: Vite와 React의 시작점

다음 파일을 순서대로 엽니다.

1. `frontend/package.json`
2. `frontend/index.html`
3. `frontend/src/main.tsx`
4. `frontend/src/App.tsx`

흐름은 아래와 같습니다.

```text
브라우저
  → index.html의 <div id="root">
  → main.tsx가 root를 찾음
  → <App />을 렌더링함
  → App.tsx와 하위 컴포넌트가 화면을 만듦
```

- **Vite**는 개발 서버를 실행하고 TypeScript/React 코드를 브라우저용 코드로 처리해 주는 도구입니다.
- `main.tsx`는 React의 입구입니다.
- `App.tsx`는 이 앱의 가장 큰 화면 조립 파일입니다.
- `.tsx`는 TypeScript 파일 안에서 HTML처럼 보이는 JSX를 쓸 수 있는 확장자입니다.

## 3단계: 컴포넌트와 JSX

React 컴포넌트는 **화면의 한 조각을 반환하는 함수**입니다.

```tsx
function Welcome() {
  return <h1>안녕하세요</h1>;
}
```

`App.tsx`에서 아래처럼 보이는 태그들이 모두 컴포넌트입니다.

```tsx
<CandleChart snapshot={snapshot} />
<OrderBook snapshot={snapshot} onPriceClick={handlePriceClick} />
```

처음에는 복잡한 차트 대신 작은 폼 컴포넌트부터 읽는 편이 좋습니다.

1. `frontend/src/auth/components/LoginForm.tsx`
2. `frontend/src/auth/components/SignupForm.tsx`
3. `frontend/src/paper/components/AccountSummary.tsx`

읽을 때 아래 세 가지를 찾아보세요.

- `return (...)` 안에 어떤 화면(JSX)을 만드는가?
- 함수의 매개변수(예: `onLogin`, `portfolio`)는 무엇인가?
- 버튼이나 입력창에서 어떤 이벤트를 처리하는가?

### 실습 1

`App.tsx`의 제목 근처에 다음 JSX를 잠시 추가하고 화면 변화를 확인합니다.

```tsx
<p>React 학습 중입니다.</p>
```

확인한 뒤에는 원래 코드로 되돌리거나, 학습용 문구로 남겨도 됩니다.

## 4단계: props, 상태(useState), 이벤트

### props: 부모가 자식에게 건네는 값

```tsx
function Greeting({ name }: { name: string }) {
  return <p>{name}님, 반갑습니다.</p>;
}

<Greeting name="Codex" />
```

여기서 `name`은 props입니다. 이 프로젝트에서는 `CandleChart`의 `snapshot`, `OrderBook`의 `onPriceClick` 등이 props입니다.

### 상태: 화면이 기억해야 하는 값

`App.tsx`의 이 코드를 읽습니다.

```tsx
const [form, setForm] = useState<FormMode>(null);
```

- `form`: 현재 로그인/회원가입 폼이 열렸는지 저장하는 값
- `setForm(...)`: 값을 바꾸는 함수
- 값이 바뀌면 React가 관련 화면을 다시 그립니다.

### 이벤트와 조건부 렌더링

```tsx
<button onClick={() => setForm('login')}>로그인</button>

{form === 'login' && <LoginForm onLogin={login} onClose={() => setForm(null)} />}
```

버튼 클릭으로 상태를 바꾸고, 상태가 `'login'`일 때만 로그인 폼을 표시합니다.

### 실습 2

`App.tsx`에 숫자를 하나 늘리는 버튼을 잠시 만들어 보세요.

```tsx
const [count, setCount] = useState(0);

<button onClick={() => setCount(count + 1)}>
  클릭 수: {count}
</button>
```

이 실습이 자연스럽게 이해되면 로그인 버튼, 로그아웃 버튼, 호가 클릭 처리도 같은 원리로 읽을 수 있습니다.

## 5단계: useEffect와 서버에서 오는 데이터

다음 파일을 읽습니다.

1. `frontend/src/market/hooks/useOrderBookStream.ts`
2. `frontend/src/market/components/OrderBook.tsx`
3. `frontend/src/market/engine/quote.ts`

`useEffect`는 컴포넌트가 화면에 나타난 뒤 서버 연결, 타이머, 이벤트 구독처럼 **React 바깥과 연결하는 작업**을 할 때 사용합니다.

이 프로젝트에서는 실시간 호가 SSE 연결이 메시지를 받을 때마다 상태를 바꾸고, 상태 변화가 `OrderBook`과 `CandleChart` 화면 갱신으로 이어집니다.

```text
백엔드 SSE 메시지
  → useOrderBookStream의 상태 변경
  → App.tsx가 새 snapshot을 props로 전달
  → OrderBook / CandleChart가 다시 렌더링
```

여기서는 특히 `return () => ...`를 찾아보세요. 이는 컴포넌트가 사라질 때 연결을 정리하는 **cleanup**입니다.

## 6단계: 커스텀 Hook과 API 함수

다음 순서가 좋습니다.

1. `frontend/src/auth/api/authApi.ts`
2. `frontend/src/auth/hooks/useAuth.ts`
3. `frontend/src/paper/api/paperApi.ts`
4. `frontend/src/paper/hooks/useTrading.ts`

역할을 이렇게 구분해 읽으면 편합니다.

| 위치 | 역할 |
|---|---|
| `api/*.ts` | `fetch`로 백엔드에 요청하고 응답을 받음 |
| `hooks/*.ts` | 로딩/오류/데이터 같은 React 상태와 API 호출을 묶음 |
| `components/*.tsx` | 받은 데이터를 화면에 표시하고 사용자 입력을 받음 |
| `shared/types.ts` | 서버와 화면이 주고받는 데이터의 모양을 정의함 |

`useAuth`를 읽을 때는 “로그인 성공 뒤 `user` 상태가 어떻게 바뀌고, 그 결과 `App.tsx`의 어느 화면이 달라지는가?”를 따라가 보세요.

## 7단계: 기능 하나를 처음부터 끝까지 따라가기

처음에는 아래 두 흐름 중 하나를 선택해 한 번에 읽습니다.

### A. 로그인 흐름

```text
LoginForm.tsx
  → useAuth.ts의 login
  → authApi.ts의 API 요청
  → User 타입
  → App.tsx의 user 조건부 화면
```

### B. 주문 흐름

```text
OrderForm.tsx
  → useTrading.ts
  → paperApi.ts의 API 요청
  → Order / Portfolio 타입
  → OrderList, AccountSummary, FillHistory 화면
```

파일 하나를 완벽히 이해하려고 멈추기보다, 이 흐름을 왕복하며 “입력 → 상태 변경 → 화면 갱신”의 길을 찾는 데 집중하세요.

## 권장 학습 리듬

- 하루 30~60분씩 한 단계만 진행합니다.
- 먼저 실행해 보고, 파일을 읽고, 작은 수정 하나를 한 다음 `npm run build`로 확인합니다.
- 모르는 문법은 표시해 두고 다음 단계로 넘어갑니다. 처음부터 차트 라이브러리나 SSE 세부 구현을 모두 이해할 필요는 없습니다.
- 원본 기능을 크게 고치기보다는, 학습용으로 문구·버튼·조건부 표시처럼 되돌리기 쉬운 변경부터 해 봅니다.

## 지금 바로 시작할 파일

처음이라면 오늘은 아래 네 파일만 순서대로 보세요.

1. `frontend/package.json`
2. `frontend/src/main.tsx`
3. `frontend/src/App.tsx`
4. `frontend/src/auth/components/LoginForm.tsx`

그리고 `App.tsx`에 `<p>React 학습 중입니다.</p>`를 한 줄 추가해 화면이 바뀌는 것까지 확인하면, React 학습의 좋은 첫걸음입니다.
