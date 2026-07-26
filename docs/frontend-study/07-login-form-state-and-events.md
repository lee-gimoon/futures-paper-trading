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

이 부모·자식 설명 구조를 **React 요소 트리**라고 한다. React는 이전 요소 트리와 새 요소 트리를 비교해 버튼을 새로 만들지, 문구만 바꿀지, 폼을 제거할지를 결정한다. ReactDOM은 그 결정대로 실제 브라우저 DOM을 수정한다. 이 요소 트리가 실제 DOM으로 이어지는 순서는 바로 다음 render·commit 설명에서 확인한다.

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
→ props와 state로 다음 React 요소 트리를 계산한다
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

`root.render(<App />)`가 최초 렌더링을 요청하면 React가 App을 호출하고, App은 render 중 Hook을 호출한다. 이 첫 결과가 commit되어 App이 트리에 들어오면 App의 **mount(마운트)**가 완료된다.

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

여기서 처음 **Hook(훅)**이 나온다. Hook은 함수형 컴포넌트가 React의 state, Effect 같은 기능을 사용하도록 하는 `use...` 함수다.

- `useState`: 컴포넌트가 렌더링 사이에 기억할 state를 만든다.
- `useEffect`: DOM commit 뒤 외부 시스템과 동기화할 작업을 등록한다.
- `useAuth`: 이 프로젝트가 만든 커스텀 Hook으로, 내부에서 React Hook들을 조합한다.

Hook은 다음 규칙을 지켜야 한다.

1. React 컴포넌트 또는 다른 커스텀 Hook 안에서 호출한다.
2. 함수의 최상위에서 호출한다.
3. 조건문·반복문·중첩 함수 안에서 호출하지 않는다.

React는 Hook 호출 순서로 각 state와 Effect를 구분한다. 어떤 렌더링에서는 첫 번째 Hook을 호출하고 다른 렌더링에서는 건너뛰면 React가 state의 자리를 올바르게 연결할 수 없다.

### 1-6. `useAuth`는 컴포넌트가 아니라 커스텀 Hook이다

`useAuth()`는 JSX를 반환하는 컴포넌트가 아니다. 커스텀 Hook은 Hook 규칙을 지키면서 다른 Hook을 조합하는 JavaScript 함수이며, React와 린터가 Hook으로 식별할 수 있도록 이름을 `use`로 시작한다.

```tsx
export function useAuth() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Effect와 인증 함수들

  return { user, loading, error, login, signup, logout, expireSession };
}
```

커스텀 Hook이 별도의 컴포넌트 인스턴스나 전역 singleton state를 만드는 것은 아니다. App이 `useAuth()`를 호출했으므로 이 Hook의 `user`, `loading`, `error` state는 App의 Hook state 일부로 React가 관리한다. 다른 컴포넌트가 `useAuth()`를 별도로 호출하면 같은 state를 공유하는 것이 아니라 그 컴포넌트에 연결된 새 state가 생긴다. `useAuth`는 관련 state와 동작을 다루는 코드를 별도 파일로 정리해 준다.

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

state는 컴포넌트 함수의 일반 지역 변수와 다르다. 일반 지역 변수는 함수가 끝나면 다음 호출에 자동으로 이어지지 않지만, React state는 해당 컴포넌트가 같은 위치에 유지되는 동안 React가 보존한다.

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

이것이 **조건부 렌더링**이다. 별도의 HTML 페이지를 선택하는 것이 아니라, 현재 state로 이번 렌더링에서 어떤 React 요소를 반환할지 결정한다.

```text
loading=true
→ 인증 버튼 영역에 null 반환
→ React는 그 위치에 아무 DOM도 만들지 않음

loading=false, user 있음
→ 사용자 이름과 로그아웃 버튼

loading=false, user=null
→ 로그인·회원가입 버튼
```

`loading=true`가 숨기는 것은 상단 인증 버튼 영역이다. 이때도 `user=null` 조건을 사용하는 공개 차트 영역은 렌더링될 수 있다.

`<>...</>`는 **Fragment**다. 형제 요소 여러 개를 묶되 불필요한 `<div>` DOM은 만들지 않는다.

첫 렌더링에서 `form=null`이므로 아래 조건도 거짓이다. 따라서 `LoginForm`은 아직 호출되지도, mount되지도 않는다.

```tsx
{form === 'login' && <LoginForm onLogin={login} onClose={() => setForm(null)} />}
```

첫 렌더링의 인증 관련 결과를 정리하면 다음과 같다.

```text
user=null
→ 공개 차트 영역 렌더링

loading=true
→ 로그인·회원가입 버튼은 아직 숨김

form=null
→ LoginForm은 아직 없음
```

### 1-9. 첫 commit 뒤 Effect가 기존 세션을 확인한다

페이지를 새로 열었을 때는 사용자가 버튼을 누르지 않아도 기존 SESSION 쿠키가 유효한지 확인해야 한다. `useAuth`는 이 동기화를 `useEffect`에 둔다.

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

**Effect**는 React가 DOM을 commit한 뒤, React 바깥의 시스템과 동기화하려고 실행하는 코드다. 대표적인 대상은 서버 요청, 이벤트 구독, 타이머, 브라우저 API다.

`useEffect(setup 함수, 의존성 배열)`의 두 번째 인자는 Effect가 사용하는 반응형 값의 목록이다. 여기서는 빈 배열 `[]`로 이 Effect에 반응형 의존성이 없음을 선언했으므로, 같은 mount에서 props나 state 변화 때문에 다시 실행되지는 않는다. 첫 commit 뒤 setup 함수가 실행된다.

개발 StrictMode에서는 첫 mount 때 Effect의 `setup → cleanup → setup` 검사가 추가로 수행될 수 있다. 현재 Effect는 cleanup이나 요청 취소가 없으므로 개발 환경에서 `GET /api/auth/me`가 두 번 전송될 수 있다. 운영 빌드에서는 이 개발용 추가 검사가 없다.

```text
App 첫 render
→ user=null, loading=true, form=null로 첫 UI 결정
→ ReactDOM 첫 commit
→ useAuth의 Effect 실행
→ GET /api/auth/me
→ 응답에 따라 setUser(...)
→ setLoading(false)
→ App 재렌더링 요청
```

이 요청은 렌더링 중이 아니라 Effect에서 실행된다. 컴포넌트 render는 UI 계산에 집중하고, 페이지가 나타난 뒤 서버 상태와 맞추는 작업은 Effect가 맡는다.

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

이후에는 앞에서 정한 대로 기존 세션이 없는 경로를 따라간다. App의 재렌더링 결과 로그인 버튼이 나타난 상태에서 다음 단계가 시작된다.

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

```tsx
onClick={() => setForm('login')} // 함수를 전달: 클릭할 때 실행
onClick={setForm('login')}       // 렌더링 중 즉시 호출: 잘못된 형태
```

React는 이벤트가 생길 때 호출할 함수를 prop으로 받는다. 함수 호출 결과를 넘기는 것이 아니라 함수 자체를 넘긴다는 점이 중요하다.

### 2-2. setter는 state를 즉시 대입하지 않고 다음 렌더링을 요청한다

```tsx
setForm('login');
```

이 코드는 `form = 'login'` 같은 변수 대입이 아니다. React에 다음 state가 `'login'`이라고 알리고 App의 재렌더링을 예약한다.

state는 각 렌더링에서 고정된 **스냅샷(snapshot)**처럼 동작한다.

```text
현재 렌더링의 form
→ null

setForm('login') 호출
→ 현재 함수 안의 form을 즉시 바꾸지 않음
→ 다음 렌더링을 요청

다음 App 호출에서 받은 form
→ 'login'
```

setter 호출 직후 같은 이벤트 핸들러 안에서 `form`을 읽으면 아직 현재 렌더링의 값인 `null`이다. 새 값은 다음 렌더링에서 받는다.

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
→ 오른쪽 <LoginForm />을 결과에 포함하지 않음

form === 'login'이 true
→ 오른쪽 <LoginForm />을 결과에 포함
```

React는 이전 요소 트리와 다음 요소 트리를 비교한다. 이 비교를 **reconciliation(재조정)**이라고 한다. 이전에는 없던 `LoginForm`이 같은 위치에 새로 나타났으므로 React는 LoginForm을 호출하고 그 결과를 트리에 추가하며, ReactDOM은 필요한 DOM을 commit한다. 이 첫 추가와 commit이 완료되어 LoginForm이 트리에 들어온 상태를 mount라고 한다.

React식 코드는 “폼 DOM을 직접 만들어 붙여라”라고 명령하지 않는다. `form` state일 때 어떤 JSX가 있어야 하는지 선언하면 React와 ReactDOM이 필요한 추가·수정을 처리한다. 이것이 React의 **선언적 UI** 방식이다.

### 2-4. App이 LoginForm에 props를 전달한다

```tsx
<LoginForm
  onLogin={login}
  onClose={() => setForm(null)}
/>
```

**props**는 부모 컴포넌트가 자식 컴포넌트에 전달하는 읽기 전용 입력값이다. 문자열·객체뿐 아니라 함수도 props로 전달할 수 있다.

- `onLogin`: `useAuth`가 만든 실제 로그인 함수
- `onClose`: App의 `form`을 `null`로 바꾸는 함수

부모인 App은 앱 전체 state와 동작을 알고 있고, 자식인 LoginForm은 전달받은 함수를 필요한 순간 호출한다. LoginForm이 부모의 state를 직접 수정하지 않는 이 흐름을 **단방향 데이터 흐름**이라고 볼 수 있다.

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

`email`이라는 지역 변수 자체가 다음 렌더링까지 살아 있는 것이 아니다. React가 LoginForm의 첫 번째 Hook state를 보존하고, 다음 LoginForm 호출 때 그 시점의 값을 `email`로 돌려준다.

```text
첫 LoginForm 호출
→ 첫 번째 useState: ''

setEmail('a')
→ React가 첫 번째 Hook state의 다음 값을 'a'로 기록

다음 LoginForm 호출
→ 첫 번째 useState가 'a'를 반환
```

React는 state를 컴포넌트 타입과 트리 위치에 연결하고, 그 컴포넌트 안에서는 “첫 번째 Hook은 email, 두 번째 Hook은 password”처럼 Hook 호출 순서로 구분한다. 이 때문에 Hook 호출 순서를 바꾸면 안 된다.

### 3-3. LoginForm의 첫 render와 commit

LoginForm은 네 state의 최초 값으로 JSX를 반환한다.

```text
React가 LoginForm(props) 호출
→ useState가 최초 state 네 개 반환
→ LoginForm이 form·input·button React 요소 반환
→ ReactDOM이 실제 form·input·button DOM을 추가
```

첫 commit이 끝나 LoginForm이 트리에 들어오면 mount가 완료된다. 이후 state 업데이트나 부모의 재렌더링 때문에 같은 LoginForm이 다시 호출되는 것은 **re-render(재렌더링)**이며 새로운 mount가 아니다.

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

`value={email}`은 input에 표시할 값을 React state에서 가져온다. `onChange`는 사용자의 입력을 다시 state로 옮긴다.

```text
표시할 값
state email → value={email} → 실제 input

새 사용자 입력
실제 input → onChange → setEmail(...) → state email
```

이처럼 React state를 값의 원천으로 사용하는 input을 **제어되는 입력(controlled input)**이라고 한다.

`value={email}`만 두고 `onChange`로 state를 바꾸지 않으면 React가 계속 같은 값을 내려 주므로 사용자가 입력을 수정할 수 없는 것처럼 보인다. 반대로 `onChange`만 있고 `value`가 없으면 DOM이 자체 값을 관리하는 비제어 입력이 된다.

`type="password"`는 브라우저가 글자를 가려 표시하게 할 뿐, React state에 저장된 문자열을 암호화하지는 않는다.

### 4-2. 한 글자를 입력하면 state 변경과 재렌더링이 이어진다

사용자가 이메일 칸에 `a`를 입력하면 다음 순서로 흐른다.

```text
브라우저가 input 입력 이벤트 감지
→ React가 onChange에 등록된 함수에 이벤트 객체 e 전달
→ e.target.value는 'a'
→ setEmail('a')
→ LoginForm 재렌더링 예약
→ 다음 LoginForm 호출에서 email은 'a'
→ JSX의 value={email}도 'a'
→ ReactDOM이 실제 input 값과 state를 맞춤
```

이 업데이트는 LoginForm 자신의 state에서 시작되므로 App까지 다시 호출할 필요 없이 LoginForm과 필요한 하위 UI가 렌더링 대상이 된다.

여기서:

- `e`: React가 이벤트 핸들러에 전달한 이벤트 객체
- `e.target`: 이벤트가 시작된 실제 DOM input
- `e.target.value`: 그 input의 현재 문자열 값

브라우저가 실제 입력 사건을 만들고, ReactDOM의 이벤트 시스템이 JSX의 `onChange` 핸들러를 찾아 호출한다. React가 input 값을 반복해서 감시하는 방식은 아니다.

### 4-3. 이벤트 핸들러는 해당 렌더링의 state를 기억한다

LoginForm 함수 안에 선언된 `handleSubmit`과 인라인 `onChange` 함수는 렌더링마다 만들어진다. 각 함수는 그 렌더링의 `email`, `password` 같은 값을 기억하는 **클로저(closure)**다.

```text
email=''인 렌더링
→ email=''을 보는 핸들러 생성

setEmail('a') 뒤 새 렌더링
→ email='a'를 보는 새 핸들러 생성
```

사용자가 제출할 때 DOM에는 최신 렌더링에서 연결된 `handleSubmit`이 있으므로, 그 함수는 최신 렌더링의 이메일과 비밀번호를 사용한다.

setter는 현재 클로저의 값을 바꾸지 않는다. `setEmail(...)`은 다음 렌더링을 요청하며, 현재 이벤트 핸들러 안의 `email`은 끝까지 현재 렌더링의 스냅샷이다.

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

### 5-1. HTML form 동작과 React 이벤트 핸들러가 연결된다

버튼을 클릭하면 브라우저의 HTML 동작이 먼저 submit 절차를 만든다.

```text
사용자가 로그인 버튼 클릭
→ 브라우저 click 이벤트 발생
→ type="submit" 기본 동작으로 form 제출 시도
→ 브라우저 기본 유효성 검사
→ 검증 성공 시 submit 이벤트 발생
→ React가 onSubmit의 handleSubmit(e) 호출
```

input에서 Enter를 눌러도 같은 submit 경로로 모인다. 그래서 로그인 동작을 버튼 `onClick`에 중복하지 않고 form의 `onSubmit` 한 곳에 둔다.

```tsx
<input type="email" required />
```

`type="email"`과 `required`는 React가 아니라 브라우저의 HTML 기능이다.

- `required`: 빈 값 제출을 막는다.
- `type="email"`: 브라우저의 기본 이메일 형식 검사를 사용한다.

검증에 실패하면 브라우저가 안내를 표시하고 submit 이벤트를 실행하지 않으므로 `handleSubmit`도 호출되지 않는다. 실제 계정 존재 여부와 비밀번호 일치는 서버가 검사한다.

### 5-2. `FormEvent`는 실행 기능이 아니라 TypeScript 타입이다

```tsx
import { useState, type FormEvent } from 'react';

async function handleSubmit(e: FormEvent) {
  // ...
}
```

`FormEvent`는 `e`가 폼 이벤트이며 `preventDefault()` 같은 메서드를 가진다고 TypeScript에 알려 준다. 실행 중 submit 이벤트를 만드는 것은 브라우저이고, 등록된 함수를 연결하는 것은 ReactDOM의 이벤트 시스템이다.

### 5-3. `preventDefault()`는 페이지 이동을 막는다

```tsx
e.preventDefault();
```

HTML form의 기본 제출은 form 데이터를 전송하며 페이지를 이동하거나 새로고침할 수 있다. 이 프로젝트는 JavaScript의 `fetch`로 로그인하므로 기본 제출을 막고 현재 React 화면을 유지한다.

`preventDefault()`는 브라우저의 기본 동작을 막는 것이지 이벤트 전파를 멈추는 함수는 아니다.

### 5-4. 로그인은 Effect가 아니라 이벤트 핸들러에서 시작한다

Effect와 이벤트 핸들러는 둘 다 컴포넌트 밖의 일을 실행할 수 있지만 시작 이유가 다르다.

```text
초기 세션 확인
→ 화면이 mount된 뒤 서버 상태와 동기화해야 함
→ useEffect에서 실행

로그인 요청
→ 사용자가 form을 제출했다는 특정 행동 때문에 실행
→ handleSubmit 이벤트 핸들러에서 실행
```

사용자 행동 때문에 생긴 작업을 Effect로 옮기면 “왜 실행되었는가”가 state 변화에 숨고 중복 실행 위험도 커진다. 이 코드처럼 제출 동작은 제출 핸들러에 두는 편이 맞다.

---

## 6. `handleSubmit`이 요청 중 UI를 만든다

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

### 6-1. `async`, Promise, `await`는 비동기 작업의 완료를 연결한다

`async` 함수는 항상 Promise를 반환한다. `await onLogin(...)`은 onLogin의 Promise 결과가 정해질 때까지 `handleSubmit`의 나머지 부분만 잠시 멈춘다.

```text
handleSubmit 시작
→ setSubmitting(true)
→ onLogin 호출
→ await에서 handleSubmit 일시 중단
→ 브라우저는 이벤트·렌더링·네트워크 응답 처리를 계속함
→ Promise 성공이면 try의 다음 줄
→ Promise 실패면 catch
→ 마지막에 finally
```

`await`가 브라우저의 JavaScript 스레드 전체를 막는 것은 아니다. 그래서 네트워크를 기다리는 동안 React가 `submitting=true` 화면을 commit할 수 있다.

React가 async 이벤트 핸들러의 Promise를 대신 관리해 주는 것도 아니다. 성공·실패·마무리 UI는 이 코드의 `try`, `catch`, `finally`와 state setter가 직접 관리한다.

4장에서 본 state 스냅샷 때문에 `onLogin(email, password)`에는 제출 시점의 문자열이 전달된다. 요청을 시작한 뒤 사용자가 input을 바꾸더라도 이미 함수 인자로 넘긴 문자열은 바뀌지 않는다.

### 6-2. React는 같은 이벤트의 state 업데이트를 모을 수 있다

```tsx
setError('');
setSubmitting(true);
```

React 18은 같은 이벤트 처리 구간의 여러 state 업데이트를 **batching(일괄 처리)**하여 불필요한 중간 렌더링을 줄인다. 두 setter를 호출했다고 반드시 화면을 두 번 commit하는 것은 아니다.

중요한 것은 setter가 호출된 순서마다 즉시 DOM이 바뀐다고 생각하지 않는 것이다. React는 업데이트를 모아 다음 UI를 계산하고 commit한다.

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

## 7. props로 전달된 `onLogin`이 실제 인증 로직을 실행한다

LoginForm 입장에서 `onLogin`은 props로 받은 함수다. 실제 함수는 App이 `useAuth`에서 받은 `login`이다.

```text
LoginForm.handleSubmit
→ props의 onLogin(email, password)
→ useAuth.login(email, password)
→ authApi.login(...)
→ authApi.fetchMe()
```

### 7-1. custom Hook이 UI와 인증 절차를 분리한다

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

역할은 세 층으로 나뉜다.

| 층 | 책임 |
|---|---|
| `LoginForm` | 입력값, 제출 중 상태, 폼 내부 오류 UI |
| `useAuth` | 로그인 절차와 앱의 사용자 state |
| `authApi` | URL, HTTP method, body, cookie 옵션 |

이 분리 덕분에 LoginForm은 SESSION 쿠키나 `/api/auth/me` 주소를 알 필요가 없다. 반대로 `authApi`는 React state와 화면을 알지 못한다.

### 7-2. `useCallback`은 함수를 실행하지 않고 참조를 기억한다

```tsx
const login = useCallback(async (...) => {
  // 로그인 동작
}, []);
```

`useCallback`은 전달한 함수를 즉시 실행하는 Hook이 아니다. 다음 렌더링에서도 의존성이 같으면 이전과 같은 함수 객체를 반환한다.

```text
useCallback(함수, 의존성)
→ 함수의 반환 결과를 캐시하지 않음
→ 함수 자체의 참조를 유지
```

여기서는 의존성 배열이 `[]`이므로 App이 같은 mount에 있는 동안 `login` 함수 참조가 유지된다. React의 state setter와 import한 모듈 함수는 안정적인 값이므로 현재 콜백에는 변하는 의존성이 없다.

나중에 콜백 안에서 props나 state를 읽도록 코드를 바꾸면 그 값을 의존성 배열에 포함해야 한다. 그렇지 않으면 콜백이 오래된 렌더링 값을 기억하는 문제가 생길 수 있다.

`useCallback`은 이 로그인 절차의 정확성 자체를 만드는 Hook은 아니다. 함수 참조 안정성이 필요한 자식 최적화나 다른 Hook의 의존성에 유용하다.

### 7-3. `authApi`와 `fetch`는 React가 아니라 브라우저 기능이다

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

로그인 응답에는 현재 사용자 전체 정보가 없으므로 두 번째 요청으로 사용자를 조회한다.

```tsx
export async function fetchMe(): Promise<User | null> {
  const res = await fetch('/api/auth/me', { credentials: 'include' });
  if (res.status === 401) return null;
  if (!res.ok) throw await toHttpError(res, '로그인 상태를 확인하지 못했습니다.');
  return res.json();
}
```

전체 서버 흐름은 다음과 같다.

```text
POST /api/auth/login
→ 서버가 자격 증명 검사
→ 성공 응답의 Set-Cookie
→ 브라우저가 SESSION 쿠키 저장

GET /api/auth/me
→ 브라우저가 SESSION 쿠키 포함
→ 서버가 현재 User JSON 응답
→ setUser(authenticatedUser)
```

### 7-4. state를 어디에 둘지는 사용하는 범위로 결정한다

| state | 실제 소유 위치 | 사용하는 범위 |
|---|---|---|
| `form` | App | 어떤 인증 폼을 보여 줄지 결정 |
| `email`, `password` | LoginForm | LoginForm 입력 UI |
| `error`, `submitting` | LoginForm | LoginForm 제출 UI |
| `user`, `loading`, 인증 `error` | App이 호출한 `useAuth` Hook state | 앱 전체 인증 UI |

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

`authApi.login`이나 `fetchMe`가 Error를 던지면 Promise 실패가 호출 경로를 거슬러 LoginForm의 `catch`까지 전달된다.

```text
authApi에서 Error 발생
→ useAuth.login Promise 실패
→ await onLogin(...)에서 throw
→ handleSubmit의 catch 실행
```

```tsx
catch (err) {
  setError(err instanceof Error ? err.message : '로그인에 실패했습니다.');
} finally {
  setSubmitting(false);
}
```

`err instanceof Error`는 잡힌 값이 JavaScript Error 객체인지 확인한다. Error라면 서버에서 변환된 `message`를 쓰고, 아니라면 기본 문구를 사용한다.

실패할 때는 `onClose()`가 실행되지 않으므로 `form`은 계속 `'login'`이다. 같은 위치에 같은 LoginForm이 남아 있어 mount가 유지된다.

```text
email/password
→ 기존 state 유지

error
→ 오류 문자열로 변경

submitting
→ false로 돌아감

결과
→ 입력값과 폼은 유지
→ 오류 문구 표시
→ 로그인 버튼 다시 활성화
```

re-render가 발생해도 컴포넌트 타입과 트리 위치가 같으면 React는 기존 state를 보존한다. 실패 후 입력값이 사라지지 않는 핵심 이유다.

### 8-3. 성공과 실패 비교

| 결과 | App의 `user` | App의 `form` | LoginForm | 지역 state |
|---|---|---|---|---|
| 성공 | 사용자 객체 | `null` | unmount | 폐기 |
| 실패 | 변경 없음 | `'login'` | re-render | 입력 유지, 오류 갱신 |

---

## 9. 사용자가 취소 버튼을 누른다

성공·실패 경로와 별개로, 제출 전이나 요청 중 사용자가 폼을 닫는 경로도 살펴본다.

```tsx
<button type="button" className="ghost" onClick={onClose}>
  취소
</button>
```

`type="button"`은 form 안에서도 submit을 시작하지 않는 일반 버튼이다. `<button>`의 기본 type은 form 문맥에서 submit이 될 수 있으므로 취소 버튼에는 명시하는 것이 안전하다.

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
