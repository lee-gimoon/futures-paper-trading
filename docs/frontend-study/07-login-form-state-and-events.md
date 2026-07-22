# LoginForm 사용자 흐름으로 배우는 React

이 문서는 개념을 사전처럼 나열하지 않는다. 사용자가 실제 로그인 화면에서 행동하는 순서대로 코드를 따라가며, 그 순간 필요한 React 개념과 문법을 함께 배운다.

학습 순서는 다음과 같다.

```text
페이지 접속
→ React 앱 시작 (브라우저에서 JavaScript가 실행되어 React가 UI·상태·이벤트를 관리하는 프런트엔드를 처음 렌더링한다는 뜻이며, React라는 별도 프로그램이 켜지는 것은 아니다.)
→ 로그인 버튼 클릭
→ LoginForm mount
→ 이메일·비밀번호 입력
→ 로그인 버튼 클릭 또는 Enter
→ 요청 중 UI
→ 성공·실패·취소
→ LoginForm unmount 또는 재시도
```

## 0. 먼저 파일 역할을 확인하자

| 파일 | 역할 |
|---|---|
| `frontend/src/main.tsx` | React 앱을 처음 시작한다. |
| `frontend/src/App.tsx` | 로그인 폼을 열고 닫으며 앱 전체 인증 화면을 결정한다. |
| `frontend/src/auth/components/LoginForm.tsx` | 입력값과 제출 상태를 관리하고 사용자 입력을 받는다. |
| `frontend/src/auth/hooks/useAuth.ts` | 로그인한 사용자 state와 로그인 동작을 관리한다. |
| `frontend/src/auth/api/authApi.ts` | 브라우저의 `fetch`로 인증 HTTP 요청을 보낸다. |

## React 프로젝트인 이유

이 프로젝트에서는 `.tsx` 파일에 JSX로 화면 구조를 작성한다. TypeScript의 `"jsx": "react-jsx"` 설정은 이 JSX를 `react` 패키지의 `jsx-runtime` 호출 코드로 변환한다. 또한 `react`에서 `useState` 같은 상태 기능을 가져오고, `react-dom`의 `ReactDOM.createRoot(...).render(<App />)`로 React가 계산한 화면을 브라우저 DOM에 표시한다. 이처럼 화면 구조·상태·렌더링에 React와 ReactDOM을 사용하므로 React 프로젝트라고 한다.

---

## 1. 사용자가 페이지에 접속한다: React 앱 시작

브라우저는 `index.html`의 `<div id="root">`와 module script를 읽고 `main.tsx`를 실행한다.

```tsx
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

실행 순서는 다음과 같다.

```text
브라우저가 JavaScript 실행
→ document.getElementById('root')로 실제 DOM div#root 찾기
→ ReactDOM.createRoot(div#root)로 React가 관리할 root 만들기
→ render(<App />)로 최초 렌더링 요청
→ React가 App 컴포넌트 호출
→ App이 JSX 계산
→ ReactDOM이 계산 결과를 실제 브라우저 DOM에 반영
```

### 이때 처음 나오는 React 개념

**React**는 컴포넌트와 state를 이용해 현재 화면 구조를 계산하는 JavaScript 라이브러리다. 별도의 React 엔진이 있는 것이 아니라 브라우저의 JavaScript 엔진이 React 라이브러리 코드와 우리 코드를 함께 실행한다.

**ReactDOM**은 React의 계산 결과를 브라우저 DOM에 반영하는 렌더러다. `createRoot`를 호출하면 `div#root` 내부를 관리하고, 브라우저 이벤트를 받을 내부 리스너도 설정한다.

**컴포넌트(component)**는 React가 렌더링 중 호출하여 JSX를 받는 함수다. `<App />`은 `App()`을 이 줄에서 직접 호출한다는 뜻이 아니라, React에게 App 컴포넌트를 렌더링하라고 설명하는 JSX다.

**JSX**는 현재 화면에 무엇이 있어야 하는지를 적는 문법이다. 빌드 과정에서 브라우저가 실행할 JavaScript 표현으로 변환된다.

**render와 commit**은 구분한다.

```text
render: React가 컴포넌트를 호출하여 새 JSX를 계산
commit: ReactDOM이 계산 결과의 필요한 차이를 실제 DOM에 반영
```

렌더링은 페이지 새로고침이 아니다. React는 기존 페이지 안에서 필요한 DOM만 갱신한다.

App은 첫 화면을 반영한 뒤 `useAuth`의 Effect로 기존 세션을 확인한다. 세션이 있으면 바로 로그인된 화면을 보여 주고, 없을 때만 로그인 버튼을 보여 준다. Effect는 10장에서 이벤트 핸들러와 비교한다. 이 시점의 `form`은 `null`이므로 `LoginForm`은 아직 없다.

---

## 2. 사용자가 App의 로그인 버튼을 클릭한다

`App.tsx`에는 현재 열 폼을 기억하는 state가 있다.

```tsx
type FormMode = 'login' | 'signup' | null;

const [form, setForm] = useState<FormMode>(null);
```

- `form`: 현재 state 값
- `setForm`: 다음 state를 요청하는 setter 함수
- `null`: 어떤 인증 폼도 열지 않은 상태
- `FormMode`: 세 값만 허용하는 TypeScript 유니온 타입

로그인 버튼은 다음과 같다.

```tsx
<button onClick={() => setForm('login')}>로그인</button>
```

사용자 행동부터 React 처리까지의 순서다.

```text
사용자가 로그인 버튼 클릭
→ 브라우저 click 이벤트
→ React가 onClick prop에 등록된 함수 호출
→ setForm('login')
→ App 재렌더링 요청
```

`onClick`은 이벤트 prop 이름이고, 실제로 전달한 함수는 `() => setForm('login')`이다.

```tsx
onClick={() => setForm('login')} // 클릭할 때 함수 실행
onClick={setForm('login')}       // 렌더링 중 즉시 실행되므로 잘못된 형태
```

`setForm('login')`은 변수에 즉시 대입하는 문법이 아니다. React에 다음 state를 저장하고 App을 다시 렌더링해 달라고 요청한다.

### 조건이 참이 되어 LoginForm이 mount된다

App의 조건부 렌더링 코드는 다음과 같다.

```tsx
{form === 'login' && (
  <LoginForm
    onLogin={login}
    onClose={() => setForm(null)}
  />
)}
```

`&&`는 JavaScript의 단축 평가 문법이다.

```text
form === 'login'이 false → 오른쪽 LoginForm을 렌더링하지 않음
form === 'login'이 true  → 오른쪽 LoginForm을 렌더링함
```

`setForm('login')` 이후 조건이 처음 참이 되면 `LoginForm`이 React 트리에 추가된다. 이것이 **mount(마운트)**다.

### App이 LoginForm에 props를 전달한다

**props**는 부모가 자식 컴포넌트에 전달하는 입력이다.

```tsx
<LoginForm
  onLogin={login}
  onClose={() => setForm(null)}
/>
```

- `onLogin`: `useAuth`가 제공한 실제 로그인 함수
- `onClose`: App의 `form`을 `null`로 바꾸는 함수

LoginForm은 서버 로그인 방법이나 App의 state 구조를 몰라도 된다. 부모가 준 함수를 필요한 순간 호출하기만 한다.

---

## 3. LoginForm이 처음 mount된다

LoginForm은 props의 모양을 TypeScript로 선언한다.

```tsx
type Props = {
  onLogin: (email: string, password: string) => Promise<void>;
  onClose: () => void;
};

export function LoginForm({ onLogin, onClose }: Props) {
  // ...
}
```

문법을 나누면 다음과 같다.

- `({ onLogin, onClose }: Props)`: props 객체에서 두 필드를 구조 분해한다.
- `(email: string, password: string) => Promise<void>`: 문자열 두 개를 받고 완료를 기다릴 수 있는 Promise를 반환하는 함수 타입이다.
- `() => void`: 인자와 반환값이 필요 없는 함수 타입이다.

### 네 개의 지역 state가 초기화된다

```tsx
const [email, setEmail] = useState('');
const [password, setPassword] = useState('');
const [error, setError] = useState('');
const [submitting, setSubmitting] = useState(false);
```

| state | 최초 값 | 역할 |
|---|---|---|
| `email` | `''` | 이메일 input의 값 |
| `password` | `''` | 비밀번호 input의 값 |
| `error` | `''` | 폼 안에 표시할 오류 메시지 |
| `submitting` | `false` | 로그인 요청 진행 여부 |

`useState`가 반환한 배열을 구조 분해하여 현재 값과 setter를 받는다.

```tsx
const [현재값, setter] = useState(초기값);
```

초기값은 최초 mount에 사용된다. 이후 재렌더링에서는 React가 기억한 state가 반환된다. 따라서 이메일 한 글자를 입력할 때마다 `useState('')` 코드가 다시 실행되어도 값이 빈 문자열로 초기화되지 않는다.

이 네 값은 LoginForm만 사용하는 **지역 state**다. App의 `user`처럼 앱 전체가 알아야 하는 값은 `useAuth`가 소유한다.

**Hook(훅)**은 컴포넌트에서 React 기능을 사용하는 `use...` 함수다. React가 호출 순서로 각 state를 구분하므로 Hook은 컴포넌트 최상위에서 항상 같은 순서로 호출하고, 조건문·반복문 안에서 호출하지 않는다.

### 첫 화면이 만들어진다

```text
LoginForm 함수 호출
→ 네 state를 사용해 JSX 계산(render)
→ ReactDOM이 form, input, button을 실제 DOM에 반영(commit)
```

mount는 최초로 트리에 들어오는 일이다. 이후 state가 바뀌어 같은 LoginForm을 다시 계산하는 것은 **re-render(재렌더링)**이며, 기존 state는 유지된다.

---

## 4. 사용자가 이메일과 비밀번호를 입력한다

이메일 input의 핵심 코드는 다음과 같다.

```tsx
<input
  type="email"
  placeholder="이메일"
  value={email}
  onChange={(e) => setEmail(e.target.value)}
  required
/>
```

한 글자 `a`를 입력하면 다음 순서로 처리된다.

```text
사용자가 a 입력
→ 브라우저가 실제 DOM input.value를 'a'로 변경
→ React가 onChange에 등록된 함수를 이벤트 객체 e와 함께 호출
→ setEmail(e.target.value)로 'a' 저장
→ LoginForm 재렌더링, 새 value={email}은 'a'
→ ReactDOM이 실제 input 표시값을 state와 맞춤
```

> 더 깊이 보기: ReactDOM은 `createRoot(div#root)` 때 root에 공용 이벤트 리스너를 설정한다. `input` 이벤트가 `input → form → ... → div#root`로 **버블링**하면 브라우저가 그 리스너를 호출하고, ReactDOM이 해당 JSX의 `onChange` 함수를 찾는다. React가 DOM을 계속 감시하는 방식이 아니라 브라우저 콜백 방식이다.

### e.target.value는 무엇인가?

```tsx
onChange={(e) => setEmail(e.target.value)}
```

- `e`: React가 이벤트 함수에 전달한 이벤트 객체
- `e.target`: 이벤트가 시작된 실제 DOM input 객체
- `e.target.value`: 그 input의 현재 문자열 값

### 제어되는 입력

`value`와 `onChange`를 함께 사용하는 input을 **제어되는 입력(controlled input)**이라고 한다.

```text
화면에 표시할 값의 원천: React state
사용자 입력을 state로 옮기는 통로: onChange
```

`value={email}`만 있고 `onChange`가 없다면 React state를 바꿀 방법이 없어 사실상 편집할 수 없는 input이 된다.

비밀번호 input도 `value={password}`와 `onChange={(e) => setPassword(e.target.value)}`로 같은 흐름을 사용한다. `type="password"`는 화면 글자를 가릴 뿐 state를 암호화하지 않는다.

### state는 현재 렌더링의 스냅샷이다

setter는 현재 함수 안의 변수를 즉시 바꾸는 대입문이 아니다. 다음 state를 저장하고 다음 렌더링을 요청하며, 새 렌더링에서 새 `email` 값을 받는다. 현재 이벤트 함수 안의 `email`은 그 렌더링이 시작될 때의 값이다.

---

## 5. 사용자가 로그인 버튼을 클릭하거나 Enter를 누른다

폼과 제출 버튼은 다음처럼 연결되어 있다.

```tsx
<form className="auth-form" onSubmit={handleSubmit}>
  {/* input들 */}
  <button type="submit" disabled={submitting}>
    {submitting ? '로그인 중...' : '로그인'}
  </button>
</form>
```

버튼을 클릭할 때의 순서다.

```text
사용자가 로그인 버튼 클릭
→ 브라우저 click 이벤트 발생
→ 이 버튼에는 onClick 함수가 없으므로 사용자 click 함수는 실행되지 않음
→ type="submit"의 HTML 기본 동작으로 form 제출 절차 시작
→ 브라우저 기본 유효성 검사
→ 검증 성공 시 form의 submit 이벤트
→ React가 onSubmit에 등록된 handleSubmit(e) 호출
```

입력칸에서 Enter를 눌러도 같은 form submit 경로로 모인다. 로그인 처리를 버튼 `onClick`이 아니라 form의 `onSubmit`에 두는 이유다.

### type="email"과 required

```tsx
<input type="email" required />
```

이것은 React 기능이 아니라 브라우저의 HTML 기본 유효성 검사다.

- `required`: 빈 값 제출 방지
- `type="email"`: 기본 이메일 형식 검사

검증에 실패하면 브라우저가 안내를 표시하고 submit 이벤트를 발생시키지 않으므로 `handleSubmit`도 호출되지 않는다. 실제 이메일 존재 여부와 비밀번호 일치는 서버가 검사한다.

submit 버튼을 클릭하면 먼저 `click` 이벤트가 발생한다. 현재 버튼에는 `onClick`이 없고, 이어지는 `type="submit"` 기본 동작이 form의 submit 이벤트를 만든다.

```text
click 이벤트: 버튼을 클릭했다는 사건
submit 이벤트: form을 제출하려 한다는 사건
```

현재 코드는 버튼 클릭과 Enter 제출을 모두 처리할 수 있도록 로그인 로직을 form의 `onSubmit` 한 곳에 모은다.

### FormEvent는 실행 코드가 아니라 타입이다

```tsx
import { type FormEvent } from 'react';

async function handleSubmit(e: FormEvent) {
  // ...
}
```

`FormEvent`는 TypeScript에게 `e`가 폼 이벤트 형태이며 `preventDefault()` 같은 메서드를 가진다고 알려 준다. 오타와 잘못된 사용을 컴파일 단계에서 검사하지만, 실행 중 이벤트를 만드는 것은 브라우저와 ReactDOM 이벤트 시스템이다.

### preventDefault()

```tsx
e.preventDefault();
```

HTML form의 기본 동작은 form 데이터를 전송하고 페이지를 이동하는 것이다. `preventDefault()`는 submit 이벤트 처리가 끝난 뒤 브라우저가 수행할 기본 전송·페이지 이동을 막아, 진행 중인 로그인 UI가 페이지 이동으로 사라지지 않게 한다.

---

## 6. 로그인 요청을 시작하고 기다린다

전체 제출 함수는 다음과 같다.

```tsx
async function handleSubmit(e: FormEvent) {
  e.preventDefault();
  setError('');
  setSubmitting(true);

  try {
    await onLogin(email, password);
    onClose();
  } catch (err) {
    setError(err instanceof Error ? err.message : '로그인에 실패했습니다.');
  } finally {
    setSubmitting(false);
  }
}
```

### async, Promise, await

`async` 함수는 Promise를 반환한다. `await`는 Promise가 끝날 때까지 `handleSubmit`의 나머지 실행을 일시 중단하고, 그동안 브라우저와 React는 다른 작업을 계속한다. `onLogin`은 App이 props로 전달한 함수이고, 실제로는 `useAuth`의 `login`이다.

```text
LoginForm.handleSubmit
→ props onLogin
→ useAuth.login
→ authApi.login
```

`setError('')`는 이전 오류를 지우고 `setSubmitting(true)`는 요청 중 UI를 만든다. Promise가 성공하면 `onClose()`, 실패하면 `catch`, 어느 쪽이든 마지막에는 `finally`가 실행된다.

### submitting state가 요청 중 화면을 만든다

```tsx
<button type="submit" disabled={submitting}>
  {submitting ? '로그인 중...' : '로그인'}
</button>
```

| submitting | disabled | 문구 |
|---:|---:|---|
| `false` | `false` | 로그인 |
| `true` | `true` | 로그인 중... |

`조건 ? 참일 때 값 : 거짓일 때 값`은 JavaScript 삼항 연산자다. `submitting` 하나에서 버튼 잠금과 문구를 함께 계산하므로 서로 모순되는 별도 state가 필요 없다.

네트워크가 매우 빠르면 사용자가 `로그인 중...`을 거의 못 볼 수 있다. 그래도 느린 네트워크에서 진행 상태를 알리고 중복 클릭을 줄이는 역할을 한다.

### error state는 조건부로 화면에 나타난다

```tsx
{error && <p className="auth-error">{error}</p>}
```

`error`가 빈 문자열이면 아무것도 렌더링하지 않는다. 오류 문자열이 있으면 `<p>`를 렌더링한다. 이것도 현재 state에서 화면을 계산하는 조건부 렌더링이다.

---

## 7. 실제 로그인은 useAuth와 authApi가 처리한다

`useAuth`의 로그인 함수는 두 요청을 순서대로 실행한다.

```tsx
const login = useCallback(async (email: string, password: string) => {
  await authApi.login(email, password);
  const authenticatedUser = await authApi.fetchMe();

  if (!authenticatedUser) {
    throw new Error('로그인 세션을 확인하지 못했습니다. 다시 시도해주세요.');
  }

  setError(null);
  setUser(authenticatedUser);
}, []);
```

실제 요청 흐름은 다음과 같다.

```text
POST /api/auth/login
→ 성공 응답의 SESSION 쿠키를 브라우저가 저장
→ GET /api/auth/me
→ 현재 사용자 정보 수신
→ setUser(authenticatedUser)
→ App 재렌더링 요청
```

관심사를 나눈 이유는 명확하다.

```text
LoginForm: 입력과 폼 UI
useAuth: 로그인 사용자 state와 인증 절차
authApi: HTTP 요청 형식
```

### state 소유권

| 소유자 | state | 범위 |
|---|---|---|
| `App` | `form` | 어떤 인증 폼을 보여 줄지 결정 |
| `LoginForm` | `email`, `password`, `error`, `submitting` | 로그인 폼 내부에서만 사용 |
| `useAuth`를 호출한 `App` | `user`, `loading`, 인증 `error` | 앱 전체 인증 상태 |

여러 화면이 알아야 하는 `user`는 LoginForm의 지역 state가 아니라 App 쪽에서 하나의 원천으로 관리한다.

---

## 8. 결과가 성공과 실패로 갈린다

### 성공 경로

```text
onLogin 완료(useAuth가 이미 setUser 요청)
→ handleSubmit의 onClose()
→ App이 전달한 () => setForm(null) 실행
→ App 재렌더링
→ user 조건에 따라 사용자 이름·로그아웃 버튼과 AuthenticatedTradingLayout 표시
→ form === 'login'이 false가 되어 LoginForm unmount
```

중요한 점은 `user`가 바뀌었다는 이유만으로 LoginForm이 자동으로 닫히는 것이 아니라는 것이다. 폼을 직접 닫는 코드는 성공 뒤 호출하는 `onClose()`다.

**unmount(언마운트)**는 컴포넌트가 React 트리에서 완전히 제거되는 일이다.

- LoginForm이 트리에서 빠지고 그 결과 form과 input DOM이 제거된다.
- `email`, `password`, `error`, `submitting` 지역 state가 폐기된다.
- Effect cleanup이 있다면 실행된다. 현재 LoginForm에는 Effect가 없다.

### 실패 경로

`authApi.login` 또는 `fetchMe`가 Error를 던지면 Promise 실패가 호출한 쪽으로 전파된다.

```text
로그인 실패
→ Error가 onLogin 밖으로 전파
→ LoginForm의 catch 실행
→ setError(오류 메시지)
→ finally에서 setSubmitting(false)
→ LoginForm 재렌더링
```

```tsx
catch (err) {
  setError(err instanceof Error ? err.message : '로그인에 실패했습니다.');
}
```

`err instanceof Error`는 잡힌 값이 Error 객체인지 검사한다. Error라면 `message`를 쓰고, 아니라면 기본 문구를 쓴다.

실패하면 LoginForm은 unmount되지 않는다.

- 입력한 이메일과 비밀번호가 유지된다.
- 오류 문구가 나타난다.
- `submitting=false`가 되어 버튼을 다시 누를 수 있다.

### 성공과 실패 비교

| 결과 | user | LoginForm | 지역 state | 다음 화면 |
|---|---|---|---|---|
| 성공 | 사용자 저장 | unmount | 폐기 | 로그인된 앱 화면 |
| 실패 | 변경 없음 | 유지·재렌더링 | 보존 | 오류와 재시도 버튼 |

---

## 9. 사용자가 취소 버튼을 누른다

```tsx
<button type="button" className="ghost" onClick={onClose}>
  취소
</button>
```

```text
사용자가 취소 버튼 클릭
→ 브라우저 click 이벤트
→ React가 onClick prop의 onClose 호출
→ App의 setForm(null)
→ App 재렌더링
→ LoginForm unmount
```

`type="button"`은 form 안에서도 submit을 시작하지 않는 일반 버튼이다. `<button>`은 form 안에서 기본 type이 submit이 될 수 있으므로, 취소 버튼에는 `type="button"`을 명시하는 것이 안전하다.

현재 취소 버튼은 요청 중에도 활성화되어 있다. 요청 중 취소하면 폼은 unmount되지만 이미 시작한 `fetch`는 계속된다. 나중에 요청이 성공하면 `setUser`가 실행되어 로그인될 수 있으므로, 현재 취소는 “요청 취소”가 아니라 “폼 닫기”다.

### 닫았다 다시 열면 왜 input이 비어 있는가?

```text
취소 또는 성공
→ LoginForm unmount
→ 기존 지역 state 폐기
→ 다시 로그인 버튼 클릭
→ 새 LoginForm mount
→ useState 초기값('', false)으로 다시 시작
```

CSS로 숨기기만 하면 state가 유지될 수 있지만, 현재 코드는 컴포넌트 자체를 제거하므로 state가 초기화된다.

---

## 10. 이벤트 핸들러와 Effect는 목적이 다르다

로그인은 사용자의 submit이라는 분명한 사건 때문에 시작하므로 이벤트 핸들러에서 실행한다.

```tsx
<form onSubmit={handleSubmit}>
```

기존 로그인 세션 확인은 화면이 나타난 뒤 서버 상태와 동기화해야 하므로 `useAuth`의 Effect에서 실행한다.

```tsx
useEffect(() => {
  authApi
    .fetchMe()
    .then(setUser)
    .catch((err) => {
      setUser(null);
      setError(err instanceof Error ? err.message : '로그인 상태를 확인하지 못했습니다.');
    })
    .finally(() => setLoading(false));
}, []);
```

```text
이벤트 핸들러: 사용자의 클릭·입력·제출에 반응
Effect: commit 뒤 외부 시스템과 동기화
```

`useAuth`는 컴포넌트가 아니라 App이 호출하는 커스텀 Hook이므로 이 Effect도 App에 속한다. 빈 의존성 배열 `[]` 때문에 초기 commit 뒤 실행되고, 같은 mount 동안 의존값 변화로 다시 실행되지 않는다. Effect가 구독·타이머 같은 작업을 시작했다면 cleanup 함수를 반환하여 재실행 전이나 unmount 때 정리한다.

---

## 11. 한 번에 복습한다

### 화면과 state 타임라인

| 단계 | form | email/password | error | submitting | LoginForm |
|---|---|---|---|---:|---|
| 앱 시작 | `null` | 없음 | 없음 | 없음 | 아직 없음 |
| 로그인 버튼 클릭 | `'login'` | `''` / `''` | `''` | `false` | mount |
| 사용자 입력 | `'login'` | 입력한 값 | `''` | `false` | re-render |
| 제출 시작 | `'login'` | 입력한 값 | `''` | `true` | 버튼 잠금 |
| 실패 | `'login'` | 유지 | 오류 문자열 | `false` | 유지·재시도 |
| 성공 | `null` | 폐기 | 폐기 | 폐기 | unmount |
| 취소 | `null` | 폐기 | 폐기 | 폐기 | unmount |

### 스스로 확인할 질문

1. 로그인 버튼을 클릭하면 왜 `LoginForm`이 mount되는가?
2. `e.target.value`의 `target`은 무엇을 가리키는가?
3. `type="submit"` 버튼에 `onClick`이 없어도 제출되는 이유는 무엇인가?
4. 로그인 실패 후 입력값이 유지되는 이유는 무엇인가?
5. 로그인 성공 후 폼을 직접 닫는 코드는 무엇인가?

## 마지막 핵심

LoginForm을 이해할 때 가장 중요한 한 줄은 다음이다.

> 사용자의 행동이 이벤트 핸들러를 실행하고, 핸들러가 state를 바꾸면 React가 컴포넌트를 다시 계산하며, ReactDOM이 필요한 차이만 실제 DOM에 반영한다.

폼이 열리고 닫히는 것은 같은 원리의 더 큰 단위다. App의 `form` state가 `<LoginForm />`을 JSX에 넣으면 mount되고, JSX에서 빼면 unmount된다.
