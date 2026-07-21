# LoginForm으로 배우는 React: useState, 입력, 제출, 리렌더링

이 문서는 React를 처음 배우는 사람이 이 프로젝트의 `LoginForm` 컴포넌트 하나를 끝까지 따라가며 React의 핵심 동작을 이해하도록 만든 입문 교재다.

예제용으로 단순화한 가상 코드가 아니라 현재 프로젝트의 실제 코드를 사용한다. 사용자가 로그인 창을 열고, 이메일과 비밀번호를 입력하고, 로그인 버튼을 누른 뒤 성공하거나 실패할 때까지 다음 질문에 답하는 것이 목표다.

- React 컴포넌트와 JSX는 무엇인가?
- `props`와 `state`는 무엇이 다른가?
- `useState()`는 값을 어디에, 어떻게 기억하는가?
- 한 글자를 입력할 때 왜 컴포넌트가 다시 렌더링되는가?
- 리렌더링은 페이지 새로고침이나 DOM 전체 재생성과 같은가?
- 로그인 버튼을 클릭하거나 Enter를 누르면 어떤 코드가 실행되는가?
- `async`/`await`로 서버를 기다리는 동안 화면은 어떻게 바뀌는가?
- 성공하면 폼이 왜 닫히고, 다시 열면 입력값이 왜 비어 있는가?
- 이 코드는 왜 화면, 인증 상태, HTTP 요청을 서로 다른 파일로 나눴는가?

> 이 장의 한 문장 요약  
> **`LoginForm`은 현재 `props + state`로 보일 화면을 JSX로 선언하고, 이벤트 핸들러는 state 변경을 요청한다. React는 새 JSX를 계산한 뒤 실제 DOM에서 필요한 부분만 갱신한다.**

---

## 1. 먼저 전체 지도를 보자

이 로그인 기능에는 네 층이 참여한다.

```text
사용자
  │ 입력, 로그인 버튼 클릭, Enter
  ▼
LoginForm.tsx
  │ email/password/error/submitting 지역 state
  │ onLogin(email, password) 호출
  ▼
App.tsx + useAuth.ts
  │ 폼 열림 상태와 로그인 사용자 상태
  │ login 함수를 props로 전달
  ▼
authApi.ts
  │ POST /api/auth/login
  │ GET  /api/auth/me
  ▼
Spring 백엔드
  │ 자격 증명 확인, 서버 세션 저장
  ▼
SESSION 쿠키 + 사용자 정보
```

파일별 책임은 다음과 같다.

| 파일 | 책임 |
|---|---|
| `frontend/src/auth/components/LoginForm.tsx` | 입력 UI, 폼의 지역 state, 제출 중 표시, 실패 메시지 |
| `frontend/src/App.tsx` | 로그인 폼을 열고 닫음, 로그인 여부에 맞는 전체 화면 선택 |
| `frontend/src/auth/hooks/useAuth.ts` | 현재 사용자 state와 로그인·로그아웃 동작 |
| `frontend/src/auth/api/authApi.ts` | 실제 HTTP 요청과 응답 오류 처리 |
| `frontend/src/shared/http.ts` | 백엔드 오류 응답을 JavaScript `Error`로 변환 |
| `src/main/java/.../auth/controller/AuthController.java` | 로그인 검증과 서버 세션 저장 |

React의 핵심을 처음 공부할 때는 아래 순서로 생각하면 된다.

```text
1. 현재 state가 무엇인가?
2. 그 state로 어떤 JSX가 반환되는가?
3. 어떤 이벤트가 state 변경을 요청하는가?
4. 다음 렌더링의 JSX는 무엇이 달라지는가?
```

---

## 2. 현재 LoginForm 전체 코드

설명용 주석을 잠시 덜어 내면 핵심 코드는 다음과 같다.

```tsx
import { useState, type FormEvent } from 'react';

type Props = {
  onLogin: (email: string, password: string) => Promise<void>;
  onClose: () => void;
};

export function LoginForm({ onLogin, onClose }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

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

  return (
    <form className="auth-form" onSubmit={handleSubmit}>
      <h2>로그인</h2>
      <input
        type="email"
        placeholder="이메일"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        required
      />
      <input
        type="password"
        placeholder="비밀번호"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        required
      />
      {error && <p className="auth-error">{error}</p>}
      <div className="auth-actions">
        <button type="submit" disabled={submitting}>
          {submitting ? '...' : '로그인'}
        </button>
        <button type="button" className="ghost" onClick={onClose}>
          취소
        </button>
      </div>
    </form>
  );
}
```

짧은 코드지만 React의 주요 개념이 거의 모두 들어 있다.

| React 개념 | 이 코드에서 찾을 위치 |
|---|---|
| 함수 컴포넌트 | `function LoginForm(...)` |
| JSX | `return (...)` |
| props | `onLogin`, `onClose` |
| state | `email`, `password`, `error`, `submitting` |
| Hook | 네 번 호출한 `useState(...)` |
| 이벤트 핸들러 | `onChange`, `onSubmit`, `onClick` |
| 제어되는 입력 | `value={email}` + `onChange` |
| 조건부 렌더링 | `error && ...`, `submitting ? ... : ...` |
| 비동기 처리 | `async`, `await onLogin(...)` |
| 부모와 자식의 협력 | 부모가 전달한 `onLogin`과 `onClose` 호출 |

### React 코드와 TypeScript 타입을 구분하기

첫 줄에는 실행 시점의 값과 타입 검사 전용 정보가 함께 있다.

```tsx
import { useState, type FormEvent } from 'react';
```

- `useState`는 브라우저에서 실제로 사용하는 React Hook이므로 실행될 JavaScript에 남는다.
- `FormEvent`는 `handleSubmit`의 이벤트 객체가 어떤 기능을 가지는지 TypeScript가 검사할 때만 쓴다.
- `type` 키워드가 붙은 import는 빌드 뒤 JavaScript에서 제거된다.

`e: FormEvent` 덕분에 TypeScript는 `e.preventDefault()`가 가능한 이벤트임을 알고 오타나 잘못된 사용을 검사할 수 있다. 타입은 실행 동작을 추가하지 않는다. 실제 제출 이벤트를 만들고 전달하는 쪽은 브라우저와 React다.

이 프로젝트의 `frontend/package.json`은 React 18.3.1 계열을 사용한다. 이 장에서 다루는 컴포넌트, props, state, 렌더 스냅샷, 제어되는 입력의 핵심 모델은 연결한 공식 학습 문서와 동일하게 적용된다.

---

## 3. 컴포넌트는 “화면을 계산하는 함수”다

`LoginForm`은 JavaScript 함수다.

```tsx
export function LoginForm({ onLogin, onClose }: Props) {
  // ...
  return (
    <form>...</form>
  );
}
```

일반 함수와 마찬가지로 입력을 받고 결과를 반환한다.

```text
입력: props와 이번 렌더링의 state
  ↓
LoginForm 함수 실행
  ↓
출력: 현재 보여야 할 화면을 설명하는 JSX
```

JSX는 HTML처럼 보이지만 HTML 문자열이 아니다. “현재 이 위치에는 `form`, `input`, `button`이 있어야 한다”는 React 요소 설명을 만든다. ReactDOM은 이전 설명과 새 설명을 비교해 브라우저의 실제 DOM을 필요한 만큼만 바꾼다.

앞 절의 `LoginForm`이 `return (...)`에서 돌려주는 것도 실제 `form` DOM 요소가 아니라, 화면 구조를 나타내는 React 요소 객체 트리다. JSX 안의 `{...}`에 있는 JavaScript 표현식을 먼저 평가한 다음, 그 결과를 자식 요소에 넣어 트리를 만든다.

전체 `return` 결과를 아주 단순화하면 다음과 같은 모양이다. 실제 React 내부 객체에는 더 많은 정보가 있지만, `type`, `props`, `children`이라는 관점으로 이해하면 충분하다.

```ts
{
  type: 'form',
  props: {
    className: 'auth-form',
    onSubmit: handleSubmit,
    children: [
      { type: 'h2', props: { children: '로그인' } },
      { type: 'input', props: { type: 'email', value: email, /* ... */ } },
      error
        ? { type: 'p', props: { className: 'auth-error', children: error } }
        : null,
      { type: 'div', props: { children: [/* 버튼 요소들 */] } }
    ]
  }
}
```

이때 `onSubmit: handleSubmit`이나 `onClick: onClose`는 함수를 지금 실행한 결과가 아니다. 나중에 제출 또는 클릭 이벤트가 발생했을 때 React가 호출할 **함수 자체를 전달**한 것이다. 반면 `{error && ...}`와 `{submitting ? '...' : '로그인'}`처럼 중괄호 안에 쓴 표현식은 이번 렌더링 중에 평가되어, 현재 state에 맞는 자식 내용이 된다.

예를 들어 사용자가 이메일을 입력하면 `onChange` 핸들러가 실행되고 `setEmail(e.target.value)`가 다음 state를 요청한다. React는 `LoginForm` 함수를 다시 호출해 새 React 요소 트리를 반환받고, 이전 트리와 비교한 뒤 `input`의 값처럼 실제로 달라진 DOM만 갱신한다.

예를 들어 아래 JSX는 개념적으로 `type`과 `props`를 가진 화면 설명이 된다.

```tsx
<input onChange={handleChange} />
```

```ts
{
  type: 'input',
  props: {
    onChange: handleChange,
  },
}
```

소문자로 쓴 `input`은 함수가 아니라 React가 아는 HTML 내장 태그 이름이고, `onChange`는 그 요소 설명의 prop이다. 이후 ReactDOM이 이 설명을 바탕으로 브라우저의 실제 input 요소를 만든다.

이를 식처럼 기억해도 좋다.

```text
UI = f(props, state)
```

현재 `error`가 빈 문자열이고 `submitting`이 `false`라면 함수는 오류 문단이 없고 활성화된 “로그인” 버튼이 있는 JSX를 반환한다. `error`와 `submitting`이 달라지면 같은 함수가 다른 JSX를 반환한다.

### 렌더링 중에는 계산만 한다

컴포넌트 본문은 여러 번 실행될 수 있다. 따라서 렌더링 중에 서버 요청을 보내거나 DOM을 직접 변경하면 안 된다. `LoginForm`은 서버 요청을 함수 본문에서 실행하지 않고 사용자의 제출로 호출되는 `handleSubmit` 안에서 실행한다.

```tsx
// 좋음: 렌더링은 화면 계산만 한다.
export function LoginForm(...) {
  async function handleSubmit(...) {
    await onLogin(...); // 사용자가 제출했을 때만 실행
  }

  return <form onSubmit={handleSubmit}>...</form>;
}
```

이벤트 핸들러는 렌더링 자체가 아니라 사용자 행동의 결과로 실행되므로 서버 요청 같은 사이드 이펙트를 두기에 알맞다.

---

## 4. props: 부모가 자식에게 주는 입력

`Props` 타입은 부모가 `LoginForm`을 사용할 때 무엇을 전달해야 하는지 정한다.

```tsx
type Props = {
  onLogin: (email: string, password: string) => Promise<void>;
  onClose: () => void;
};
```

두 props 모두 값이 아니라 **함수**다.

- `onLogin`: 이메일과 비밀번호를 받아 비동기 로그인을 수행한다.
- `onClose`: 로그인 폼을 닫는다.

부모인 `App`은 실제로 다음처럼 전달한다.

```tsx
{form === 'login' && (
  <LoginForm
    onLogin={login}
    onClose={() => setForm(null)}
  />
)}
```

왼쪽 이름은 `LoginForm`이 받을 prop 이름이고, 오른쪽 중괄호 안은 부모가 가진 실제 값이다.

```text
App의 login 함수
  └─ onLogin이라는 이름으로 LoginForm에 전달

App의 () => setForm(null) 함수
  └─ onClose라는 이름으로 LoginForm에 전달
```

### 함수를 “호출”하지 않고 “전달”한다

다음 두 코드는 완전히 다르다.

```tsx
onSubmit={handleSubmit}   // 함수 자체를 전달: 나중에 제출할 때 React가 호출
onSubmit={handleSubmit()} // 지금 렌더링하면서 즉시 호출: 잘못된 형태
```

`onLogin={login}`도 같은 원리다. `App`이 렌더링되는 순간 로그인하는 것이 아니라, `LoginForm`이 나중에 `onLogin(email, password)`로 호출할 수 있게 함수 자체를 넘긴다.

### 왜 LoginForm이 authApi를 직접 import하지 않는가?

`LoginForm`은 “입력을 받고 진행 상태와 오류를 보여 주는 UI”에 집중한다. 실제 인증 방법은 부모가 준 함수에 맡긴다.

이 분리는 다음 장점이 있다.

- 폼 UI와 HTTP 통신의 책임이 섞이지 않는다.
- `LoginForm`은 로그인 구현의 세부 사항을 몰라도 된다.
- 나중에 테스트에서 가짜 `onLogin` 함수를 전달하기 쉽다.
- 인증 방식이 바뀌어도 폼 UI의 변경을 줄일 수 있다.

props는 자식이 소유한 값이 아니다. 자식은 props를 읽고 호출할 수 있지만 직접 바꾸지 않는다. 부모의 state를 바꾸고 싶을 때는 부모가 전달한 콜백을 호출한다.

---

## 5. useState 집중 학습: 컴포넌트의 기억

일반 함수의 지역 변수는 함수 호출이 끝나면 다음 호출을 위한 기억이 되지 않는다. 또한 일반 변수를 바꿔도 React는 화면을 다시 계산해야 한다는 사실을 알 수 없다.

```tsx
import type { ChangeEvent } from 'react';

function BrokenLoginForm() {
  let email = ''; // 컴포넌트의 일반 지역 변수

  function handleChange(e: ChangeEvent<HTMLInputElement>) {
    email = e.target.value;
    // 값은 바뀌지만 React에 리렌더링을 요청하지 않는다.
    // 나중에 다른 이유로 컴포넌트가 다시 호출되면 다시 ''로 만들어진다.
  }

  return (
    <>
      <input value={email} onChange={handleChange} />
      <p>React가 마지막으로 렌더링한 값: {email}</p>
    </>
  );
}
```

화면이 렌더링 사이에 값을 기억하고, 값이 바뀔 때 화면도 갱신되어야 한다면 state가 필요하다.

```tsx
const [email, setEmail] = useState('');
```

`useState`는 정확히 두 값을 가진 배열을 반환한다.

```tsx
const emailStatePair = useState('');
const email = emailStatePair[0];
const setEmail = emailStatePair[1];
```

배열 구조 분해를 사용하면 현재 코드처럼 짧게 쓸 수 있다.

```tsx
const [email, setEmail] = useState('');
```

- `email`: **이번 렌더링에서 읽을 현재 state 값**
- `setEmail`: **다음 state를 저장하고 리렌더링을 요청하는 setter 함수**
- `''`: **이 컴포넌트가 처음 마운트될 때 사용할 초기값**

### LoginForm의 네 state

| state | 초기값 | setter | 화면에서 하는 일 | 왜 state인가? |
|---|---:|---|---|---|
| `email` | `''` | `setEmail` | 이메일 input의 `value`, 로그인 인수 | 입력할 때마다 바뀌고 렌더 사이에 기억해야 함 |
| `password` | `''` | `setPassword` | 비밀번호 input의 `value`, 로그인 인수 | 입력할 때마다 바뀌고 렌더 사이에 기억해야 함 |
| `error` | `''` | `setError` | 실패 메시지를 조건부로 표시 | 비동기 실패 후에도 화면에 남아야 함 |
| `submitting` | `false` | `setSubmitting` | 버튼 비활성화와 `...` 문구 | 네트워크 요청 중이라는 시간에 따른 상태를 보여 줘야 함 |

### 초기값은 최초 마운트에서만 사용한다

컴포넌트가 다시 렌더링될 때도 코드상 `useState('')`가 다시 호출된다. 그렇다고 이메일이 매번 빈 문자열로 초기화되지는 않는다.

```text
최초 마운트
useState('') → 초기값 ''를 저장 → email은 ''

다음 렌더링
useState('') → 기존에 React가 보관한 값을 읽음 → 예: email은 'a@b.com'
```

`''`는 최초 state를 정하는 값이다. 이후 렌더링에서 React는 해당 컴포넌트 위치와 Hook 호출 순서에 연결해 보관한 값을 돌려준다.

### state는 함수 안에 보이지만 React가 보관한다

`email` 변수가 모듈 전역에 계속 살아 있는 것이 아니다. React가 렌더 트리의 해당 `LoginForm` 위치에 state를 보관하고, 컴포넌트를 호출할 때 이번 렌더링의 값을 제공한다.

그래서 같은 화면에 `LoginForm` 두 개를 렌더링하면 각 폼은 서로 다른 `email` state를 갖는다. state는 컴포넌트 인스턴스에 지역적이고 비공개다.

### Hook을 항상 최상위에서 호출하는 이유

현재 네 `useState` 호출은 컴포넌트 최상위에 있고 매 렌더링에서 같은 순서로 호출된다.

```tsx
const [email, setEmail] = useState('');
const [password, setPassword] = useState('');
const [error, setError] = useState('');
const [submitting, setSubmitting] = useState(false);
```

React는 호출 순서를 이용해 state를 대응시킨다.

```text
첫 번째 useState  → email
두 번째 useState  → password
세 번째 useState  → error
네 번째 useState  → submitting
```

따라서 다음처럼 조건 안에 Hook을 넣으면 안 된다.

```tsx
// 잘못된 예
if (something) {
  const [email, setEmail] = useState('');
}
```

조건이 렌더링마다 달라지면 Hook 호출 순서도 달라져 React가 어느 state가 어느 호출에 속하는지 안정적으로 대응시킬 수 없다.

> Hook의 기본 규칙  
> `useState` 같은 Hook은 React 함수 컴포넌트 또는 다른 Hook의 **최상위에서만** 호출한다. 조건문, 반복문, 이벤트 핸들러 안에서 호출하지 않는다.

### TypeScript는 state 타입을 추론한다

초기값을 보고 TypeScript가 타입을 추론한다.

```tsx
useState('')    // string state
useState(false) // boolean state
```

따라서 `setEmail(false)`나 `setSubmitting('yes')` 같은 잘못된 호출은 타입 검사에서 막힌다.

---

## 6. state는 “변하는 상자”가 아니라 렌더별 스냅샷이다

React를 처음 배울 때 가장 중요한 부분이다.

```tsx
setSubmitting(true);
```

이 코드는 현재 실행 중인 `submitting` 변수를 즉시 `true`로 대입하는 명령이 아니다. React에 다음 state를 알려 주고 새로운 렌더링을 요청한다.

```tsx
console.log(submitting); // false라고 가정
setSubmitting(true);
console.log(submitting); // 이 핸들러 안에서는 여전히 false
```

현재 이벤트 핸들러는 자신이 만들어진 렌더링의 state 스냅샷을 본다.

```text
렌더링 A의 submitting = false
  └─ 렌더링 A가 만든 handleSubmit도 submitting=false를 봄

setSubmitting(true)
  └─ 렌더링 B를 요청

렌더링 B의 submitting = true
  └─ 새 JSX에서 버튼 disabled=true, 문구='...'
```

setter를 호출한 뒤 같은 함수 안에서 state 변수가 즉시 바뀌지 않는 이유는 JavaScript의 지역 변수를 React가 중간에 바꿔 끼우는 방식이 아니기 때문이다. 다음 렌더링에서 컴포넌트가 다시 호출될 때 새 state 스냅샷을 받는다.

### 제출에 사용되는 email과 password도 스냅샷이다

`handleSubmit`은 해당 렌더링이 만든 `email`과 `password`를 읽는다.

```tsx
await onLogin(email, password);
```

함수 인수는 호출 순간의 문자열 값으로 전달된다. 요청을 시작한 뒤 사용자가 input을 다시 편집하더라도 이미 시작된 요청의 이메일과 비밀번호가 바뀌지는 않는다.

현재 코드는 요청 중 로그인 버튼만 비활성화하고 input은 비활성화하지 않는다. 따라서 다음 동작이 가능하다.

```text
1. email = 'old@example.com' 상태에서 제출
2. old@example.com으로 로그인 요청 시작
3. 요청을 기다리는 동안 input을 'new@example.com'으로 수정
4. 화면 state는 new@example.com
5. 이미 시작된 요청은 old@example.com으로 계속 진행
```

문자열은 함수에 값으로 전달되므로 이후 state 변경이 과거 요청을 수정하지 않는다.

### setter에는 값 또는 업데이터 함수를 전달할 수 있다

`useState`의 setter에는 다음 state 값을 직접 전달할 수 있다.

```tsx
setEmail(e.target.value);
setError('');
setSubmitting(true);
```

현재 `LoginForm`은 다음 값이 이전 state를 계산해야만 나오는 값이 아니다. 이벤트가 제공한 문자열 또는 명확한 상수이므로 직접 전달하는 형태가 가장 단순하다.

반대로 다음 값이 이전 state에 의존한다면 업데이터 함수를 사용한다.

```tsx
const [attempts, setAttempts] = useState(0);

setAttempts((previousAttempts) => previousAttempts + 1);
```

React는 업데이터 함수를 큐에 넣고 다음 렌더링을 계산할 때 이전 값에 차례로 적용한다. 같은 이벤트에서 여러 번 증가시키는 코드라면 이 차이가 중요하다.

```tsx
setAttempts((n) => n + 1);
setAttempts((n) => n + 1);
```

반면 제출 시작과 종료는 이전 불리언을 반전하는 동작이 아니라 의도가 명확한 전환이다.

```tsx
setSubmitting(true);  // 제출 시작
setSubmitting(false); // 제출 종료
```

`setSubmitting((value) => !value)`로 쓰면 호출 횟수에 따라 뜻이 달라질 수 있어 이 코드의 의도를 오히려 흐린다.

---

## 7. 사용자가 한 글자를 입력할 때 일어나는 일

이메일 input을 보자.

```tsx
<input
  type="email"
  placeholder="이메일"
  value={email}
  onChange={(e) => setEmail(e.target.value)}
  required
/>
```

`value={email}`과 `onChange={...}`를 함께 사용하므로 이 input은 **제어되는 입력(controlled input)**이다. 화면 input의 현재 값을 React state가 결정한다.

### 예: 빈 input에 `a` 한 글자를 입력

입력 전 렌더링을 A라고 하자.

```text
렌더링 A
email state = ''
JSX: value={''}
화면 input = ''
```

이제 사용자가 `a`를 입력한다.

```text
1. 브라우저가 사용자의 키 입력을 받는다.
2. React가 input의 onChange 이벤트 핸들러를 호출한다.
3. e.target.value는 현재 입력 문자열 'a'다.
4. setEmail('a')가 다음 state를 요청한다.
5. React가 LoginForm을 다시 호출한다.
6. 새 렌더링에서 email state는 'a'다.
7. 새 JSX는 value={'a'}를 반환한다.
8. ReactDOM이 필요한 DOM 변경을 커밋한다.
```

결과는 다음과 같다.

```text
렌더링 B
email state = 'a'
JSX: value={'a'}
화면 input = 'a'
```

다음 글자를 입력할 때도 같은 과정이 반복된다.

```text
'' → 'a' → 'ab' → 'abc' → ...
 각 단계마다 onChange → setEmail → 새 렌더링
```

### 여러 글자를 입력하면 어떻게 되는가?

예를 들어 빈 input에 `lee`를 입력하면, 보통 키 입력 한 번마다 아래 과정이 반복된다.

```text
빈칸
↓ 'l' 입력
onChange → setEmail('l') → LoginForm 리렌더링 → value="l"

↓ 'e' 입력
onChange → setEmail('le') → LoginForm 리렌더링 → value="le"

↓ 'e' 입력
onChange → setEmail('lee') → LoginForm 리렌더링 → value="lee"
```

따라서 마지막에는 `email` state가 `'lee'`이고, 새 JSX의 `value={email}`도 사실상 `value="lee"`가 된다. React가 그 값을 input에 반영하므로 화면에도 `lee`가 보인다.

여기서 `setEmail(...)`이 `LoginForm` 함수를 직접 호출하는 것은 아니다. setter는 “다음 state로 다시 렌더링해 달라”고 React에 요청하고, React가 `LoginForm`을 다시 호출해 새 JSX를 계산한다.

### 그런데 왜 입력하자마자 보이는가?

키를 누르면 브라우저가 먼저 input의 값을 잠시 바꾸고, 곧바로 React의 `onChange`가 실행된다. 이어서 React가 새 state로 리렌더링하고 `value={email}`을 통해 input 값을 다시 맞춘다. 이 과정이 매우 빠르므로 사용자에게는 글자가 즉시 입력되는 것처럼 보인다.

`value={email}`이 있기 때문에 최종 값의 기준은 브라우저 DOM이 아니라 React state다. 만약 `onChange`에서 `setEmail`을 호출하지 않으면 state는 이전 값에 머물고, React는 input을 그 이전 값으로 되돌린다.

### onChange의 e는 무엇인가?

`e`는 이벤트 객체다. 어떤 요소에서 이벤트가 발생했는지, 그 요소의 현재 값이 무엇인지 같은 정보를 담는다.

```tsx
onChange={(e) => setEmail(e.target.value)}
```

- `e.target`: 이번 입력 이벤트가 발생한 input
- `e.target.value`: 사용자가 편집한 뒤의 전체 문자열
- `setEmail(...)`: 그 문자열을 다음 React state로 저장

JSX의 `<input ... />`와 `e.target`은 input 두 개가 아니다. JSX는 React에 전달하는 화면 설명이고, React는 이를 바탕으로 브라우저에 실제 input 하나를 만든다. `e.target`은 사용자가 입력하는 그 실제 input을 가리킨다.

`password` input도 똑같이 작동한다.

```tsx
value={password}
onChange={(e) => setPassword(e.target.value)}
```

`type="password"`는 화면에서 글자를 가려 주지만 state 값을 암호화하지는 않는다. 비밀번호는 폼이 마운트된 동안 JavaScript 문자열로 존재하므로 로그에 출력하거나 별도 저장소에 보관하지 않는 것이 중요하다. 이 프로젝트는 비밀번호를 `localStorage`에 저장하지 않는다.

### value만 있고 onChange가 없다면?

```tsx
<input value={email} />
```

React state가 input 값을 항상 `email`로 고정하지만 입력 이벤트에서 state를 바꿀 방법이 없다. 사용자가 타이핑해도 다음 DOM 동기화에서 원래 값으로 돌아가 사실상 읽기 전용처럼 보인다.

제어되는 입력에는 사용자의 편집을 state에 반영하는 `onChange`가 필요하다.

### value가 없다면?

```tsx
<input />
```

이 경우 브라우저 DOM이 현재 입력값을 직접 관리하는 비제어 입력이다. 제출할 때 `FormData`나 `ref`로 읽는 방식도 가능하다.

현재 코드가 제어되는 입력을 선택한 이유는 입력값을 React state와 항상 동기화해 다음 작업을 자연스럽게 만들기 위해서다.

- 제출할 때 `email`과 `password`를 바로 사용
- 입력값에 따라 안내나 검증 UI를 렌더링
- 필요할 때 코드로 입력값 초기화
- 같은 state를 다른 JSX에도 표시

### 왜 빈 문자열로 초기화하는가?

텍스트 input의 `value`는 문자열이어야 한다.

```tsx
const [email, setEmail] = useState('');
```

처음부터 `''`를 사용하면 컴포넌트의 생명주기 내내 제어되는 문자열 input으로 유지된다. `undefined`로 시작했다가 나중에 문자열로 바꾸면 비제어 입력에서 제어되는 입력으로 전환되어 React 경고와 예측하기 어려운 동작의 원인이 될 수 있다.

---

## 8. 리렌더링은 페이지 새로고침이 아니다

setter가 다른 state를 예약해 React가 업데이트를 수행하면 화면 갱신은 개념적으로 세 단계를 거친다.

```text
1. 트리거
   setEmail('a')가 렌더링을 요청

2. 렌더링
   React가 LoginForm 함수를 다시 호출해 새 JSX를 계산

3. 커밋
   이전 결과와 새 결과를 비교해 실제 DOM의 필요한 부분만 반영
```

그 뒤 브라우저가 변경된 화면을 페인트한다.

setter에 현재 state와 `Object.is` 비교로 같은 값을 전달하면 React가 불필요한 리렌더링을 생략할 수 있다. 따라서 setter는 “DOM을 반드시 다시 그리는 명령”이 아니라 state 업데이트를 React에 요청하는 함수로 이해하는 편이 정확하다.

중요한 구분은 다음과 같다.

| 용어 | 뜻 |
|---|---|
| 리렌더링 | React가 컴포넌트 함수를 다시 호출해 새 화면 설명을 계산 |
| 커밋 | 계산 결과 중 필요한 변경을 실제 DOM에 적용 |
| 브라우저 페인트 | DOM과 스타일 결과를 실제 픽셀로 그림 |
| 페이지 새로고침 | 문서와 JavaScript 앱을 처음부터 다시 로드 |

이메일 한 글자를 입력했다고 페이지 전체를 새로고침하지 않는다. `LoginForm`이 새 JSX를 계산하고 ReactDOM이 달라진 input 값처럼 필요한 부분만 DOM에 반영한다.

또한 “컴포넌트가 리렌더링되었다”와 “그 컴포넌트가 만든 모든 DOM을 버리고 다시 만들었다”는 같은 말이 아니다. 계산은 다시 할 수 있지만 DOM은 차이가 있을 때만 최소한으로 바뀐다.

### 부모 App도 매 글자마다 다시 렌더링되는가?

`email`은 `LoginForm`의 지역 state다. `setEmail`은 해당 컴포넌트의 업데이트를 예약한다. 따라서 이메일 한 글자를 입력하기 위해 `App`의 state를 바꿀 필요가 없다.

이처럼 자주 바뀌는 입력 state를 필요한 작은 컴포넌트 가까이에 두면 업데이트 범위와 책임이 명확해진다.

다만 이것이 “`LoginForm`은 자기 state가 바뀔 때만 렌더링된다”는 뜻은 아니다. 부모가 자기 props나 state 때문에 다시 렌더링되면 자식 컴포넌트도 다시 호출될 수 있다. 실제 프로젝트에서는 실시간 시세를 받는 `useOrderBookStream`이 `App`의 `snapshot` state를 계속 갱신하므로, 로그인 폼이 열려 있다면 `App`과 함께 `LoginForm`도 다시 렌더링될 수 있다.

그때도 같은 렌더 트리 위치에 같은 `LoginForm`이 유지되므로 `email`과 `password` state는 초기화되지 않는다.

```text
LoginForm의 setEmail
  → LoginForm 업데이트를 직접 요청

App의 실시간 snapshot 변경
  → App 렌더링
  → 자식 LoginForm도 다시 렌더링될 수 있음
  → 같은 위치이므로 LoginForm의 지역 state는 보존
```

---

## 9. 로그인 버튼 클릭과 Enter가 같은 곳으로 모이는 이유

폼은 다음처럼 제출 핸들러를 가진다.

```tsx
<form className="auth-form" onSubmit={handleSubmit}>
```

로그인 버튼은 제출 버튼이다.

```tsx
<button type="submit" disabled={submitting}>
  {submitting ? '...' : '로그인'}
</button>
```

사용자가 이 버튼을 클릭하면 브라우저의 폼 제출 동작이 발생하고 `form`의 `onSubmit`으로 연결된다. 유효한 input에서 Enter를 눌러 제출해도 같은 `handleSubmit`으로 들어온다.

버튼의 `onClick`에 로그인 로직을 넣지 않고 폼의 `onSubmit`에 둔 이유는 다음과 같다.

- 버튼 클릭과 Enter 제출을 한 코드로 처리한다.
- HTML 폼의 의미와 키보드 사용 방식을 유지한다.
- 브라우저의 기본 입력 검증과 함께 동작한다.
- 제출 로직이 한 곳에 있어 중복이 줄어든다.

### 취소 버튼은 왜 type="button"인가?

```tsx
<button type="button" className="ghost" onClick={onClose}>
  취소
</button>
```

`form` 안의 `button`은 `type`을 생략하면 기본적으로 제출 버튼처럼 동작할 수 있다. 취소를 눌렀는데 로그인 제출까지 발생하면 안 되므로 `type="button"`을 명시한다.

### required와 type="email"은 언제 동작하는가?

```tsx
<input type="email" required />
<input type="password" required />
```

브라우저가 먼저 HTML 제약 검증을 수행한다.

- `required`: 빈 값을 허용하지 않는다.
- `type="email"`: 브라우저가 이메일 형식으로 해석하고 기본 형식 검증을 한다.

제약을 통과하지 못하면 일반적인 사용자 제출에서는 브라우저가 제출을 막고 `handleSubmit`이 호출되지 않는다. 통과하면 `onSubmit`이 실행된다.

이 검증은 사용자 경험을 돕는 프론트 검증이다. 요청은 조작될 수 있으므로 백엔드 검증을 대신할 수 없다. 이 프로젝트의 백엔드도 로그인 요청의 이메일과 비밀번호에 `@NotBlank` 검증을 적용한다.

---

## 10. handleSubmit을 한 줄씩 실행해 보기

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

### 10.1 e.preventDefault()

```tsx
e.preventDefault();
```

HTML 폼은 원래 데이터를 전송하고 페이지를 이동하거나 새로고침하는 기본 동작을 할 수 있다. 이 프로젝트는 JavaScript `fetch`로 API를 호출하고 React 화면을 유지해야 하므로 기본 제출 동작을 막는다.

> `preventDefault()`는 이벤트 전파를 막는 함수가 아니다. 브라우저가 그 이벤트에 기본으로 수행하려던 동작을 막는다.

### 10.2 이전 오류 지우기

```tsx
setError('');
```

이전 로그인 시도가 실패해 오류가 남아 있을 수 있다. 새 시도를 시작할 때 빈 문자열로 바꿔 오류 문단이 렌더링되지 않게 한다.

### 10.3 제출 중 상태로 전환

```tsx
setSubmitting(true);
```

다음 렌더링에서는 아래 두 표현이 새 state를 읽는다.

```tsx
disabled={submitting}
{submitting ? '...' : '로그인'}
```

따라서 버튼은 비활성화되고 문구는 `...`로 바뀐다.

직접 DOM을 찾는 명령은 없다.

```tsx
// React 코드에는 이런 명령형 조작이 필요 없다.
button.disabled = true;
button.textContent = '...';
errorElement.remove();
```

대신 state에 맞는 UI를 선언한다.

```tsx
<button disabled={submitting}>
  {submitting ? '...' : '로그인'}
</button>
```

이것이 React의 선언형 UI다.

### 10.4 여러 setter는 배칭될 수 있다

`setError('')`와 `setSubmitting(true)`는 같은 제출 이벤트의 동기 구간에서 연달아 호출된다. React는 일반적으로 이런 업데이트를 모아 처리해 불필요한 중간 렌더링을 줄인다. 이를 배칭(batching)이라고 한다.

```text
이전 화면
error = '비밀번호가 올바르지 않습니다.'
submitting = false

같은 이벤트에서
setError('')
setSubmitting(true)

다음 화면
error = ''
submitting = true
```

“오류만 지워진 중간 화면”과 “그 다음 로딩 화면”을 반드시 각각 따로 그릴 필요 없이 일관된 다음 화면을 계산할 수 있다.

배칭과 별개로 각 setter는 현재 지역 변수를 즉시 바꾸는 함수가 아니라 다음 렌더링을 위한 업데이트를 요청하는 함수라는 점을 기억해야 한다.

### 10.5 부모의 로그인 함수를 기다림

```tsx
await onLogin(email, password);
```

`onLogin`의 타입은 다음과 같다.

```tsx
(email: string, password: string) => Promise<void>
```

- 문자열 두 개를 받는다.
- 비동기 작업의 완료 또는 실패를 나타내는 `Promise`를 반환한다.
- `void`이므로 완료 뒤 `LoginForm`에 사용자 값을 직접 반환하지 않는다.
- 성공하면 Promise가 이행(resolve)되고 다음 줄로 진행한다.
- 실패하면 Promise가 거부(reject)되어 `catch`로 이동한다.

`await`로 기다리는 동안 브라우저의 JavaScript 메인 스레드가 네트워크 응답만 바라보며 멈춰 있는 것은 아니다. 함수의 나머지 실행을 나중으로 미루고 브라우저는 입력, 렌더링 등 다른 작업을 계속 처리할 수 있다.

이 때문에 요청을 기다리는 동안 `submitting=true`인 화면을 사용자에게 보여 줄 수 있다.

### 10.6 성공하면 폼 닫기

```tsx
onClose();
```

`onLogin`이 정상 완료되면 부모가 준 `onClose`를 호출한다. 실제 함수는 다음과 같다.

```tsx
() => setForm(null)
```

`App`의 `form` state가 `null`로 바뀌면 조건이 거짓이 된다.

```tsx
{form === 'login' && <LoginForm ... />}
```

그 결과 `LoginForm`이 렌더 트리에서 제거되고 폼이 화면에서 사라진다.

### 10.7 실패하면 오류 state 저장

```tsx
catch (err) {
  setError(err instanceof Error ? err.message : '로그인에 실패했습니다.');
}
```

`onLogin`이 던진 값이 JavaScript `Error`라면 구체적인 `message`를 사용한다. 그렇지 않으면 안전한 기본 메시지를 사용한다.

다음 렌더링에서 이 JSX가 오류를 보여 준다.

```tsx
{error && <p className="auth-error">{error}</p>}
```

빈 문자열은 falsy이므로 아무것도 렌더링하지 않는다. 오류 문자열은 truthy이므로 `p` 요소를 렌더링한다.

### 10.8 finally에서 버튼 복원

```tsx
finally {
  setSubmitting(false);
}
```

`finally`는 성공과 실패 양쪽에서 실행된다.

- 실패: 폼이 남아 있으므로 버튼을 다시 활성화하고 “로그인” 문구로 되돌린다.
- 성공: `onClose`로 폼이 제거되므로 복원된 버튼을 사용자가 보지는 않는다.

실패 경로에서 `finally`가 없다면 버튼이 계속 비활성화되어 재시도할 수 없는 버그가 생길 수 있다.

---

## 11. 성공 경로: 실제 프로젝트 전체 흐름

로그인에 성공할 때는 `LoginForm` 밖의 코드까지 이어진다.

### 11.1 App이 useAuth의 login을 전달

`App.tsx`:

```tsx
const {
  user,
  loading,
  error: authError,
  login,
  signup,
  logout,
  expireSession,
} = useAuth();

{form === 'login' && (
  <LoginForm onLogin={login} onClose={() => setForm(null)} />
)}
```

`LoginForm` 안에서 부르는 `onLogin`은 실제로 `useAuth`가 반환한 `login`이다.

### 11.2 useAuth가 두 API를 순서대로 호출

`useAuth.ts`:

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

여기서 `useCallback`은 로그인 요청을 즉시 실행하는 함수가 아니다. 안쪽 함수 정의를 렌더링 사이에 재사용할 수 있게 해 주는 별도의 React Hook이다. 이 장에서는 최적화 세부 사항보다 **`login(email, password)`을 호출하면 두 API를 기다리고, 성공 시 `user` state를 바꾼다**는 동작에 집중하면 된다.

흐름은 다음과 같다.

```text
1. POST /api/auth/login
   이메일·비밀번호 확인
   성공하면 서버 세션 생성 및 SESSION 쿠키 발급

2. GET /api/auth/me
   방금 생긴 세션으로 현재 사용자 정보 조회

3. setUser(authenticatedUser)
   앱 전체에서 사용할 로그인 사용자 state 갱신

4. login Promise 완료
   LoginForm의 await 다음 줄로 복귀

5. onClose()
   LoginForm 제거
```

로그인 API가 사용자 정보를 직접 반환하지 않기 때문에 `fetchMe()`를 한 번 더 호출한다. `credentials: 'include'` 설정으로 브라우저가 `SESSION` 쿠키를 요청에 포함한다.

### 11.3 user state가 전체 화면을 바꿈

`App`은 `user` 유무로 헤더와 거래 화면을 조건부 렌더링한다.

```tsx
{user ? (
  <>
    <span>{user.displayName || user.email}님</span>
    <button onClick={logout}>로그아웃</button>
  </>
) : (
  <>
    <button onClick={() => setForm('login')}>로그인</button>
    <button onClick={() => setForm('signup')}>회원가입</button>
  </>
)}
```

즉, `LoginForm`이 헤더 DOM을 직접 찾아 바꾸는 것이 아니다.

```text
로그인 성공
  ↓
setUser(authenticatedUser)
  ↓
App 리렌더링
  ↓
user가 있는 경우의 JSX 선택
  ↓
로그인/회원가입 버튼 대신 사용자/로그아웃 표시
```

이것도 선언형 UI다.

---

## 12. 실패 경로: 서버 오류가 화면에 도착하는 과정

잘못된 비밀번호처럼 첫 로그인 요청이 실패하면 아래 방향으로 전달된다.

```text
백엔드가 4xx/5xx 응답
  ↓
authApi.login이 res.ok === false 확인
  ↓
toHttpError가 응답의 message를 Error로 변환
  ↓
authApi.login이 throw
  ↓
useAuth.login의 Promise가 reject
  ↓
LoginForm의 catch 실행
  ↓
setError(err.message)
  ↓
LoginForm 리렌더링
  ↓
오류 <p> 표시
```

`authApi.ts`의 핵심은 다음과 같다.

```tsx
export async function login(
  email: string,
  password: string,
): Promise<void> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) {
    throw await toHttpError(res, '로그인에 실패했습니다.');
  }
}
```

실패를 반환값으로 숨기지 않고 `throw`하므로 UI의 `try/catch`가 성공과 실패 흐름을 분명하게 나눌 수 있다.

실패 지점은 첫 `POST /api/auth/login`에만 있는 것이 아니다. POST가 성공해도 `useAuth.login`은 곧바로 `GET /api/auth/me`로 세션 사용자를 확인한다.

```text
POST /api/auth/login
  ├─ 4xx/5xx 또는 네트워크 실패
  │    └─ Error throw → LoginForm catch
  │
  └─ 성공
       ↓
     GET /api/auth/me
       ├─ 401 → fetchMe가 null 반환
       │         └─ useAuth가
       │            "로그인 세션을 확인하지 못했습니다" Error throw
       ├─ 5xx 또는 네트워크 실패
       │    └─ Error throw
       └─ 사용자 응답
            └─ setUser → 최종 성공
```

따라서 “POST가 200을 반환했다”만으로 이 프론트 로그인 흐름 전체가 성공한 것은 아니다. POST와 그 뒤의 세션 사용자 확인이 모두 성공해야 `onLogin` Promise가 정상 완료되고 `onClose`가 실행된다. 어느 단계에서든 거부되면 같은 `LoginForm`의 지역 `catch`에서 메시지를 표시한다.

실패하면 `onClose`가 호출되지 않으므로 폼은 계속 마운트되어 있다. 따라서 다음 state가 보존된다.

- 사용자가 입력했던 `email`
- 사용자가 입력했던 `password`
- 새로 저장된 `error`
- `finally`가 되돌린 `submitting=false`

사용자는 오류를 보고 입력을 수정한 뒤 다시 제출할 수 있다.

---

## 13. 지역 state와 앱 state를 나눈 이유

이 기능에는 서로 범위가 다른 state가 있다.

### LoginForm만 알아야 하는 지역 state

```text
email
password
error
submitting
```

폼이 사라지면 함께 사라져도 되는 값들이다. 다른 화면이 이 값을 직접 알 필요가 없다.

### App이 알아야 하는 상위 state

```text
form: 어떤 인증 폼이 열렸는가
user: 누가 로그인했는가
loading: 최초 세션 확인 중인가
authError: 앱 수준 인증 오류가 있는가
```

헤더와 거래 레이아웃처럼 여러 UI 결정에 영향을 주므로 상위에서 소유한다.

| 정보 | 소유자 | 이유 |
|---|---|---|
| input의 현재 문자열 | `LoginForm` | 해당 폼에서만 필요 |
| 제출 중 여부 | `LoginForm` | 해당 로그인 시도의 UI에만 필요 |
| 로그인 폼 열림 여부 | `App` | `LoginForm`을 만들거나 제거해야 함 |
| 로그인 사용자 | `App`이 호출한 `useAuth` | 헤더와 인증 화면 전체가 사용 |

필요한 여러 컴포넌트가 같은 값을 공유해야 한다면 가장 가까운 공통 부모로 state를 끌어올린다. 반대로 한 컴포넌트만 필요한 값은 그 컴포넌트 가까이에 둔다.

> 주의  
> `useAuth`라는 이름 때문에 자동 전역 저장소라고 생각하면 안 된다. 현재 프로젝트에서는 `App`이 `useAuth()`를 한 번 호출하고 그 결과를 아래로 전달하기 때문에 하나의 인증 state 원천처럼 쓰인다. 다른 컴포넌트가 `useAuth()`를 별도로 호출하면 별도의 state가 생긴다.

### submitting과 loading은 왜 둘 다 있는가?

이름은 비슷하지만 의미와 소유자가 다르다.

| state | 의미 |
|---|---|
| `LoginForm.submitting` | 사용자가 지금 이 폼에서 로그인 요청을 진행 중 |
| `useAuth.loading` | 앱 시작 시 기존 세션의 사용자 정보를 확인 중 |

하나로 합치면 서로 다른 작업의 상태가 뒤섞인다.

---

## 14. 조건부 렌더링으로 상태를 화면에 표현한다

### 오류가 있을 때만 문단 표시

```tsx
{error && <p className="auth-error">{error}</p>}
```

`&&`는 JavaScript의 논리 AND 연산자다. 여기서는 앞의 `error` 값을 조건처럼 사용한다. `&&`는 왼쪽 값이 falsy이면 오른쪽을 평가하지 않고 왼쪽 값을 그대로 반환하는 **단축 평가(short-circuit evaluation)** 규칙을 따른다.

따라서 이 JSX는 개념적으로 아래 `if` 문과 같다.

```tsx
if (error) {
  return <p className="auth-error">{error}</p>;
}
```

`error`가 빈 문자열(`''`)이면 falsy이므로 표현식의 결과도 빈 문자열이고, React는 이를 화면에 렌더링하지 않는다. 반대로 오류 메시지처럼 내용이 있는 문자열은 truthy이므로 오른쪽 JSX가 결과가 되어 오류 문단을 렌더링한다.

| error 값 | JavaScript 평가 | 렌더링 결과 |
|---|---|---|
| `''` | falsy | 아무 문단도 없음 |
| `'로그인 실패'` | truthy | 오류 `p` 렌더링 |

별도의 `hasError` state는 필요하지 않다. `error !== ''` 여부는 `error`에서 계산할 수 있기 때문이다.

### 제출 상태로 버튼 두 속성 결정

```tsx
<button type="submit" disabled={submitting}>
  {submitting ? '...' : '로그인'}
</button>
```

| submitting | disabled | 버튼 문구 |
|---:|---:|---|
| `false` | `false` | 로그인 |
| `true` | `true` | ... |

`buttonDisabled`와 `buttonText`를 별도 state로 저장하지 않는다. 둘 다 `submitting`에서 계산되는 값이기 때문이다.

이것은 state 설계의 중요한 원칙이다.

> **렌더링 중 기존 props나 state에서 계산할 수 있는 값은 중복 state로 저장하지 않는다.**

중복 state가 많으면 서로 맞지 않는 상태가 생길 수 있다. 예를 들어 `submitting=false`인데 `buttonText='...'`인 모순이 가능해진다. 현재 코드는 하나의 원천에서 두 UI 값을 계산하므로 그런 모순을 피한다.

---

## 15. 폼을 닫았다 다시 열면 state가 초기화되는 이유

`App`은 조건이 참일 때만 `LoginForm`을 렌더링한다.

```tsx
{form === 'login' && <LoginForm ... />}
```

### 열 때

```text
form: null
  ↓ 사용자가 로그인 버튼 클릭
setForm('login')
  ↓
form === 'login'이 true
  ↓
LoginForm 최초 마운트
  ↓
email='', password='', error='', submitting=false
```

### 닫을 때

```text
onClose()
  ↓
setForm(null)
  ↓
form === 'login'이 false
  ↓
LoginForm이 렌더 트리에서 제거(unmount)
  ↓
LoginForm의 지역 state도 제거
```

### 다시 열 때

이전 컴포넌트가 다시 나타나는 것이 아니라 새 `LoginForm` 인스턴스가 마운트된다. 따라서 네 `useState` 초기값으로 시작한다.

만약 조건부로 제거하지 않고 CSS로 숨기기만 했다면 컴포넌트는 같은 트리 위치에 계속 남아 state도 보존될 수 있다.

React state는 코드 줄 자체가 아니라 렌더 트리의 컴포넌트 위치에 연결된다는 점이 핵심이다.

---

## 16. 왜 네 state를 하나의 객체로 합치지 않았는가?

다음처럼 쓸 수도 있다.

```tsx
const [formState, setFormState] = useState({
  email: '',
  password: '',
  error: '',
  submitting: false,
});
```

하지만 객체 state는 한 필드만 바꿀 때 기존 필드를 복사해야 한다.

```tsx
setFormState((prev) => ({
  ...prev,
  email: e.target.value,
}));
```

현재 네 값은 작은 원시값이고 변경 이유가 비교적 독립적이다. 별도 `useState`로 두면 각 업데이트가 단순하고 읽기 쉽다.

```tsx
setEmail(e.target.value);
setSubmitting(true);
setError('');
```

무조건 분리하거나 무조건 합쳐야 하는 규칙은 없다.

- 항상 함께 바뀌며 하나의 개념을 이루면 묶는 것을 고려한다.
- 독립적으로 바뀌는 작은 값이면 분리하는 편이 단순할 수 있다.
- state 조합이 복잡해져 불가능한 상태가 많아지면 `useReducer`나 명시적인 상태 모델을 고려한다.

예를 들어 폼이 더 복잡해지면 제출 단계와 실패 메시지를 판별 가능한 유니온 하나로 표현할 수도 있다.

```tsx
type SubmissionState =
  | { status: 'typing' }
  | { status: 'submitting' }
  | { status: 'error'; message: string };

const [submission, setSubmission] = useState<SubmissionState>({
  status: 'typing',
});
```

이 구조에서는 `status === 'error'`일 때만 `message`가 존재하므로 단계와 오류 메시지가 어긋나는 상태를 타입으로 줄일 수 있다. 하지만 현재 작은 폼에서는 `submitting`과 `error`가 각각 무엇을 위한 값인지 명확해 네 state 구조도 충분히 이해하기 쉽다.

---

## 17. 왜 useEffect로 로그인하지 않는가?

다음처럼 email이나 password가 바뀔 때 Effect에서 로그인하면 안 된다.

```tsx
// 잘못된 설계 예
useEffect(() => {
  login(email, password);
}, [email, password, login]);
```

이 코드는 글자를 입력할 때마다 로그인 요청을 보낼 수 있다.

로그인은 “컴포넌트가 화면에 나타났기 때문에 외부 시스템과 동기화해야 하는 작업”이 아니라 “사용자가 폼을 제출했기 때문에 수행하는 작업”이다. 원인이 명확한 사용자 이벤트이므로 `handleSubmit`에서 실행하는 것이 자연스럽다.

```text
입력 이벤트 → 입력 state만 변경
제출 이벤트 → 로그인 요청
```

이벤트와 Effect를 구분하는 좋은 기준이다.

---

## 18. 현재 구현에서 정확히 알아둘 세부 동작

아래는 코드가 잘못되었다는 뜻이 아니라, 현재 코드 그대로 실행했을 때의 정확한 동작과 다음 개선을 고민할 지점이다.

### 18.1 요청 중에도 input은 편집할 수 있다

`submitting`은 로그인 버튼의 `disabled`에만 연결되어 있다. 이메일·비밀번호 input에는 `disabled={submitting}`이 없다.

따라서 요청 중 입력을 바꿀 수 있지만 이미 보낸 요청의 인수는 바뀌지 않는다. 요청 중 폼을 완전히 고정하고 싶다면 input에도 `disabled={submitting}`을 사용할 수 있다.

### 18.2 요청 중에도 취소할 수 있고, 요청은 중단되지 않는다

취소 버튼도 비활성화되지 않는다.

```text
로그인 요청 시작
  ↓
사용자가 취소 → LoginForm 제거
  ↓
이미 시작된 fetch는 계속 진행
  ↓
나중에 성공하면 useAuth의 user state는 갱신될 수 있음
```

현재 `fetch`에는 `AbortController`가 연결되어 있지 않기 때문이다. 제품 요구사항에 따라 다음 중 하나를 선택할 수 있다.

- 요청 중 취소 버튼 비활성화
- 취소는 폼만 닫고 로그인 요청은 계속 허용
- 취소 시 `AbortController`로 브라우저의 `fetch` 대기 중단 시도

마지막 선택은 `signal`을 `LoginForm → useAuth.login → authApi.login/fetchMe`까지 전달하도록 인터페이스를 함께 설계해야 한다. 또한 `fetch` 중단은 브라우저가 응답을 기다리는 일을 그만두게 할 뿐, 서버가 이미 처리한 로그인이나 생성한 세션을 자동으로 되돌리는 트랜잭션 롤백은 아니다.

취소로 `LoginForm`이 이미 제거된 뒤 요청이 실패하면 비동기 함수의 `catch`와 `finally`는 실행될 수 있지만, 제거된 폼의 지역 `setError`와 `setSubmitting`이 폼을 다시 화면에 만들지는 않는다.

### 18.3 disabled는 일반적인 중복 클릭을 막는다

첫 제출 뒤 `submitting=true`가 렌더링되면 로그인 버튼이 비활성화되어 일반적인 연속 클릭을 막는다.

다만 `handleSubmit` 안에 `if (submitting) return` 같은 별도 방어가 있는 것은 아니다. 사람의 일반적인 UI 조작에는 버튼 비활성화가 충분할 수 있지만, 중복 요청을 절대 허용할 수 없는 작업이라면 프론트와 서버 양쪽의 추가 방어를 고려해야 한다.

### 18.4 오류는 타이핑할 때 자동으로 사라지지 않는다

`setError('')`는 새 유효 제출이 시작될 때 실행된다. 사용자가 오류를 본 뒤 input을 편집해도 오류 문구는 그대로 남아 있다.

입력 변경 시 오류를 지우고 싶다면 `onChange` 핸들러에서 함께 처리할 수 있다. 다만 UX 정책의 선택이지 `useState`의 필수 규칙은 아니다.

### 18.5 브라우저 검증이 막은 제출은 handleSubmit까지 오지 않는다

이메일 형식이 잘못됐거나 필수값이 비어 있으면 브라우저가 제출을 막는다. 그러면 `setError('')`도 실행되지 않아 이전 서버 오류가 남을 수 있다.

### 18.6 접근성을 더 보강할 수 있다

현재 input은 `placeholder`만 있고 명시적인 `label`, `name`, `autoComplete`가 없다. 오류 문단도 `role="alert"`가 없다.

React state 학습과 별개로 실제 제품 폼에서는 다음을 고려할 수 있다.

- 이메일과 비밀번호에 연결된 `label`
- `name="email"`, `name="password"`
- `autoComplete="email"`, `autoComplete="current-password"`
- 오류 메시지의 `role="alert"` 또는 `aria-live`

### 18.7 개발 StrictMode가 로그인 요청을 두 번 보내는 것은 아니다

이 프로젝트의 `App`은 개발 환경에서 `StrictMode` 안에 있다. StrictMode는 순수하지 않은 렌더링 코드를 찾기 위해 컴포넌트 렌더링을 추가로 실행할 수 있다.

그러나 사용자의 클릭이나 제출 이벤트 핸들러를 StrictMode가 자동으로 두 번 호출하는 것은 아니다. 로그인 요청을 렌더링 본문이 아니라 `handleSubmit`에 둔 것이 중요한 이유이기도 하다.

다만 `useAuth`에는 앱 마운트 시 기존 세션을 확인하는 `useEffect(..., [])`가 있다. 현재 그 Effect에는 요청 취소 cleanup이 없으므로 React 18 개발 StrictMode의 추가 setup 검사에서 `GET /api/auth/me`가 두 번 보일 수 있다.

```text
개발 StrictMode에서 두 번 보일 수 있음
  GET /api/auth/me  ← 마운트 Effect의 세션 확인

사용자 제출 한 번으로 자동 중복되지 않음
  POST /api/auth/login ← handleSubmit 이벤트
```

Network 탭에서 중복 `/me`를 봤다면 먼저 이 개발 전용 Effect 검사를 구분해야 한다.

---

## 19. 처음부터 끝까지 상태표로 복습

예를 들어 사용자가 로그인에 한 번 실패하고 두 번째에 성공한다고 하자.

| 단계 | email | password | error | submitting | 화면 |
|---|---|---|---|---:|---|
| 폼 최초 열림 | `''` | `''` | `''` | `false` | 빈 input, 로그인 버튼 |
| 이메일 입력 | `'a@b.com'` | `''` | `''` | `false` | 이메일 표시 |
| 비밀번호 입력 | `'a@b.com'` | `'wrong'` | `''` | `false` | 두 input 표시 |
| 첫 제출 시작 | `'a@b.com'` | `'wrong'` | `''` | `true` | 버튼 비활성화, `...` |
| 첫 제출 실패 | `'a@b.com'` | `'wrong'` | 오류 문자열 | `false` | 오류 표시, 버튼 복원 |
| 비밀번호 수정 | `'a@b.com'` | `'correct'` | 오류 문자열 | `false` | 새 비밀번호 state, 기존 오류 유지 |
| 두 번째 제출 | `'a@b.com'` | `'correct'` | `''` | `true` | 오류 제거, `...` |
| 로그인 성공 | 폼 제거 | 폼 제거 | 폼 제거 | 폼 제거 | 사용자 헤더와 인증 화면 |

React가 DOM을 직접 조작하라는 명령을 받는 것이 아니라, 각 행의 state에 맞는 JSX를 `LoginForm`과 `App`이 계산한다는 점을 확인하자.

---

## 20. 자주 하는 오해

### “useState('')는 리렌더링 때마다 email을 비우나요?”

아니다. `useState` 호출은 매 렌더링에 존재하지만 초기값은 최초 마운트에 사용된다. 이후에는 React가 보존한 state를 돌려준다.

### “setEmail은 email 변수에 대입하는 함수인가요?”

아니다. 다음 state를 저장하고 리렌더링을 요청한다. 현재 핸들러의 `email`은 그 렌더링의 스냅샷이다.

### “한 글자마다 리렌더링하면 DOM 전체를 다시 만드나요?”

아니다. React는 JSX를 다시 계산할 수 있지만 커밋 단계에서는 실제 차이가 있는 DOM만 갱신한다.

### “로그인 버튼의 onClick이 handleSubmit을 호출하나요?”

현재 버튼에는 `onClick`이 없다. `type="submit"` 버튼이 폼 제출을 일으키고 `form onSubmit`이 `handleSubmit`을 호출한다. Enter 제출도 같은 경로다.

### “e.preventDefault()가 React 리렌더링을 막나요?”

아니다. 브라우저의 기본 폼 제출과 페이지 이동을 막는다. React setter가 요청하는 리렌더링과는 다른 개념이다.

### “useAuth는 전역 상태인가요?”

Hook 자체가 자동 전역 상태를 만들지는 않는다. 현재는 `App`이 한 번 호출한 결과를 사용하므로 앱의 인증 상태 원천 역할을 한다.

### “비밀번호 input이면 state도 암호화되나요?”

아니다. `type="password"`는 화면 표시를 가릴 뿐이다. 네트워크 보안은 HTTPS, 서버 저장 보안은 해시 같은 별도 계층이 담당한다.

### “로그인 성공 값을 왜 LoginForm이 직접 받지 않나요?”

`onLogin`은 `Promise<void>`다. 사용자 정보는 `useAuth`가 `setUser`로 상위 state에 저장한다. 폼은 성공/실패와 완료 시점만 알면 된다.

---

## 21. 이 컴포넌트에서 얻어야 할 React 핵심

### 1. 선언형 UI

DOM을 직접 바꾸는 절차 대신 현재 state에서 어떤 JSX가 보여야 하는지 작성한다.

```tsx
{error && <p>{error}</p>}
<button disabled={submitting}>
  {submitting ? '...' : '로그인'}
</button>
```

### 2. 컴포넌트

`LoginForm`은 props와 state로 JSX를 계산하는 독립된 UI 함수다.

### 3. props

부모가 자식에게 값과 동작을 전달한다. 자식은 `onLogin`과 `onClose`로 부모에게 사용자 의도를 알린다.

### 4. state

렌더링 사이에 기억해야 하고 화면에 영향을 주는 값을 담는다.

### 5. useState

현재 state 스냅샷과 다음 state를 예약하는 setter를 제공한다.

### 6. 이벤트

`onChange`는 입력을 state로 옮기고, `onSubmit`은 로그인이라는 사용자 의도를 처리한다.

### 7. 제어되는 입력

`value`의 원천이 React state이며 `onChange`가 사용자의 편집을 다시 state에 반영한다.

### 8. 렌더링과 커밋

setter가 렌더링을 요청하면 React가 컴포넌트를 다시 호출해 JSX를 계산하고 ReactDOM이 필요한 DOM 변경만 적용한다.

### 9. state 소유권

폼 전용 state는 `LoginForm`에, 앱 전체 사용자 state는 상위 `useAuth`에 둔다.

### 10. state 보존과 초기화

같은 렌더 트리 위치에 컴포넌트가 유지되면 state도 유지된다. `LoginForm`이 제거되면 지역 state도 사라지고 새로 열 때 초기화된다.

---

## 22. 직접 해 볼 실습

아래 실습은 한 번에 하나씩 적용하고 브라우저에서 확인한 뒤 되돌리는 방식으로 진행하자.

### 실습 1: 입력 state 화면에 출력

비밀번호는 출력하지 말고 이메일만 임시로 표시한다.

```tsx
<p>현재 email state: {email}</p>
```

한 글자마다 문구가 바뀌는 것을 보며 `onChange → setEmail → 리렌더링`을 확인한다.

### 실습 2: setter 직후 스냅샷 확인

이메일 핸들러를 잠시 함수로 분리하고, 기존 이메일 input의 `onChange`도 새 함수로 교체한다.

```tsx
import type { ChangeEvent } from 'react';

function handleEmailChange(e: ChangeEvent<HTMLInputElement>) {
  console.log('setter 전:', email);
  setEmail(e.target.value);
  console.log('setter 후:', email);
}

<input
  type="email"
  value={email}
  onChange={handleEmailChange}
/>
```

두 로그가 같은 이전 값을 보는 이유를 렌더별 스냅샷으로 설명해 본다. 새 state는 다음 렌더링에서 보인다.

### 실습 3: 요청 중 input 잠그기

```tsx
<input
  disabled={submitting}
  value={email}
  onChange={(e) => setEmail(e.target.value)}
/>
```

`submitting` 하나가 버튼과 input의 UI를 함께 결정하는 모습을 확인한다.

### 실습 4: 입력하면 이전 오류 지우기

```tsx
import type { ChangeEvent } from 'react';

function handleEmailChange(e: ChangeEvent<HTMLInputElement>) {
  setEmail(e.target.value);
  setError('');
}

<input
  type="email"
  value={email}
  onChange={handleEmailChange}
/>
```

한 사용자 이벤트 안에서 두 state 업데이트가 일어나는 경우를 관찰한다.

### 실습 5: 상태 단계로 리팩터링 설계만 해 보기

코드를 바로 바꾸기 전에 종이에 다음 전이를 그린다.

```text
typing
  └─ submit → submitting
                  ├─ success → 폼 닫기
                  └─ failure → error
                                  └─ submit → submitting
```

그다음 `submitting` 불리언과 `error` 문자열 구조가 현재 규모에 적절한지, 16절의 판별 가능한 `SubmissionState` 유니온이 더 나은지 비교한다.

---

## 23. 스스로 답해 볼 질문

1. `email`을 일반 `let` 변수로 바꾸면 어떤 두 문제가 생기는가?
2. `setEmail` 호출 직후 같은 핸들러에서 `email`은 왜 이전 값인가?
3. `value={email}`만 있고 `onChange`가 없다면 왜 입력하기 어려운가?
4. 로그인 버튼 클릭과 Enter 제출은 어디에서 같은 흐름으로 합쳐지는가?
5. `e.preventDefault()`가 없다면 브라우저의 기본 폼 동작이 무엇을 할 수 있는가?
6. `submitting` 하나가 화면의 어떤 두 부분을 결정하는가?
7. 로그인 실패 뒤 이메일과 비밀번호가 남는 이유는 무엇인가?
8. 성공 또는 취소 뒤 다시 폼을 열면 값이 비어 있는 이유는 무엇인가?
9. `LoginForm`이 `authApi.login`을 직접 호출하지 않고 prop을 사용하는 장점은 무엇인가?
10. 요청 중 email을 편집해도 이미 전송한 요청 값이 바뀌지 않는 이유는 무엇인가?

### 짧은 답

1. 렌더 사이에 안정적으로 보존되지 않고, 변경해도 React에 리렌더링을 요청하지 못한다.
2. state는 렌더별 스냅샷이고 setter는 다음 렌더링을 예약하기 때문이다.
3. React가 `value`를 고정하지만 사용자의 변경을 state에 반영할 코드가 없기 때문이다.
4. `form`의 `onSubmit={handleSubmit}`에서 합쳐진다.
5. 현재 문서로 데이터를 제출하고 페이지 이동 또는 새로고침을 일으킬 수 있다.
6. 버튼의 `disabled`와 버튼 문구를 결정한다.
7. 실패 시 `LoginForm`이 언마운트되지 않고 해당 지역 state가 유지되기 때문이다.
8. `LoginForm`이 트리에서 제거되며 지역 state도 폐기되고, 다시 열 때 새로 마운트되기 때문이다.
9. UI와 인증 구현의 책임 분리, 재사용, 테스트 대역 주입이 쉬워진다.
10. 제출 핸들러가 해당 렌더링의 문자열 스냅샷을 함수 인수로 이미 전달했기 때문이다.

---

## 24. 다음 학습 순서

이 장을 이해했다면 같은 프로젝트에서 다음 순서로 확장해 보자.

1. `SignupForm.tsx`  
   `LoginForm`과 비교해 state가 하나 더 늘어났을 때 구조를 확인한다.

2. `App.tsx`  
   `form`과 `user`에 따른 조건부 렌더링, 자식에게 props를 전달하는 방식을 본다.

3. `useAuth.ts`  
   여러 React Hook을 조합한 커스텀 Hook과 앱 수준 state를 공부한다.

4. `authApi.ts`  
   React 바깥의 일반 TypeScript 비동기 함수와 HTTP 책임 분리를 본다.

5. `OrderForm.tsx`  
   더 많은 입력 state, 선택값, 숫자 변환이 있는 폼으로 확장한다.

관련 선행 문서:

- [React, JSX, 컴포넌트 기초](./03-react-jsx-components-basics.md)
- [JavaScript 객체 구조 분해](./04-javascript-object-destructuring.md)
- [브라우저 이벤트 루프와 async/await](./05-browser-event-loop-async-await.md)
- [메인 스레드, 콜 스택, 이벤트 루프](./06-main-thread-call-stack-and-event-loop.md)

---

## 25. 공식 React 문서와 이 코드의 연결

사용자가 지정한 공식 학습 장:

- [빠르게 시작하기](https://ko.react.dev/learn)  
  컴포넌트, JSX, 이벤트, state 등 매일 사용하는 React 개념의 전체 입구다.
- [UI 표현하기](https://ko.react.dev/learn/describing-the-ui)  
  `LoginForm`을 함수 컴포넌트로 만들고 JSX, props, 조건부 렌더링으로 화면을 설명하는 기반이다.
- [상호작용 추가하기](https://ko.react.dev/learn/adding-interactivity)  
  `onChange`와 `onSubmit` 이벤트, state 기억, 렌더별 스냅샷, 업데이트 큐를 이해하는 기반이다.
- [State 관리하기](https://ko.react.dev/learn/managing-state)  
  폼의 시각적 상태를 선언하고 state를 잘 구조화하며 소유권을 나누는 기반이다.

이 장에서 직접 연결한 세부 공식 문서:

- [이벤트에 응답하기](https://ko.react.dev/learn/responding-to-events)
- [State: 컴포넌트의 기억 저장소](https://ko.react.dev/learn/state-a-components-memory)
- [렌더링 그리고 커밋](https://ko.react.dev/learn/render-and-commit)
- [스냅샷으로서의 State](https://ko.react.dev/learn/state-as-a-snapshot)
- [state 업데이트 큐](https://ko.react.dev/learn/queueing-a-series-of-state-updates)
- [State를 사용해 Input 다루기](https://ko.react.dev/learn/reacting-to-input-with-state)
- [State 구조 선택하기](https://ko.react.dev/learn/choosing-the-state-structure)
- [컴포넌트 간 State 공유하기](https://ko.react.dev/learn/sharing-state-between-components)
- [State를 보존하고 초기화하기](https://ko.react.dev/learn/preserving-and-resetting-state)
- [useState API](https://ko.react.dev/reference/react/useState)
- [React input API](https://ko.react.dev/reference/react-dom/components/input)
- [Hook의 규칙](https://ko.react.dev/reference/rules/rules-of-hooks)

---

## 마지막 핵심 정리

`LoginForm`을 읽을 때 아래 흐름이 머릿속에 그려지면 이 장의 목표를 달성한 것이다.

```text
사용자가 입력
  ↓
onChange
  ↓
setEmail / setPassword
  ↓
React가 새 state 스냅샷으로 LoginForm 렌더링
  ↓
ReactDOM이 필요한 input DOM 변경만 커밋

사용자가 로그인 버튼 클릭 또는 Enter
  ↓
form의 onSubmit
  ↓
preventDefault
  ↓
error='', submitting=true 예약
  ↓
onLogin(email, password)
  ↓
POST login → SESSION 쿠키 → GET me → setUser
  ├─ 성공: onClose → LoginForm 제거 → 지역 state 폐기
  └─ 실패: setError → 오류 표시, setSubmitting(false) → 재시도
```

React의 핵심은 “클릭하면 DOM을 이렇게 바꿔라”가 아니다.

> **현재 state가 이 값이면 UI는 이런 모습이어야 한다고 선언하고, 이벤트가 state 변경을 요청하게 만드는 것.**

`LoginForm`은 바로 그 React 사고방식을 작게 압축한 실제 사례다.
