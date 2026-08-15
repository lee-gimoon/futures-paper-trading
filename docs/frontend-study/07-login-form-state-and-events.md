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

이 프로젝트에서는 `.tsx` 파일에 JSX로 화면 구조를 작성한다. TypeScript의 `"jsx": "react-jsx"` 설정(`frontend/tsconfig.json`)은 이 JSX를 `react` 패키지의 `jsx-runtime` 호출 코드로 변환한다. 또한 `react`에서 `useState` 같은 상태 기능을 가져오고, `react-dom`의 `ReactDOM.createRoot(...).render(<App />)`로 React가 계산한 화면을 브라우저 DOM에 표시한다. 이처럼 화면 구조·상태·렌더링에 React와 ReactDOM을 사용하므로 React 프로젝트라고 한다.

### React 코드가 브라우저에 전달되는 과정

`frontend/package.json`의 `"build": "tsc && vite build"`는 먼저 TypeScript 타입을 검사한 뒤, `vite build`로 애플리케이션 코드와 import된 React·ReactDOM 패키지 코드를 브라우저가 실행할 JavaScript 결과물로 만드는 빌드 명령이다.

#### 로컬 개발과 배포의 전달 방식

로컬 개발에서 `npm run dev`를 실행하면 Vite 개발 서버가 브라우저 요청에 맞춰 React 앱 코드를 변환해 직접 전달한다. 이때는 `vite build`나 `dist` 폴더를 사용하지 않는다.

배포할 때는 `npm run build`의 `vite build`가 `dist` 결과물을 만들고, Spring·Nginx·CDN 같은 서버가 그 파일을 브라우저에 전달한다.

## Docker 배포에서 React 앱이 전달되는 과정

현재 구조는 **백엔드 API와 프론트엔드 정적 파일을 Spring Boot 하나가 함께 제공하는 방식**이다. Vite 개발 서버는 배포 환경에서 실행하지 않고, Vite가 미리 빌드한 React 정적 파일을 Spring Boot JAR에 포함한다. 실행 중인 Spring Boot는 API 요청과 이 정적 파일 요청을 모두 처리한다.

```text
Docker 빌드
→ RUN npm run build
  → Vite가 React 앱을 frontend/dist로 빌드

→ COPY --from=frontend-build /workspace/frontend/dist/ ./src/main/resources/static/
  → Dockerfile이 dist 파일을 Spring Boot의 표준 정적 파일 위치에 복사

→ RUN ./gradlew bootJar --no-daemon -x test
  → static 파일을 포함한 Spring Boot JAR 생성
  → JAR 내부에는 대략 BOOT-INF/classes/static/으로 들어감

→ java -jar app.jar
  → Spring Boot 실행
  → 브라우저가 정적 파일 URL을 요청하면, Spring Boot의 정적 리소스 자동 설정(ResourceHttpRequestHandler)이
    `classpath:/static/`에서 요청 경로에 해당하는 리소스를 찾아 HTTP 응답 본문으로 전송한다.

사용자 홈페이지 접속
→ 브라우저가 GET / 요청
→ Spring Boot가 static/index.html 응답
→ 브라우저가 HTML의 script·link를 읽고 JS·CSS를 자동 요청
→ Spring Boot가 static/assets/... 파일을 응답
→ 브라우저가 React 앱을 실행하고 화면을 렌더링
```

---

## 1. 사용자가 페이지에 접속한다: React 앱 시작

브라우저는 `index.html`을 읽어 실제 DOM 요소인 `<div id="root">`를 만들고, module script로 연결된 `main.tsx`를 실행한다. `main.tsx`의 React 시작 코드는 다음과 같다.

이후에는 **기존 로그인 세션이 없는 사용자가 로그인 버튼을 눌러 로그인에 성공하는 흐름**을 기준으로 살펴본다. 기존 세션이 있는 경우와 로그인 실패·취소 경로도 해당 시점에서 함께 비교한다.

```tsx
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

### 1-1. React root를 만들고 첫 렌더링을 요청한다

코드를 한 줄씩 나누면 다음과 같다.

```text
document.getElementById('root')
→ index.html에 이미 존재하는 실제 DOM div#root를 찾는다

ReactDOM.createRoot(div#root)
→ div#root 내부를 관리할 React root 객체를 만든다

root.render(<App />)
→ 이 root에 App 컴포넌트를 처음 렌더링해 달라고 요청한다
```

`root`는 `div#root`에 연결된 React root 객체이며, React가 관리하는 DOM 영역은 `div#root` 내부다. 이 프로젝트에서는 `div#root` 바깥의 DOM을 React가 관리하지 않는다.

최초 렌더링의 트리거는 `render(<App />)`이다. 이후에는 state setter가 업데이트를 예약하거나, 부모 컴포넌트가 다시 렌더링되어 자식에게 새 props를 전달하는 등의 이유로 관련 컴포넌트가 재렌더링될 수 있다. 이 로그인 흐름에서는 주로 state 변경을 보게 된다.

### 1-2. `<App />`, 컴포넌트, JSX는 각각 무엇인가?

**컴포넌트(component)**는 React가 렌더링 중 호출하는 함수다. 컴포넌트는 props를 입력으로 받을 수 있고 JSX, `null` 등 이번 화면에 필요한 결과를 반환한다. `App.tsx`의 `App`도 함수형 컴포넌트다.

```tsx
export default function App() {
  // Hook 호출과 일반 JavaScript 코드

  return (
    <div className="app">
      {/* 현재 state에 맞는 화면 */}
    </div>
  );
}
```

`<App />`은 `App()`을 직접 실행하는 함수 호출이 아니라, 이 위치에 App 컴포넌트를 렌더링하라고 작성하는 JSX다. `root.render(<App />)`가 최초 렌더링을 요청하면 React가 `App` 함수를 호출하고, App이 반환한 JSX를 바탕으로 화면을 만든다.

**JSX**는 JavaScript/TypeScript 안에서 UI 구조를 작성하는 문법이다.

```tsx
<button onClick={logout}>로그아웃</button>
```

Vite는 JSX를 브라우저가 실행할 JavaScript로 변환한다. 브라우저가 변환된 코드를 실행하면 실제 DOM 버튼이 즉시 생기는 것이 아니라, 먼저 React가 처리할 **React 요소**가 만들어진다.

React 요소는 화면에 필요한 UI 구조를 담은 **JavaScript 객체 형태의 설명서**라고 생각하면 된다. 실제 DOM은 아니다.

```text
type: button
props:
  onClick: logout 함수
  children: "로그아웃"
```

여기서:

- `type`: 어떤 UI인지 — `button`, `div`, 또는 `LoginForm` 같은 컴포넌트
- `props`: 그 UI에 전달할 값 — `onClick`, `className` 등
- `children`: 내부에 들어갈 내용 — 글자나 다른 JSX

JSX가 중첩되면 이 설명도 부모·자식 구조가 된다.

```tsx
<div>
  <h1>로그인</h1>
  <button>로그인</button>
</div>
```

```text
div
├─ h1
│  └─ "로그인"
└─ button
   └─ "로그인"
```

이 부모·자식 설명 구조를 **React 요소 트리**라고 한다. state·props·context의 변경 등으로 재렌더링이 요청되면, React는 새 요소 트리를 계산한다. 그리고 이전 요소 트리와 비교해 버튼을 새로 만들지, 문구만 바꿀지, 폼을 제거할지를 결정한다. ReactDOM은 그 결정대로 실제 브라우저 DOM을 수정한다. 이 요소 트리가 실제 DOM으로 이어지는 순서는 바로 다음 render·commit 설명에서 확인한다.

### 1-3. React, ReactDOM, 브라우저가 나누어 맡는 일

세 역할을 구분하면 렌더링이라는 말이 덜 모호해진다.

| 주체 | 이 프로젝트에서 하는 일 |
|---|---|
| React | 컴포넌트를 호출하고 Hook의 state를 관리하며 다음 UI 구조를 결정한다. |
| ReactDOM | React가 결정한 내용을 웹 브라우저의 실제 DOM에 반영한다. |
| 브라우저 | JavaScript를 실행하고, DOM을 바탕으로 스타일·레이아웃·페인트를 수행해 픽셀을 표시한다. |

React의 화면 갱신은 크게 **render 단계**와 **commit 단계**로 나뉜다.

```text
render
→ React가 App과 필요한 하위 컴포넌트를 호출한다
→ 현재 props와 state를 바탕으로 이번 화면의 React 요소 트리를 계산한다
→ 이전 결과와 비교해 필요한 DOM 변경을 결정한다

commit
→ ReactDOM이 render 단계에서 결정된 DOM 생성·수정·삭제를 적용한다

browser paint
→ 브라우저가 변경된 DOM을 실제 화면의 픽셀로 그린다
```

따라서 React에서 “렌더링된다”는 말은 페이지 전체 새로고침과 다르다. state가 바뀌면 React가 컴포넌트를 다시 호출할 수 있지만, ReactDOM은 실제로 달라진 DOM만 commit한다.

컴포넌트 함수는 render 단계에서 여러 번 호출될 수 있으므로 **순수하게** 작성해야 한다. 같은 props와 state라면 같은 JSX를 반환해야 하고, 렌더링 중 서버 요청을 보내거나 외부 값을 임의로 변경하면 안 된다. 서버 통신처럼 렌더링 밖의 작업은 이벤트 핸들러나 Effect에서 실행한다.

### 1-4. `StrictMode`는 개발 중 문제를 찾는다

`<React.StrictMode>`는 화면에 별도의 DOM을 추가하는 컴포넌트가 아니다. 개발 환경에서 잘못된 렌더링 부수 효과나 Effect 정리 문제를 찾도록 검사를 강화한다.

```tsx
<React.StrictMode>
  <App />
</React.StrictMode>
```

개발 환경에서는 StrictMode 때문에 컴포넌트 함수가 추가로 호출될 수 있고, Effect도 `setup → cleanup → setup` 검사를 한 번 더 거칠 수 있다. 운영 빌드에서는 이 개발용 추가 검사가 실행되지 않는다. 따라서 `console.log` 횟수를 컴포넌트의 실제 mount 횟수로 단정하면 안 되고, 컴포넌트와 Effect는 반복 실행되어도 문제가 없도록 작성해야 한다.

### 1-5. App의 최초 render에서 Hook을 호출한다

`root.render(<App />)`가 최초 렌더링을 요청하면 React가 App을 호출하고, App은 render 중 Hook을 호출한 뒤 JSX를 반환한다. 그 결과가 commit되어(즉, JSX에 해당하는 UI가 브라우저 DOM에 처음 반영되면) App의 **mount(마운트)**가 완료된다.

**mount(마운트)**는 React가 컴포넌트를 자신의 **컴포넌트 트리**에 처음 추가하여, 해당 컴포넌트의 상태와 이벤트 등을 관리하기 시작하는 것을 말한다. `<App />` 자체가 브라우저의 실제 `<App>` DOM 태그로 생성되는 것은 아니다. 이 예제에서는 `App`이 반환한 `div`, `button` 같은 UI가 commit 과정에서 브라우저 DOM에 처음 반영되는 시점에 `App`도 mount된다. 단, 컴포넌트가 `null`을 반환하는 경우처럼 DOM 요소가 없어도 컴포넌트는 mount될 수 있다.

컴포넌트가 이미 mount된 뒤에는 state·props·context 변경으로 화면이 다시 반영되어도 mount가 아니라 **update(업데이트)**다. 반대로 조건부 렌더링 결과에서 컴포넌트가 사라지면 **unmount(언마운트)**라고 한다. 예를 들어 나중에 `form`이 `null`에서 `'login'`으로 바뀌어 `LoginForm`이 처음 나타나면 LoginForm이 mount되고, 다시 `null`이 되어 사라지면 unmount된다.

App의 인증 관련 코드는 다음과 같다.

```tsx
export default function App() {
  const { user, loading, error: authError, login, signup, logout, expireSession } = useAuth();
  const [form, setForm] = useState<FormMode>(null);

  return (
    // 현재 user, loading, form에 맞는 JSX
  );
}
```

#### 꼭 알아야 할 Hook 핵심 개념

여기서 처음 **Hook(훅)**이 나온다.<br />
Hook은 함수형 컴포넌트가 React 기능을 요청하고 연결할 때 호출하는 `use...` 형태의 특별한 함수다.<br />
컴포넌트 함수는 렌더링될 때마다 처음부터 다시 실행된다.<br />
Hook을 사용하면 React가 컴포넌트별 state를 다음 렌더링까지 기억한다(React가 mount된 컴포넌트의 state를 관리하기 때문이다).<br />
또한 React는 Hook으로 등록한 Effect를 DOM 반영 뒤에 실행한다.

- `useState`: React에게 이 컴포넌트의 값을 렌더링 사이에도 기억해 달라고 요청한다.
- `useEffect`: React에게 DOM 반영 뒤 이 작업을 실행해 달라고 등록한다.
- `useAuth`: 이 프로젝트가 만든 커스텀 Hook으로, 내부에서 다른 React Hook을 조합해 인증 관련 state와 함수를 제공한다.

Hook은 다음 규칙을 지켜야 한다.

1. React 컴포넌트 또는 다른 커스텀 Hook 안에서 호출한다.
2. 함수의 최상위에서 호출한다.
3. 조건문·반복문·중첩 함수 안에서 호출하지 않는다.

React는 변수 이름이 아니라, **컴포넌트 함수가 위에서 아래로 실행되며 Hook을 호출한 순서**로 state와 Effect를 구분한다. 예를 들어 첫 번째 `useState`는 첫 번째 state 자리와 연결되고, 다음 렌더링에서도 같은 순서로 호출되어야 이전 state를 올바르게 이어서 사용할 수 있다. 어떤 렌더링에서는 첫 번째 Hook을 호출하고 다른 렌더링에서는 건너뛰면 React가 state의 자리를 올바르게 연결할 수 없다.

#### Hook의 작동 과정을 이해하는 기본 예시

`useState`를 하나만 사용하는 간단한 컴포넌트를 보면 Hook의 기본 동작을 확인할 수 있다.

```tsx
import { useState } from 'react';

export function Counter() {
  const [count, setCount] = useState(0);

  return (
    <div>
      <p>현재 값: {count}</p>
      <button onClick={() => setCount(count + 1)}>1 증가</button>
    </div>
  );
}
```

처음 `<Counter />`가 렌더링되면 React가 `Counter()`를 호출한다.

```text
첫 렌더링
React → Counter() 호출
      → useState(0) 실행
      → JSX 반환
      → DOM에 표시
```

버튼을 눌러 `setCount(1)`이 호출되면 `count` state가 바뀌므로 React가 `Counter()`를 다시 호출한다.

**즉, state가 업데이트되면 React는 그 state를 소유한 컴포넌트를 다시 렌더링하며, 그 과정에서 컴포넌트 함수가 다시 실행된다.**

```text
버튼 클릭
setCount(1)
  ↓
React → Counter() 다시 호출
      → useState(0) 호출
      → 이전 state인 1을 count로 받음
      → 새 JSX 반환
```

다시 렌더링될 때도 `useState(0)`은 호출되지만, `0`은 최초 mount 때만 사용하는 초기값이다. 이후에는 React가 저장한 이전 state를 `count`로 돌려준다.

### 1-6. `useAuth`는 컴포넌트가 아니라 커스텀 Hook이다

`useAuth()`는 JSX를 반환하는 컴포넌트가 아니다. 커스텀 Hook은 Hook 규칙을 지키면서 다른 Hook을 조합하는 JavaScript 함수다. 이름을 `use`로 시작하는 것은 React 개발자들이 따르는 관례이며, Hook 린터가 커스텀 Hook으로 인식해 Hook 규칙을 검사할 수 있게 한다.

커스텀 Hook은 여러 state·Effect·이벤트 처리 로직을 하나로 묶어 컴포넌트에서 재사용하고, 컴포넌트가 화면 구성에 집중하도록 돕기 위해 만든다.

```tsx
export function useAuth() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Effect와 인증 함수들

  return { user, loading, error, login, signup, logout, expireSession };
}
```

커스텀 Hook이 별도의 컴포넌트 인스턴스나 전역 singleton state를 만드는 것은 아니다.<br />
App이 `useAuth()`를 호출했으므로 이 Hook의 `user`, `loading`, `error` state는 App의 Hook state 일부로 React가 관리한다.<br />
다른 컴포넌트가 `useAuth()`를 별도로 호출하면 같은 state를 공유하는 것이 아니라 그 컴포넌트에 연결된 새 state가 생긴다.<br />
`useAuth`는 관련 state와 동작을 다루는 코드를 별도 파일로 정리해 준다.

### 1-7. `useState`는 렌더링 사이에 값을 기억한다

App의 `form` state는 어떤 인증 폼을 보여 줄지 기억한다.

```tsx
type FormMode = 'login' | 'signup' | null;

const [form, setForm] = useState<FormMode>(null);
```

`useState`는 두 값을 배열로 반환한다.

```text
form
→ 현재 렌더링에서 사용할 state 값

setForm
→ 다음 state를 요청하는 setter 함수
```

최초 render에서 state를 초기화할 때 초기값 `null`이 사용된다. `null`은 로그인 폼도 회원가입 폼도 열지 않은 상태다. 이후 재렌더링에서는 `useState(null)` 코드가 다시 실행되어도 React가 기억한 최신 state를 돌려주므로 매번 `null`로 초기화되지 않는다.

일반 지역 변수는 컴포넌트가 다시 렌더링될 때마다 새로 만들어져 이전 값을 잃는다. 반면 React state는 React가 별도로 저장해 두므로, 컴포넌트가 계속 화면에 있는 동안 다음 렌더링에서도 마지막 값을 사용할 수 있다.

### 1-8. 첫 render는 초기 state로 화면을 결정한다

App의 상단 인증 영역은 `loading`과 `user`로 화면을 선택한다.

```tsx
{loading ? null : user ? (
  <>
    <span className="auth-user">{user.displayName || user.email}님</span>
    <button className="ghost" onClick={logout}>
      로그아웃
    </button>
  </>
) : (
  <>
    <button onClick={() => setForm('login')}>로그인</button>
    <button onClick={() => setForm('signup')}>회원가입</button>
  </>
)}
```

이것이 **조건부 렌더링**이다. 별도의 HTML 페이지를 고르는 것이 아니라, JSX의 **이 위치**에 이번 render에서 어떤 React 요소를 둘지 state로 결정한다.

위 코드의 조건은 상단 인증 영역만 결정하며, 다음 순서로 읽는다.

```text
loading=true
→ 아직 로그인 상태를 확인하는 중
→ 이 위치에는 null: 상단 인증 버튼 영역을 비워 둠

loading=false, user=User 객체
→ 사용자 이름과 로그아웃 버튼

loading=false, user=null
→ 로그인·회원가입 버튼
```

여기서 `user=null`은 “현재 로그인 사용자가 없다”는 뜻이고, `loading=true`는 “그 판단이 아직 확정되지 않았다”는 뜻이다. 그래서 인증 확인 전에는 로그인·회원가입 버튼을 잠시 숨긴다.

`null`을 반환해도 App 전체가 사라지는 것은 아니다. 이 조건식이 있는 상단 인증 영역만 비어 있다.

`<>...</>`는 **Fragment**다. 형제 요소 여러 개를 묶되 불필요한 `<div>` DOM은 만들지 않는다.

첫 render에서 `form=null`이므로 아래 조건도 거짓이다. 따라서 `LoginForm`은 아직 렌더링되지 않고 mount되지도 않는다.

```tsx
{form === 'login' && <LoginForm onLogin={login} onClose={() => setForm(null)} />}
```

### 1-9. 첫 commit 뒤 Effect가 기존 세션을 확인한다

페이지를 새로 열었을 때는 사용자가 버튼을 누르지 않아도 기존 SESSION 쿠키가 유효한지 확인해야 한다.<br />
같은 일반 브라우저 프로필의 탭은 SESSION 쿠키를 공유하지만, 각 탭의 React state는 별개다.<br />
`useAuth`는 `useEffect`에서 서버에 현재 로그인 상태를 확인하고, 응답에 맞춰 React의 `user` state를 갱신한다.

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

**Effect**는 React가 DOM을 commit한 뒤, React 바깥의 시스템과 동기화하려고 실행하는 코드다. React 바깥의 시스템이란 React가 직접 상태를 관리하지 않는 대상, 즉 서버·브라우저·타이머·다른 라이브러리 등을 말한다.

`useEffect`는 이러한 Effect를 컴포넌트에 등록하는 React Hook이다. React 공식 설명으로는 “컴포넌트를 외부 시스템과 동기화할 수 있게 해 주는 Hook”이다. 즉, 화면을 그리는 render 로직과 API 요청·이벤트 구독·타이머 같은 외부 작업을 분리하고, 필요한 시점에 실행하거나 정리(cleanup)하도록 React에 알려 준다.

여기서 동기화란 React state가 바뀌었을 때 외부 시스템에도 그 변화를 반영하거나, 외부 시스템의 현재 상태를 읽어 React state에 반영해 둘의 상태를 맞추는 일이다. 예를 들어 채팅방 ID가 바뀌면 새 채팅 서버에 연결하고, 타이머가 필요 없어지면 중지하며, 서버 응답이 오면 화면 state를 갱신한다.

render의 역할은 props와 state로 JSX를 계산하는 것이다. 서버 요청·이벤트 구독·타이머·브라우저 API처럼 React 밖에 영향을 주는 작업을 render 중에 하면 render할 때마다 의도치 않게 반복될 수 있다. 그래서 DOM을 먼저 반영한 뒤 Effect에서 처리한다.

`useEffect`의 두 번째 인자인 의존성 배열은 “Effect를 언제 다시 실행할지” 정하는 관찰 목록이다.

```tsx
useEffect(실행할_함수, [관찰할_값]);
```

배열 안의 값이 이전 render와 달라지면 Effect를 다시 실행한다. 빈 배열 `[]`은 관찰할 값이 없다는 뜻이므로, 이 Effect는 컴포넌트가 처음 화면에 나타난 뒤에만 실행된다. 이후 `setUser`나 `setLoading`으로 재render되어도 다시 실행되지 않는다.

개발 StrictMode에서는 첫 mount 때 Effect의 `setup → cleanup → setup` 검사가 추가로 수행될 수 있다. 현재 Effect는 cleanup이나 요청 취소가 없으므로 개발 환경에서 `GET /api/auth/me`가 두 번 전송될 수 있다. 운영 빌드에서는 이 개발용 추가 검사가 없다.

`fetchMe()` 요청이 끝나면 응답에 따라 `user`와 `loading` state가 갱신되고, App의 상단 인증 UI는 다음처럼 갈린다.

```text
기존 세션 있음
→ fetchMe()가 User 반환
→ user=User, loading=false
→ 사용자 이름과 로그아웃 버튼 표시

기존 세션 없음
→ /api/auth/me가 401
→ fetchMe()가 null 반환
→ user=null, loading=false
→ 로그인·회원가입 버튼 표시
```

---

## 2. 사용자가 App의 로그인 버튼을 클릭한다

세션 확인이 끝나고 `user=null`이면 App은 로그인 버튼을 렌더링한다.

```tsx
<button onClick={() => setForm('login')}>로그인</button>
```

### 2-1. 이벤트 핸들러는 사용자 행동에 반응한다

브라우저가 클릭을 감지하면 ReactDOM의 이벤트 시스템이 이 버튼의 `onClick`에 전달된 함수를 호출한다.

```text
사용자가 로그인 버튼 클릭
→ 브라우저 click 이벤트 발생
→ onClick에 등록된 함수 실행
→ setForm('login') 호출
```

`onClick`은 JSX에서 사용하는 이벤트 prop이고, `() => setForm('login')`은 나중에 클릭되었을 때 실행할 **이벤트 핸들러 함수**다.

참고로 `<button>`은 JSX 요소이며, `onClick`은 이 요소에 전달되어 클릭 시의 동작을 지정하는 속성(prop)이다.

```tsx
onClick={() => setForm('login')} // 함수를 전달: 클릭할 때 실행
onClick={setForm('login')}       // 렌더링 중 즉시 호출: 잘못된 형태
```

React는 이벤트가 생길 때 호출할 함수를 prop으로 받는다. 함수 호출 결과를 넘기는 것이 아니라 함수 자체를 넘긴다는 점이 중요하다.

참고로 **속성(property)**은 객체 안에 저장된 하나의 항목을 말한다. 객체에서는 `키: 값` 형태로 표현하며, 키는 속성 이름이고 값은 속성 값이다.

```js
{
  onLogin: login,
  onClose: close,
}
```

위 객체에서 `onLogin: login`과 `onClose: close`가 각각 하나의 속성이다. `onLogin`, `onClose`는 속성 이름이고, `login`, `close`는 그 속성에 저장된 함수 값이다. React에서는 컴포넌트에 전달한 이러한 속성들을 줄여서 props라고 부른다.

### 2-2. setter는 state를 즉시 대입하지 않고 다음 렌더링을 요청한다

```tsx
setForm('login');
```

이 코드는 `form = 'login'` 같은 변수 대입이 아니다. React에 다음 state가 `'login'`이라고 알리고 App의 재렌더링을 예약한다.

state는 각 렌더링에서 고정된 **스냅샷(snapshot)**처럼 동작한다. 즉, `form`은 현재 호출된 App 함수가 React에게서 받은 값이며, setter를 호출해도 이 호출 안에서 이미 받은 `form` 값 자체가 바뀌지는 않는다. React가 다음 렌더링을 처리할 때 App을 다시 호출하고, 그 새 호출에 변경된 state를 전달한다.

```text
현재 렌더링의 form
→ null

setForm('login') 호출
→ 현재 함수 안의 form을 즉시 바꾸지 않음
→ 다음 렌더링을 요청

다음 렌더링에서 App이 읽는 form
→ 'login'
```

따라서 setter 호출 직후 같은 이벤트 핸들러 안에서 `form`을 읽으면 아직 현재 렌더링의 값인 `null`이다. 새 값은 다음 렌더링에서 받는다.

```tsx
function App() {
  const [form, setForm] = useState<FormMode>(null);

  function openLoginForm() {
    setForm('login');
    console.log(form); // null: 현재 렌더링의 스냅샷
  }

  return <button onClick={openLoginForm}>로그인</button>;
}
```

`openLoginForm`은 함수 선언만으로는 일반 함수지만, `onClick={openLoginForm}`으로 클릭 이벤트에 전달했기 때문에 이벤트 핸들러가 된다. 이 이벤트 핸들러가 끝난 뒤 React가 재렌더링을 처리하면 App 함수가 다시 호출되고, 그때의 `form`은 `'login'`이다. 그 새 값으로 조건부 렌더링을 다시 계산하므로 `LoginForm`이 JSX 결과에 포함된다.

### 2-3. 조건부 렌더링이 LoginForm을 mount한다

App이 다시 호출되면 같은 JSX 조건을 새 `form` 값으로 평가한다.

```tsx
{form === 'login' && (
  <LoginForm
    onLogin={login}
    onClose={() => setForm(null)}
  />
)}
```

JavaScript의 `&&` 단축 평가 때문에 결과가 달라진다.

```text
form === 'login'이 false
→ 표현식의 값은 false
→ React는 false를 화면에 렌더링하지 않으므로 이 위치에 LoginForm이 없음

form === 'login'이 true
→ 오른쪽 <LoginForm />을 결과에 포함
```

React는 이전 render 결과와 다음 render 결과를 비교한다. 이 비교를 **reconciliation(재조정)**이라고 한다.

```text
이전 render: 이 위치에 LoginForm이 없음
다음 render: 이 위치에 LoginForm이 있음
→ React: LoginForm을 새로 추가해야 함
→ React: LoginForm 함수를 호출해 필요한 UI 구조를 계산
→ ReactDOM: 계산된 결과에 맞춰 form 등의 DOM을 추가
→ 브라우저: 변경된 DOM을 화면에 그림
```

이처럼 LoginForm이 React 트리에 처음 추가되고, 폼 DOM도 처음 화면에 반영된 상태를 **mount(마운트)**라고 한다.

React식 코드는 “폼 DOM을 직접 만들어 붙여라”라고 명령하지 않는다. 대신 “`form === 'login'`이면 이 위치에 LoginForm이 있어야 한다”라고 선언한다. 그러면 React와 ReactDOM이 이전 화면과 비교해 필요한 추가·수정을 처리한다. 이것이 React의 **선언적 UI** 방식이다.

### 2-4. App이 LoginForm에 props를 전달한다

```tsx
<LoginForm
  onLogin={login}
  onClose={() => setForm(null)}
/>
```

**props**는 부모 컴포넌트가 자식 컴포넌트에 전달하는 읽기 전용 입력값이다. 문자열·객체뿐 아니라 함수도 props로 전달할 수 있다.

#### DOM 이벤트 prop과 컴포넌트 callback prop은 다르다

`<button>`처럼 HTML 요소를 나타내는 JSX 태그에 쓰는 `onClick`은 React가 처리하는 DOM 이벤트 prop이다.
JSX의 `<button>`은 React 엘리먼트를 만든다. ReactDOM은 그 엘리먼트 설명을 바탕으로 실제 브라우저 DOM `<button>` 요소(`HTMLButtonElement`)를 생성하거나, 이미 존재하는 요소를 갱신한다.
소문자로 쓴 `button`은 HTML DOM 요소로 처리하라는 뜻이다.

```tsx
<button onClick={someFunction}>클릭</button>
```

사용자가 버튼을 클릭하면 브라우저가 click 이벤트를 발생시키고, React는 `onClick`에 전달된 `someFunction`을 실행한다.

반면 `LoginForm`은 브라우저 DOM 요소가 아니라 개발자가 만든 컴포넌트다.
JSX의 `<LoginForm>`은 `LoginForm`을 타입으로 가지는 React 엘리먼트를 만든다. React는 렌더링 과정에서 이 엘리먼트의 props를 `LoginForm`에 전달하고 컴포넌트를 호출한다. `LoginForm`이 반환한 `<form>`, `<button>` 같은 엘리먼트를 바탕으로 ReactDOM이 실제 브라우저 DOM 요소를 생성하거나, 이미 존재하는 요소를 갱신한다.
대문자로 쓴 `LoginForm`은 HTML DOM 요소가 아니라 React 컴포넌트로 처리하라는 뜻이다.

```tsx
<LoginForm
  onLogin={login}
  onClose={() => setForm(null)}
/>
```

여기의 `onLogin`, `onClose`는 `LoginForm` 함수에 전달하는 일반 props다. 이름이 `on...`으로 시작해도 React가 자동으로 이벤트에 연결하거나 함수를 실행하지 않는다. `on...`은 “어떤 일이 일어났을 때 부모에게 알려 줄 함수”라는 관례적인 이름일 뿐이다. `onLogin={login}`은 `login` 함수 자체를 전달하고, `onClose={() => setForm(null)}`은 `setForm(null)`을 나중에 실행할 새 함수를 전달한다. 따라서 이 JSX를 평가하는 시점에는 `login`도 `setForm(null)`도 실행되지 않는다.

LoginForm은 전달받은 함수를 직접 호출하거나 실제 DOM 이벤트 prop에 연결해야 한다.

```tsx
function LoginForm({ onLogin, onClose }: Props) {
  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    await onLogin(email, password);
  }

  return (
    <form onSubmit={handleSubmit}>
      <button type="button" onClick={onClose}>취소</button>
    </form>
  );
}
```

`onLogin`은 부모가 `useAuth`에서 받아 전달한 `login` 함수이고, `onClose`는 부모가 전달한 `() => setForm(null)` 함수다. `LoginForm`은 제출·취소처럼 필요한 순간에 이 함수들을 호출한다.

이처럼 부모가 state 변경 동작을 함수로 전달하고, 자식이 그 함수를 호출해 변경을 요청하는 흐름은 **단방향 데이터 흐름**의 한 형태다. 자식은 부모의 `form` state를 직접 수정하지 않는다.

---

## 3. LoginForm이 처음 mount된다

React가 LoginForm을 호출할 때 App이 전달한 props를 하나의 객체로 넘긴다.

```tsx
type Props = {
  onLogin: (email: string, password: string) => Promise<void>;
  onClose: () => void;
};

export function LoginForm({ onLogin, onClose }: Props) {
  // ...
}
```

`{ onLogin, onClose }`는 props 객체의 두 필드를 꺼내는 구조 분해 문법이다. `Props`는 TypeScript 타입이라 빌드 전 검사에 사용되며 브라우저에서 별도 객체를 만들지는 않는다.

```text
onLogin
→ 문자열 두 개를 받고 Promise<void>를 반환하는 함수

onClose
→ 인자 없이 호출하고 반환값을 사용하지 않는 함수
```

### 3-1. 네 개의 지역 state가 최초 값으로 만들어진다

```tsx
const [email, setEmail] = useState('');
const [password, setPassword] = useState('');
const [error, setError] = useState('');
const [submitting, setSubmitting] = useState(false);
```

| state | 최초 값 | 역할 |
|---|---|---|
| `email` | `''` | 이메일 input에 표시할 값 |
| `password` | `''` | 비밀번호 input에 표시할 값 |
| `error` | `''` | 폼 안에 표시할 오류 메시지 |
| `submitting` | `false` | 로그인 요청 진행 여부 |

이 state들은 LoginForm 인스턴스에 속하는 **지역 state**다. App은 이 값을 알 필요가 없고 LoginForm 안에서만 사용하므로 가장 가까운 컴포넌트가 소유한다.

`useState(초기값)`의 초기값은 최초 render에서 state를 초기화할 때 사용된다. 다음 절에서 React가 이 값을 재렌더링 사이에 어떻게 보존하는지 확인한다.

### 3-2. state는 JSX나 변수 안이 아니라 React가 보존한다

`email` 지역 변수가 다음 렌더링까지 살아 있는 것은 아니다. `LoginForm`이 다시 호출될 때마다 `email`은 새 지역 변수로 만들어진다. React는 렌더 트리에서 `LoginForm`이 놓인 위치의 Hook state와 대기 중인 업데이트를 별도로 보관한다. 이후 `LoginForm`이 다시 렌더링되어 같은 `useState` 호출 위치에 도달하면, React는 최신 state를 `useState`의 첫 번째 반환값으로 제공하고 그 값이 새 지역 변수 `email`에 할당된다.

```text
첫 렌더링
React가 보관한 첫 번째 state: ''
→ LoginForm 안의 email: ''

사용자가 입력해 setEmail('a') 호출
→ React가 첫 번째 state에 대한 업데이트를 예약

다음 렌더링
React가 보관한 첫 번째 state: 'a'
→ 새로 호출된 LoginForm 안의 email: 'a'
```

React는 `email` 같은 변수 이름이 아니라 Hook 호출 순서로 state를 구분한다. 따라서 같은 컴포넌트에서 첫 번째 `useState`는 항상 첫 번째 state, 두 번째 `useState`는 항상 두 번째 state를 가리켜야 한다. 이 때문에 Hook은 조건문이나 반복문 안에서 호출해 순서를 바꾸면 안 된다.

### 3-3. LoginForm의 첫 render와 commit

`LoginForm`이 처음 렌더링될 때 React는 props를 전달해 `LoginForm` 함수를 호출한다. 이때 각 `useState`는 최초 state 값과 setter 함수를 반환한다.

```text
React가 LoginForm(props) 호출
→ email='', password='', error='', submitting=false 상태 값을 얻음
→ LoginForm이 이 값으로 JSX(React 엘리먼트)를 반환
→ ReactDOM이 실제 form·input·button DOM을 생성해 화면에 반영
```

마지막처럼 ReactDOM이 실제 DOM 변경을 화면에 적용하는 과정을 **commit**이라고 한다. 첫 commit이 끝나 `LoginForm`이 트리에 들어오면 mount가 완료된다. 이후 state 업데이트나 부모의 재렌더링 때문에 같은 `LoginForm`이 다시 호출되는 것은 **re-render(재렌더링)**이며 새로운 mount가 아니다.

---

## 4. 사용자가 이메일과 비밀번호를 입력한다

이메일 input은 다음과 같다.

```tsx
<input
  type="email"
  placeholder="이메일"
  value={email}
  onChange={(e) => setEmail(e.target.value)}
  required
/>
```

### 4-1. `value`와 `onChange`가 input을 state에 연결한다

입력칸을 사용할 때 `value`와 `onChange`는 서로 다른 일을 한다. 먼저 `value`는 **입력칸에 지금 보여 줄 글자**를 정하는 속성이다.

```tsx
value={email}
```

즉 React에게 “이 입력칸에는 `email` state에 들어 있는 값을 보여 줘”라고 지시한다. 예를 들어 `email`이 `"user@example.com"`이면 input에도 `user@example.com`이 표시된다.

`onChange`는 **사용자가 입력칸의 내용을 바꿨을 때 실행할 함수**를 정하는 속성이다.

```tsx
onChange={(e) => setEmail(e.target.value)}
```

사용자가 글자를 입력하거나 지우면 이 함수가 실행된다. `e.target.value`에는 그 순간 input에 들어 있는 최신 문자열이 있고, `setEmail(...)`은 그 문자열을 `email` state에 저장한다.

### 4-2. 글자를 입력하거나 지울 때 화면에는 어떻게 반영될까?

처음 `email` state와 input은 모두 빈 문자열(`''`)이라고 가정한다. 사용자가 이메일 칸에 `a`를 입력하면 다음 일이 아주 짧은 시간 안에 일어난다.

```text
1. 브라우저가 input DOM의 현재 값(`input.value`)을 ''에서 'a'로 바꾼다.
2. 값 변경 이벤트가 발생하고 React가 `onChange` 함수를 실행한다.
3. `e.target.value`에서 'a'를 읽어 `setEmail('a')`를 호출한다.
4. React가 email state가 'a'인 LoginForm을 다시 렌더링한다.
5. `value={email}`도 'a'가 되고, React가 DOM에 그 값을 반영한다.
6. 브라우저가 최종 결과인 'a'를 화면에 그린다.
```

1번은 input의 **내부 값**이 먼저 바뀌는 시점이지, 화면에 `a`가 그려지는 시점은 아니다. 사용자는 보통 React의 처리까지 끝난 6번의 최종 결과를 본다. 따라서 `a`가 두 번 보이는 것이 아니라, 한 번 자연스럽게 표시된다.

`setEmail(...)`은 input을 직접 수정하지 않는다. state를 바꾸고, 다음 렌더링에서 `value={email}`을 통해 input에 보여 줄 최종 값을 정한다. 이처럼 React state가 input 값을 관리하는 방식을 **제어되는 입력(controlled input)**이라고 한다.

### 4-3. 이벤트 핸들러는 자신이 만들어진 렌더링의 state를 읽는다

React가 `LoginForm()`을 다시 호출할 때마다 `handleSubmit`도 새로 만들어진다. 함수 본문은 이때 실행되지 않고, 사용자가 form을 제출할 때 실행된다.

```tsx
function LoginForm({ onLogin }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    await onLogin(email, password);
  }

  return (
    <form onSubmit={handleSubmit}>
      <input
        value={email}
        onChange={(e) => setEmail(e.target.value)}
      />
    </form>
  );
}
```

`handleSubmit`은 자신의 함수 안에 없는 `email`, `password`도 사용할 수 있다. 함수가 만들어진 바깥 `LoginForm()` 실행의 지역 변수 환경과 연결되어 있기 때문이다.

**클로저(closure)**는 함수와 그 함수가 만들어질 당시의 바깥 변수 환경이 묶여 있는 것을 뜻한다. JavaScript는 바깥 함수 실행이 끝난 뒤에도 클로저가 그 환경을 참조하는 동안 필요한 변수 환경을 자동으로 유지한다. 그래서 함수가 나중에 호출되어도 바깥 지역 변수에 접근할 수 있다. `closure`라는 이름은 함수가 바깥 변수 환경을 감싸서(close over) 함께 가진다는 뜻에서 왔다.

```text
첫 렌더링(t1): email='' → handleSubmit A 생성
→ A는 t1의 email 변수 환경에 연결됨

setEmail('a')

다음 렌더링(t2): 새 email='a' → handleSubmit B 생성
→ B는 t2의 새 email 변수 환경에 연결됨
```

`setEmail('a')`은 A가 연결된 t1의 `email=''` 변수를 바꾸지 않는다. 대신 React가 관리하는 email state에 `'a'` 업데이트를 등록하고, 그 변경을 반영할 다음 렌더링을 요청한다. 다음 `LoginForm()` 실행에서는 `'a'`를 가진 새 `email` 지역 변수와 handleSubmit B가 만들어진다.

따라서 나중에 A를 호출해도 A는 `''`을 읽고, B를 호출하면 B는 `'a'`를 읽는다. A가 남아 있는 동안에는 A가 참조하는 t1의 변수 환경도 유지되며, A를 더 이상 참조하지 않으면 그 환경은 GC 대상이 된다.

---

## 5. 사용자가 로그인 버튼을 클릭하거나 Enter를 누른다

LoginForm은 버튼의 `onClick`이 아니라 form의 `onSubmit`에서 로그인 요청을 시작한다.

```tsx
<form className="auth-form" onSubmit={handleSubmit}>
  {/* input들 */}
  <button type="submit" disabled={submitting}>
    {submitting ? '로그인 중...' : '로그인'}
  </button>
</form>
```

`type="submit"`은 클릭하거나 input에서 Enter를 눌렀을 때 form의 `submit` 이벤트를 발생시킨다. 따라서 React는 `onSubmit={handleSubmit}`의 `handleSubmit`을 호출한다.

`disabled={submitting}`은 `submitting` 값에 따라 버튼을 누를 수 있는지 정한다. 버튼은 비활성화되어도 화면에서 사라지지 않는다.

```text
submitting === false → disabled={false} → 버튼을 누를 수 있음
submitting === true  → disabled={true}  → 버튼은 보이지만 누를 수 없음
```

### 5-1. HTML form 동작과 React 이벤트 핸들러가 연결된다

`<form>` 내부의 `<button type="submit">`을 클릭하면 버튼의 `click` 이벤트가 발생하고, 그 이벤트가 취소되지 않았다면 기본 동작으로 해당 form의 제출을 시도합니다.

```text
사용자가 로그인 버튼 클릭
→ 브라우저가 button의 click 이벤트를 발생시킴
→ 이 버튼에는 React onClick 핸들러가 없음
→ type="submit" 기본 동작으로 form 제출 시도
→ 브라우저 기본 유효성 검사
→ 검증 성공 시 submit 이벤트 발생
→ React가 onSubmit의 handleSubmit(e) 호출
```

form 제출을 시도하면, `submit` 이벤트를 발생시키기 전에 브라우저가 input의 HTML 유효성 조건을 먼저 검사한다.

```tsx
<input type="email" required />
```

`type="email"`과 `required`는 React가 아니라 브라우저의 HTML 기능이다.

- `type="email"`: 브라우저의 기본 이메일 형식 검사를 사용한다.
- `required`: 빈 값 제출을 막는다.

검증에 실패하면 브라우저가 안내를 표시하고 submit 이벤트를 실행하지 않으므로 `handleSubmit`도 호출되지 않는다. 실제 계정 존재 여부와 비밀번호 일치는 서버가 검사한다.

#### `onSubmit`은 `submit` 이벤트가 발생했을 때 실행할 함수를 React에 알려 주는 속성이다

`on`은 “~이 발생했을 때”라는 뜻이다. 따라서 `onSubmit={handleSubmit}`은 “이 `<form>`에서 `submit` 이벤트가 발생했을 때 `handleSubmit(e)`를 실행해 줘”라고 React에 알려 주는 코드다.

`submit` 이벤트 자체는 React가 아니라 브라우저가 발생시킨다. `<button type="submit">`의 기본 동작으로 form 제출을 시도하고, HTML 유효성 검사를 통과하면 브라우저가 form의 `submit` 이벤트를 발생시킨다. React는 이 이벤트를 받으면 `onSubmit`에 연결된 `handleSubmit(e)`를 호출한다.

`onSubmit`이 없으면 React가 호출할 함수만 없을 뿐, 브라우저의 기본 form 제출은 계속 진행된다. 반대로 `handleSubmit(e)` 안에서 `e.preventDefault()`를 호출하면 브라우저의 기본 제출은 취소되고, React 코드가 로그인 API 요청 등을 직접 처리한다.

### 5-2. `FormEvent`는 이벤트 객체의 TypeScript 타입이다

```tsx
import { useState, type FormEvent } from 'react';

async function handleSubmit(e: FormEvent) {
  // ...
}
```

브라우저가 실제 `submit` 이벤트를 발생시키면 ReactDOM이 이를 감지한다. ReactDOM은 원본 이벤트 정보를 감싼 React 이벤트 객체 `e`를 자동으로 만들고, `onSubmit`에 등록된 `handleSubmit(e)`를 호출한다.

`FormEvent`는 React가 제공하는 TypeScript 타입으로, `e`에 `preventDefault()` 같은 메서드가 있음을 TypeScript에 알려 준다. 타입은 실행 중 이벤트를 만들지 않고, 코드의 타입 검사에만 사용된다.

### 5-3. `preventDefault()`는 페이지 이동을 막는다

```tsx
e.preventDefault();
```

HTML form의 기본 제출은 form 데이터를 전송하며 페이지를 이동하거나 새로고침할 수 있다. 이 프로젝트는 JavaScript의 `fetch`로 로그인하므로 기본 제출을 막고 현재 React 화면을 유지한다.

`preventDefault()`는 브라우저의 기본 동작을 막는 것이지 이벤트 전파를 멈추는 함수는 아니다.

### 5-4. 로그인은 Effect가 아니라 이벤트 핸들러에서 시작한다

여기서 “로그인”은 사용자가 입력한 이메일과 비밀번호로 로그인 API를 호출하는 요청을 뜻한다. 기존 SESSION 쿠키로 현재 사용자를 확인하는 `fetchMe()` 요청과는 구분한다.

`useEffect`와 이벤트 핸들러는 모두 API 요청처럼 컴포넌트 밖의 작업을 실행할 수 있지만, 실행되는 이유가 다르다.

| 작업 | 실행 이유 | 실행 위치 |
| --- | --- | --- |
| 초기 세션 확인 | 앱 화면이 처음 나타났으므로 기존 로그인 상태를 확인해야 함 | `useEffect`에서 `fetchMe()` 호출 |
| 로그인 요청 | 사용자가 로그인 form을 제출함 | `handleSubmit`에서 `onLogin(email, password)` 호출 |

Effect는 렌더링 뒤 또는 의존성 값 변경 뒤에 실행되는 후속 작업이고, 이벤트 핸들러는 사용자의 특정 행동에 응답해 실행되는 함수다.

로그인 API 요청은 `handleSubmit`에서 바로 시작한다. `useEffect`는 사용자의 제출을 직접 알지 못하고 state 변화 뒤에 실행되므로, 로그인 요청을 Effect에 넣으면 제출했다는 사실을 `loginRequested` 같은 별도 state로 전달해야 한다. 그러면 단순한 “제출 → 로그인 요청” 흐름이 복잡해지고, state 변화로 요청이 다시 실행되지 않도록 관리도 필요해진다.


---

## 6. `handleSubmit`은 로그인 요청과 그 결과를 처리한다

5번에서 본 것처럼 사용자가 로그인 버튼을 누르거나 Enter를 누르면 form의 `submit` 이벤트가 발생하고, React가 `handleSubmit`을 호출한다. 이 함수는 브라우저의 기본 form 제출을 막은 뒤 로그인 요청을 시작하고, 성공·실패 결과에 맞는 후속 동작을 처리한다. `submitting`과 `error` state 변경은 이 과정에서 버튼과 오류 메시지를 현재 상황에 맞게 보여 주기 위한 것이다.

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

코드의 흐름은 다음과 같다.

1. `e.preventDefault()`로 브라우저가 form을 기본 방식으로 제출하면서 페이지를 이동하거나 새로고침하는 것을 막는다.
2. `setError('')`로 이전 로그인 실패 메시지를 지운다.
3. `setSubmitting(true)`로 로그인 결과를 기다리는 중임을 state에 기록한다.
4. 부모가 props로 전달한 `onLogin(email, password)`을 호출해 로그인 작업을 시작하고, `await`로 그 성공 또는 실패 결과를 기다린다.
5. 성공하면 `onClose()`를 호출해 로그인 폼을 닫는다.
6. 실패하면 `catch`에서 `error` state에 오류 메시지를 저장한다.
7. 마지막으로 `finally`에서 `setSubmitting(false)`를 호출한다. 이는 성공·실패 모두에서 실행되어 로그인 처리가 끝났음을 state에 기록한다.

이 state 변화가 화면에 보이는 결과는 다음과 같다.

```text
제출 전
→ [ 로그인 ] (클릭 가능)

제출 직후: setSubmitting(true)
→ [ 로그인 중... ] (클릭 불가)

로그인 성공
→ onClose() → LoginForm이 화면에서 사라짐

로그인 실패
→ 오류 메시지 표시 + setSubmitting(false)
→ [ 로그인 ] (다시 클릭 가능)
```

`handleSubmit`이 버튼을 직접 만들거나 DOM을 수정하는 것은 아니다. `setSubmitting`과 `setError`가 state 변경을 요청하면 React가 `LoginForm`을 다시 렌더링한다. 그러면 `return`의 JSX가 현재 state를 읽어 버튼 문구, `disabled` 속성, 오류 메시지를 결정하고 ReactDOM이 달라진 부분을 화면에 반영한다.

### 6-1. `async`, Promise, `await`는 비동기 작업의 완료를 연결한다

`async` 함수는 항상 Promise를 반환한다. 여기서 `onLogin(email, password)`도 로그인 작업의 완료를 나타내는 Promise를 반환한다. `await onLogin(...)`은 그 Promise가 성공하거나 실패할 때까지 `handleSubmit`의 **`await` 아래 코드만** 잠시 멈춘다.

`setSubmitting(true)`는 `await`보다 먼저 실행되어 React에 “`LoginForm`의 `submitting` state를 `true`로 바꾸고, 그 변경을 반영하도록 다시 렌더링해 줘”라고 요청한다.<br>
`setSubmitting(true)`는 버튼 DOM을 직접 수정하는 것이 아니라, 다음 렌더에서 처리할 state 변경을 React에 등록한다.<br>
React는 보통 이벤트 핸들러의 동기 실행이 끝난 뒤 등록된 state 변경을 처리한다.<br>
따라서 `setSubmitting(true)`를 호출한 직후에는 버튼 DOM이 아직 바뀌지 않을 수 있다.<br>
이후 `await onLogin(...)`에서 로그인 Promise가 아직 완료되지 않았다면, `handleSubmit`의 실행은 그 지점에서 잠시 중단된다.<br>
React는 그 사이에 `submitting: true`로 다시 렌더링하고, 변경된 버튼 문구와 `disabled` 속성을 DOM에 반영할 수 있다.<br>
즉 서버 응답을 받아 `handleSubmit` 전체가 끝날 때까지 기다릴 필요는 없다.

submit 이벤트에서 시작된 `handleSubmit`의 실행 순서는 다음과 같다.

```text
사용자가 로그인 버튼 클릭
→ 브라우저가 submit 이벤트 발생
→ React가 handleSubmit 호출
→ setSubmitting(true): LoginForm의 submitting state를 true로 바꾸고 재렌더링하도록 React에 요청
→ await onLogin(...): handleSubmit의 나머지 코드를 미루고 브라우저에 제어권을 돌려줌
→ React가 state 업데이트를 처리
→ [ 로그인 중... ] 문구와 disabled=true를 화면에 반영
→ onLogin Promise가 성공 또는 실패
→ handleSubmit 재개: 성공이면 onClose() 후 finally, 실패면 catch 후 finally 실행
```

JavaScript가 이 작업들을 한순간에 여러 개 병렬 실행한다는 뜻은 아니다. JavaScript는 한 번에 한 작업씩 실행한다. 다만 `await`로 현재 함수의 나머지 실행을 미뤄 두었기 때문에, 그 사이에 브라우저와 React가 다른 작업을 처리할 수 있다.

### 6-2. React는 같은 이벤트의 state 업데이트를 모을 수 있다

```tsx
setError('');
setSubmitting(true);
```

위 두 호출은 `handleSubmit`이 `await`에 도달하기 전, 같은 동기 실행 구간에서 발생한다. React 18은 이처럼 함께 발생한 여러 state 업데이트를 **batching(일괄 처리)**하여 한 묶음으로 처리할 수 있다.

따라서 `setError('')`와 `setSubmitting(true)`를 호출했다고 화면을 반드시 두 번 commit하는 것은 아니다. React는 `error: ''`와 `submitting: true`를 함께 반영한 한 번의 재렌더링으로 다음 UI를 계산하고 DOM에 commit할 수 있다. 즉 오류 메시지만 먼저 사라지고 버튼은 아직 활성화된 중간 화면이 꼭 나타나는 것은 아니다.

### 6-3. `submitting` 하나로 버튼 상태와 문구를 결정한다

```tsx
<button type="submit" disabled={submitting}>
  {submitting ? '로그인 중...' : '로그인'}
</button>
```

| `submitting` | `disabled` | 버튼 문구 |
|---|---|---|
| `false` | `false` | 로그인 |
| `true` | `true` | 로그인 중... |

`조건 ? 참일 때 값 : 거짓일 때 값`은 JavaScript 삼항 연산자다. state 하나에서 관련 UI를 함께 계산하므로 “버튼은 활성인데 문구는 로그인 중” 같은 모순된 별도 state가 필요 없다.

네트워크가 매우 빠르면 사용자가 요청 중 문구를 거의 못 볼 수 있지만, 느린 요청에서는 진행 상태를 알리고 중복 제출을 줄인다.

### 6-4. `error`도 조건부 렌더링에 사용된다

```tsx
{error && <p className="auth-error">{error}</p>}
```

빈 문자열 `''`은 falsy이므로 오류 요소가 결과에 포함되지 않는다. 오류 문자열이 생기면 `<p>`가 결과에 포함되고 ReactDOM이 실제 DOM에 추가한다.

```text
error=''
→ 오류 문단 없음

error='이메일 또는 비밀번호가 올바르지 않습니다.'
→ 오류 문단 렌더링
```

---

## 7. `onLogin` prop에 전달된 `login` 함수가 실제 인증 절차를 실행한다

`onLogin`은 React가 미리 정해 둔 기능이 아니라 **`LoginForm`이 받는 prop의 이름**이다. App은 `useAuth`가 반환한 `login` 함수를 그 값으로 전달한다.

```tsx
function App() {
  const { login } = useAuth();

  return <LoginForm onLogin={login} onClose={() => setForm(null)} />;
}
```

이 JSX는 개념적으로 다음과 같은 React 요소 설명을 만든다.

```tsx
{
  type: LoginForm,
  props: {
    onLogin: login,
    onClose: () => setForm(null),
  },
}
```

이 객체에서 `onLogin`은 prop의 이름(key)이고, `login`은 그 prop의 값인 함수다. 따라서 `onLogin: login` 하나가 prop 하나이며, JSX에서는 이를 `onLogin={login}`으로 쓴다. `login()`처럼 괄호를 붙이지 않았으므로 이 시점에 함수가 실행되지는 않는다. React가 나중에 `props` 객체를 LoginForm에 전달하면, 구조 분해로 받은 `onLogin`은 App의 `login`과 같은 함수를 가리킨다.

```tsx
function LoginForm({ onLogin }: Props) {
  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    await onLogin(email, password);
  }

  return <form onSubmit={handleSubmit}>...</form>;
}
```

`on...`은 “그 일이 일어났을 때 호출할 콜백”이라는 관례적인 이름일 뿐이다. React가 `onLogin`을 자동으로 실행하지는 않는다. 로그인 버튼 클릭 또는 Enter로 `submit` 이벤트가 발생하면 `handleSubmit`이 실행되고, 그 안에서 `onLogin(email, password)`을 호출한다. 이 `onLogin`은 App이 전달한 `useAuth`의 `login` 함수다.

### 7-1. 함수 컴포넌트는 props 객체 하나를 입력으로 받는다

JSX로 렌더링하는 함수 컴포넌트라면 React는 항상 props 객체를 전달합니다. 하지만 props를 사용하지 않는 컴포넌트라면 그 객체를 받을 매개변수는 생략할 수 있습니다.

```tsx
function LoadingSpinner() {
  return <div>로딩 중...</div>;
}

<LoadingSpinner />
```

위 `LoadingSpinner`도 빈 props 객체를 전달받는다. 하지만 함수 안에서 사용할 값이 없으므로 매개변수를 선언하지 않은 것이다. 아래는 props 객체를 매개변수로 받는 형태를 개념적으로 보인 코드다.

```tsx
function LoadingSpinner(props: {}) {
  return <div>로딩 중...</div>;
}
```

실제 코드에서는 `props`를 사용하지 않으므로 첫 번째처럼 매개변수를 생략하는 편이 낫다.

### 7-2. custom Hook이 UI와 인증 절차를 분리한다

`useAuth`의 `login` 코드는 다음과 같다.

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

역할은 세 층으로 나뉜다. `useAuth()`는 “이 컴포넌트에서 인증 상태와 인증 기능을 사용하겠다”라고 읽을 수 있는 custom Hook이다.

| 층 | 이런 느낌 | 관리하는 상태 | 맡는 동작 |
|---|---|---|---|
| `LoginForm` | 사용자와 직접 만나는 로그인 UI 컴포넌트 | input에 표시하고 로그인 요청에도 쓰는 폼 입력값 state `email`, `password`, 버튼 UI를 제어하는 `submitting`, 오류 메시지를 위한 폼 내부 `error` | 로그인 화면을 보여 주고 입력을 받는다. 제출하면 `onLogin(email, password)`으로 부모가 전달한 로그인 처리 함수를 호출하고, 성공하면 `onClose()`를 호출한다. |
| `useAuth` | 인증 상태와 기능을 꺼내 쓰게 해 주는 React용 인증 기능 묶음 | 앱이 기억하는 인증 상태: 현재 사용자 정보 `user` (`user`가 있으면 로그인 상태, `null`이면 로그아웃 상태), 초기 세션 확인 중인지 나타내는 `loading`, 인증 과정에서 발생한 오류 `error` | `login()`, `signup()`, `logout()`, `expireSession()`을 제공한다. `authApi`를 호출한 뒤 결과에 맞게 인증 state를 갱신한다. |
| `authApi` | 서버 HTTP API를 호출하는 통신 도구 | React state를 직접 관리하지 않음 | `/api/auth/login` 같은 서버 API 주소, HTTP method, 요청 body, `credentials: 'include'`를 정하고 실제 HTTP 요청을 보낸다. |

세 층으로 나눈 이유는 화면, 폼 state, 앱의 인증 state, 서버 통신의 책임을 분리하기 위해서다. `LoginForm`은 UI와 폼의 지역 state를, `useAuth`는 인증 흐름과 앱의 인증 state를, React에 의존하지 않는 TypeScript 모듈인 `authApi`는 서버 통신을 맡는다. 따라서 UI나 API가 바뀌어도 관련된 층만 수정하면 된다.

### 7-3. `useCallback`은 의존성이 같을 때 함수 참조를 재사용한다

```tsx
const login = useCallback(async (...) => {
  // 로그인 동작
}, []);
```

`useCallback`은 전달한 callback을 즉시 실행하지 않는다. `useMemo`와 비교하면 무엇을 재사용하는지 구분하기 쉽다.

```text
useCallback(callback, deps)
→ callback을 지금 호출하지 않는다.
→ deps가 이전 렌더링과 같으면,
   이전 렌더링에서 반환했던 같은 함수 참조를 다시 반환한다.
→ deps가 바뀌면,
   이번 렌더링의 새 함수 참조를 반환한다.

useMemo(calculateValue, deps)
→ calculateValue를 렌더링 중 호출해 값을 계산한다.
→ deps가 이전 렌더링과 같으면,
   이전 렌더링에서 계산해 둔 같은 결과값을 다시 반환한다.
→ deps가 바뀌면,
   새로 계산한 결과값을 반환한다.
```

여기서는 의존성 배열이 `[]`이므로 App이 처음 mount된 뒤 unmount되지 않는 동안 `login`은 같은 함수 참조를 반환한다. 그렇다고 로그인 결과나 서버 응답을 저장하는 것은 아니다. 사용자가 `login(email, password)`을 호출할 때마다 함수 본문과 서버 요청은 새로 실행된다.

> **주의:** `[]`은 콜백 안에서 렌더링마다 바뀔 수 있는 props나 state를 읽지 않을 때만 쓸 수 있다. 현재 `login`은 React state setter와 import한 `authApi`만 쓰므로 `[]`이지만, props나 state 값을 읽게 되면 의존성 배열에 넣어야 오래된 값을 사용하지 않는다.

`useCallback`은 `LoginForm`에 전달하는 `login` 함수의 참조를 유지한다. 현재는 없어도 로그인 기능은 같지만, `React.memo`로 감싼 자식이나 다른 Hook의 의존성에 함수를 전달할 때 유용하다.

### 7-4. `authApi`는 React에 의존하지 않는 API 모듈이고, `fetch`는 브라우저 기능이다

첫 요청은 로그인 자격 증명을 서버로 보낸다.

```tsx
export async function login(email: string, password: string): Promise<void> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) throw await toHttpError(res, '로그인에 실패했습니다.');
}
```

`fetch`는 React Hook이 아니라 브라우저가 제공하는 비동기 HTTP API다. React는 요청을 직접 보내지 않는다.

로그인 성공 응답의 `Set-Cookie`를 받으면 브라우저가 SESSION 쿠키를 저장한다. `credentials: 'include'`는 이후 요청에도 조건에 맞는 쿠키를 포함하도록 한다. JavaScript가 HttpOnly SESSION 쿠키 값을 직접 읽어 사용자 state로 저장하는 구조가 아니다.

로그인 응답에는 현재 사용자 전체 정보가 없으므로, `useAuth`의 `login` 함수가 다음 코드로 두 번째 요청을 시작해 사용자를 조회한다.

```tsx
const authenticatedUser = await authApi.fetchMe();
```

```tsx
export async function fetchMe(): Promise<User | null> {
  const res = await fetch('/api/auth/me', { credentials: 'include' });
  if (res.status === 401) return null;
  if (!res.ok) throw await toHttpError(res, '로그인 상태를 확인하지 못했습니다.');
  return res.json();
}
```

로그인 성공 시 요청·응답과 React state 갱신 흐름은 다음과 같다.

```text
POST /api/auth/login
→ 서버가 자격 증명 검사
→ 성공 응답의 Set-Cookie
→ 브라우저가 SESSION 쿠키 저장

GET /api/auth/me
→ 브라우저가 SESSION 쿠키 포함
→ 서버가 현재 User JSON 응답
→ useAuth의 login이 setUser(authenticatedUser) 호출
```

### 7-5. state를 어디에 둘지는 사용하는 범위로 결정한다

이 표는 로그인 흐름과 관련된 state만 정리한 것이다.

| state | 실제 소유 위치 | 사용하는 범위 |
|---|---|---|
| `form` | `App` | 로그인·회원가입 폼을 열지와 어떤 폼을 열지 결정 |
| `email`, `password` | `LoginForm` | LoginForm 입력 UI |
| `error`, `submitting` | `LoginForm` | LoginForm 제출 UI |
| `user` | `App`이 호출한 `useAuth` 내부 Hook state | 로그인 여부에 따라 공개 화면·거래 화면을 결정 |
| `loading` | `App`이 호출한 `useAuth` 내부 Hook state | 초기 세션 확인 중 상단 인증 영역을 제어 |
| `authError` (`useAuth`의 `error`) | `App`이 호출한 `useAuth` 내부 Hook state | 인증 오류 메시지 표시 |

여러 화면이 알아야 하는 `user`를 LoginForm 지역 state로 두면 App은 로그인 결과를 알 수 없다. 반대로 이메일 입력값을 App까지 올리면 필요 이상으로 범위가 커진다. state는 그 값을 함께 사용해야 하는 컴포넌트들의 가장 가까운 공통 부모가 소유하는 것이 기본 원칙이다.

---

## 8. 로그인 결과가 성공과 실패로 갈린다

### 8-1. 성공하면 App state가 바뀌고 LoginForm은 unmount된다

`useAuth.login`은 사용자 조회까지 성공하면 다음 코드를 실행한다.

```tsx
setError(null);
setUser(authenticatedUser);
```

`user`는 App이 호출한 Hook state이므로 `setUser`는 App의 재렌더링을 요청한다. Promise가 성공으로 끝나면 LoginForm의 `handleSubmit`도 이어서 실행된다.

```tsx
await onLogin(email, password);
onClose();
```

`onClose`는 App이 전달한 함수다.

```tsx
onClose={() => setForm(null)}
```

성공 시 최종 state는 `user=authenticatedUser`, `form=null`이 된다. React는 관련 업데이트를 모아 최종 state에 맞는 UI를 계산할 수 있으므로, 각 setter 뒤에 반드시 별도의 중간 화면이 보인다고 가정하면 안 된다.

```text
setUser(authenticatedUser)
→ App이 로그인 사용자 화면을 계산할 상태가 됨

setForm(null)
→ <LoginForm /> 조건이 거짓이 됨

다음 App 결과
→ 사용자 이름·로그아웃 버튼
→ AuthenticatedTradingLayout
→ LoginForm 없음
```

React 트리에서 LoginForm이 제거되는 것이 **unmount(언마운트)**다.

unmount되면:

- LoginForm이 만든 form·input·button DOM이 제거된다.
- `email`, `password`, `error`, `submitting` 지역 state가 폐기된다.
- LoginForm에 Effect cleanup이 있었다면 cleanup이 실행된다.

현재 LoginForm에는 Effect가 없으므로 실행할 cleanup도 없다.

### 8-2. 실패하면 같은 LoginForm이 state를 유지한 채 재렌더링된다

`onLogin`은 `useAuth.login` 함수다. 인증 요청이 실패하거나 `fetchMe()`가 `null`을 반환하면 이 Promise가 실패하고, `await onLogin(...)`에서 바로 `catch`로 이동한다. 따라서 성공 뒤에 있는 `onClose()`는 실행되지 않는다.

```tsx
try {
  await onLogin(email, password);
  onClose(); // 실패하면 이 줄은 실행되지 않음
} catch (err) {
  setError(err instanceof Error ? err.message : '로그인에 실패했습니다.');
} finally {
  setSubmitting(false);
}
```

`err instanceof Error`가 true면 그 Error의 `message`를, 아니면 기본 문구를 `error` state에 저장한다.

여기서 “실패하면 재렌더링된다”는 말은 `onLogin`의 실패 자체가 화면을 다시 그린다는 뜻이 아니다. 실패를 처리하면서 호출한 `setError(...)`와 `setSubmitting(false)`가 state를 변경하고, React가 그 변경을 반영하기 위해 `LoginForm`을 다시 렌더링한다는 뜻이다.

`onClose()`가 실행되지 않으므로 App의 `form`은 계속 `'login'`이고 LoginForm은 React 트리에서 제거되지 않는다. `setError`와 `setSubmitting(false)`로 LoginForm은 재렌더링되지만, React는 같은 트리 위치의 같은 LoginForm을 update로 판단하므로 state를 초기화하지 않는다. 따라서 setter를 호출하지 않은 `email`, `password`는 이전 값을 유지하고, `error`, `submitting`만 새 값으로 갱신된다.

```text
state 변경
→ LoginForm 재렌더링
→ 이전 email/password state는 유지
→ 변경한 error/submitting만 새 값 사용
→ 오류 문구 표시, 로그인 버튼 다시 활성화
```

### 8-3. 성공과 실패 비교

| 결과 | App의 `user` | App의 `form` | LoginForm | 지역 state |
|---|---|---|---|---|
| 성공 | 사용자 객체 | `null` | unmount | 폐기 |
| 실패 | 변경 없음 (`null` 유지) | `'login'` | mount 유지 | 입력 유지, 오류 갱신 |

---

## 9. 사용자가 취소 버튼을 누른다

성공·실패 경로와 별개로, 제출 전이나 요청 중 사용자가 폼을 닫는 경로도 살펴본다.

```tsx
<button type="button" className="ghost" onClick={onClose}>
  취소
</button>
```

`type="button"`은 form 안에서도 submit을 시작하지 않는 일반 버튼이다. `<button>`의 기본 type은 form 문맥에서 submit이 될 수 있으므로 취소 버튼에는 명시하는 것이 안전하다.

버튼의 type은 보통 다음처럼 구분한다.

- `type="submit"`: form을 제출한다.
- `type="button"`: form을 제출하지 않는 일반 버튼이다.

```text
사용자가 취소 클릭
→ onClick의 onClose 호출
→ App의 setForm(null)
→ App 재렌더링
→ 조건에서 LoginForm 제거
→ LoginForm unmount
```

unmount되면서 지역 state가 폐기되므로 다시 로그인 버튼을 누르면 새 LoginForm이 최초 값으로 mount된다.

```text
취소
→ 기존 LoginForm unmount
→ email/password state 폐기

다시 로그인 버튼 클릭
→ 새 LoginForm mount
→ 네 useState가 각각 '', '', '', false의 최초 값을 사용
```

CSS로 폼을 숨기는 것과 컴포넌트를 트리에서 제거하는 것은 다르다. CSS로만 숨기면 컴포넌트가 계속 mount된 상태라 state가 남을 수 있지만, 현재 코드는 조건부 렌더링으로 제거하므로 state도 사라진다.

> 더 깊이 보기: 현재 취소 버튼은 `submitting=true`일 때도 활성화되어 있다. 요청 중 취소하면 LoginForm은 unmount되지만 이미 시작한 `fetch`가 자동으로 취소되는 것은 아니다. 요청 취소까지 원한다면 `AbortController` 같은 별도 처리가 필요하다. 현재 동작에서 “취소”는 네트워크 요청 취소가 아니라 폼 닫기다. 취소 뒤 요청이 성공하면 `useAuth`는 App에 연결되어 있으므로 `setUser`가 실행되어 로그인 상태가 될 수 있다. 요청이 실패해 이미 unmount된 LoginForm의 `setError`나 `setSubmitting`이 호출되더라도 제거된 폼의 UI를 되살리거나 나중에 새로 연 LoginForm의 state를 바꾸지는 않는다.

---

## 10. 로그인 흐름으로 배운 React 기초를 정리한다

### 10-1. 이 코드에 실제로 등장한 Hook

| Hook | 처음 등장한 이유 | 이 로그인 흐름에서 하는 일 |
|---|---|---|
| `useState` | 렌더링 사이에 값을 기억해야 함 | `form`, `user`, 입력값, 오류, 요청 상태 관리 |
| `useEffect` | mount 뒤 서버와 동기화해야 함 | 기존 SESSION으로 현재 사용자 확인 |
| `useCallback` | 렌더링 사이에 함수 참조를 유지 | `login`, `logout` 등 인증 함수 참조 유지 |
| `useAuth` | 인증 state와 동작을 묶어야 함 | React Hook들을 조합한 프로젝트의 커스텀 Hook |

Hook은 “특별한 문법”이 아니라 React 패키지 또는 프로젝트가 제공하는 JavaScript 함수다. 다만 React가 호출 순서로 Hook state를 연결하므로 컴포넌트와 커스텀 Hook의 최상위에서만 호출한다.

### 10-2. 컴포넌트 생명 흐름

```text
최초 render
→ 컴포넌트 함수 호출
→ Hook state를 최초 값으로 초기화
→ JSX 계산

첫 commit과 mount
→ 계산 결과를 React 트리와 실제 DOM에 처음 반영
→ 컴포넌트가 mounted 상태가 됨

re-render
→ state 업데이트 또는 부모의 재렌더링 등으로 같은 컴포넌트 함수가 다시 호출됨
→ 기존 state 유지

unmount
→ 컴포넌트가 트리에서 제거됨
→ 지역 state 폐기
→ Effect cleanup이 있으면 실행
```

### 10-3. 전체 state 타임라인

| 단계 | `user` / `loading` | `form` | `email/password` | `error` | `submitting` | LoginForm |
|---|---|---|---|---|---|---|
| 앱 첫 렌더링 | `null` / `true` | `null` | 없음 | 없음 | 없음 | 아직 없음 |
| 세션 없음 확인 | `null` / `false` | `null` | 없음 | 없음 | 없음 | 아직 없음 |
| 로그인 버튼 클릭 | `null` / `false` | `'login'` | `''` / `''` | `''` | `false` | mount |
| 사용자 입력 | `null` / `false` | `'login'` | 입력한 값 | `''` | `false` | re-render |
| 제출 시작 | `null` / `false` | `'login'` | 입력한 값 | `''` | `true` | re-render |
| 실패 | `null` / `false` | `'login'` | 유지 | 오류 문자열 | `false` | 유지·재시도 |
| 성공 | `User` / `false` | `null` | 폐기 | 폐기 | 폐기 | unmount |
| 취소 | `null` / `false` | `null` | 폐기 | 폐기 | 폐기 | unmount |

### 10-4. 데이터와 이벤트의 방향

```text
데이터
App state
→ props
→ LoginForm
→ JSX
→ DOM

사용자 행동
DOM 이벤트
→ React 이벤트 핸들러
→ state setter
→ 재렌더링
→ 새 JSX
→ DOM 갱신
```

props와 state는 화면을 아래 방향으로 만들고, 사용자의 이벤트는 callback과 setter를 통해 state 변경을 요청한다. 이 순환이 React UI의 기본 동작이다.

### 10-5. 스스로 확인할 질문

1. `<App />`과 `App()`은 어떤 차이가 있는가?
2. `root.render(<App />)`는 무엇을 시작하는가?
3. `form` state가 `'login'`이 되면 왜 LoginForm이 mount되는가?
4. re-render 때 `useState('')`가 다시 실행되어도 입력값이 초기화되지 않는 이유는 무엇인가?
5. Hook을 조건문 안에서 호출하면 안 되는 이유는 무엇인가?
6. `value`와 `onChange`를 함께 쓰는 input을 왜 제어되는 입력이라고 하는가?
7. 로그인 요청은 왜 Effect가 아니라 submit 이벤트 핸들러에서 시작하는가?
8. `useCallback`은 로그인 함수를 실행하는가, 함수 참조를 기억하는가?
9. 실패 후 입력값은 유지되고 성공 후에는 폐기되는 이유는 무엇인가?
10. 취소 버튼이 폼을 닫아도 이미 시작한 `fetch`가 계속될 수 있는 이유는 무엇인가?

## 마지막 핵심

LoginForm 흐름을 한 문장으로 줄이면 다음과 같다.

> 현재 props와 state로 컴포넌트가 JSX를 반환하고, 사용자 이벤트가 setter를 호출하면 React가 컴포넌트를 다시 렌더링하며, ReactDOM이 필요한 DOM 변경만 commit한다.

폼을 여는 일도, 입력값을 표시하는 일도, 요청 중 버튼을 잠그는 일도, 성공 후 폼을 제거하는 일도 이 원리에서 나온다. React 기초의 중심은 DOM을 직접 명령하는 것이 아니라 **현재 state라면 어떤 UI여야 하는지를 컴포넌트가 선언하는 것**이다.
