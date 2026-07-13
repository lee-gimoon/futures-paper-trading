# DOM과 ReactDOM: React 화면이 브라우저에 그려지는 과정

이 문서는 현재 프로젝트의 `frontend/index.html`, `frontend/src/main.tsx`, `frontend/src/App.tsx`를 기준으로 다음 두 가지를 설명합니다.

- **DOM**: 브라우저가 HTML을 읽어 메모리에 만든 화면 요소의 객체 트리
- **ReactDOM**: React 컴포넌트가 만든 화면 내용을 브라우저 DOM에 붙이고 갱신하는 라이브러리

## 1. DOM이란?

DOM은 **Document Object Model**의 약자입니다. 브라우저는 HTML 파일을 단순한 문자열로만 보관하지 않습니다. HTML의 태그마다 JavaScript로 다룰 수 있는 객체를 만들고, 태그의 부모·자식 관계를 트리 구조로 연결합니다. 이 객체 트리가 DOM입니다.

### DOM은 왜 필요한가?

브라우저는 HTML을 화면에 한 번 보여 주는 데서 끝나지 않습니다. 사용자의 클릭, 입력, 서버에서 받은 데이터처럼 실행 중에 바뀌는 상황에 맞춰 화면도 변경해야 합니다.

예를 들어 사용자가 로그인하면 로그인 버튼을 숨기고 사용자 이름을 보여 주거나, 실시간 BTC 가격을 새 값으로 바꿔야 합니다. 이때 JavaScript가 HTML 문자열을 직접 수정하는 대신, 브라우저가 만든 DOM 객체를 찾아 속성·텍스트·자식 요소를 변경합니다.

```text
HTML 파일
  ↓ 브라우저가 읽음
DOM 객체 트리 생성
  ↓
브라우저가 화면에 그림

JavaScript
  ↓ DOM 객체 변경
브라우저가 변경된 DOM을 다시 화면에 반영
```

예를 들어 브라우저가 다음 HTML을 받으면,

```html
<body>
  <main>
    <h1>BTCUSDT</h1>
    <p>현재가</p>
  </main>
</body>
```

브라우저 메모리에는 개념적으로 다음 DOM 트리가 만들어집니다.

```text
document
└─ body
   └─ main
      ├─ h1
      │  └─ "BTCUSDT"
      └─ p
         └─ "현재가"
```

JavaScript는 `document` 객체를 통해 이 DOM을 찾고 변경할 수 있습니다.

```ts
const title = document.querySelector('h1'); // query(질의·조회) + selector(선택 조건): CSS 선택자로 첫 번째 h1을 찾는다.
title?.textContent = 'ETHUSDT';
```

이 코드는 실제 화면의 `<h1>` 텍스트도 `ETHUSDT`로 바꿉니다.

## 2. 현재 프로젝트에서 처음 만들어지는 DOM

사용자가 Vite 개발 서버 주소인 `http://localhost:5173/`에 접속하면, Vite는 `frontend/index.html`을 응답합니다. 브라우저는 HTML을 위에서 아래로 읽어 DOM을 만듭니다.

```html
<body>
  <div id="root"></div>
  <script type="module" src="/src/main.tsx"></script>
</body>
```

이 시점의 핵심 DOM은 아직 비어 있는 `root` 영역입니다.

```text
document
└─ body
   └─ div#root
      └─ (비어 있음)
```

`<script type="module">`을 만난 브라우저는 `main.tsx`를 요청합니다. Vite는 TSX 코드를 브라우저가 실행할 JavaScript로 변환해 전달합니다.

## 3. ReactDOM이란?

`ReactDOM`은 React와 브라우저 DOM 사이를 연결하는 **웹 브라우저용 렌더러**입니다. 여기서 렌더러는 React가 계산한 화면 구조를 브라우저 DOM 요소로 만들고 갱신하는 도구라는 뜻입니다.

React는 `<App />`, `<h1>BTCUSDT</h1>`처럼 “현재 상태라면 어떤 화면이 필요할지”를 컴포넌트와 JSX로 계산합니다. 하지만 React 자체가 브라우저의 `<div>`나 `<h1>`을 직접 만드는 것은 아닙니다. ReactDOM이 그 결과를 받아 `document.createElement(...)`, `appendChild(...)` 같은 브라우저 DOM 기능으로 실제 요소를 `#root` 안에 생성·수정합니다.

```text
React: "h1에 BTCUSDT를 보여야 한다"는 화면 구조를 계산
  ↓
ReactDOM: 실제 h1 DOM 요소를 만들고 div#root에 연결
  ↓
브라우저 렌더링 엔진: DOM을 분석해 화면의 픽셀로 그림
```

따라서 ReactDOM은 픽셀을 직접 그리는 브라우저 엔진이 아닙니다. React의 화면 결과를 **브라우저가 그릴 수 있는 DOM으로 반영하는 연결층**입니다.

```text
React 컴포넌트와 JSX
        ↓
ReactDOM
        ↓
브라우저 DOM
        ↓
사용자에게 보이는 화면
```

React는 `<App />`처럼 어떤 화면을 원하는지 컴포넌트와 JSX로 표현합니다. ReactDOM은 그 결과를 받아 실제 DOM 요소를 만들고, `#root` 아래에 붙입니다.

현재 프로젝트의 `main.tsx` 핵심 코드는 다음과 같습니다.

```tsx
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
```

각 부분의 역할은 다음과 같습니다.

| 코드 | 역할 |
|---|---|
| `document.getElementById('root')` | DOM에서 `<div id="root">` 객체를 찾는다. |
| `ReactDOM.createRoot(...)` | 찾은 `div`를 React가 관리할 화면의 루트로 등록한다. |
| `.render(...)` | React 컴포넌트 트리를 이 루트에 처음 그리거나 갱신한다. |
| `<App />` | 실제 애플리케이션 화면의 최상위 컴포넌트다. |
| `<React.StrictMode>` | 개발 중 잘못된 사용을 찾도록 돕는 검사 영역이다. 화면에 별도 DOM 태그를 만들지는 않는다. |

`getElementById('root')!` 뒤의 `!`는 TypeScript에게 “이 요소는 반드시 존재한다”고 알려 주는 문법입니다. 실제로는 `index.html`에 `<div id="root"></div>`가 있으므로 그 전제는 맞습니다.

## 4. 화면을 처음 그리는 전체 흐름

```text
1. 브라우저가 사이트 URL에 접속
2. Vite가 index.html 응답
3. 브라우저가 index.html을 읽어 div#root DOM 생성
4. 브라우저가 module script를 발견하고 /src/main.tsx 요청
5. Vite가 main.tsx와 import된 파일들을 JavaScript로 변환해 제공
6. main.tsx가 document.getElementById('root')로 div#root를 찾음
7. ReactDOM.createRoot(...)가 그 div를 React 루트로 등록
8. render(<App />)가 App과 하위 컴포넌트를 DOM으로 그려 넣음
```

처음에는 비어 있던 `root`가 렌더링 뒤에는 다음처럼 채워집니다. 실제 태그 구성은 `App.tsx`와 하위 컴포넌트의 JSX에 따라 달라집니다.

```text
렌더링 전
div#root
└─ (비어 있음)

렌더링 후
div#root
└─ App이 만든 DOM 요소들
   ├─ header
   ├─ main
   │  ├─ 주문 화면
   │  ├─ 호가창
   │  └─ 차트
   └─ footer 등
```

## 5. JSX가 DOM으로 바뀌는 모습

`App.tsx` 같은 컴포넌트가 다음 JSX를 반환한다고 가정해 보겠습니다.

```tsx
function App() {
  return (
    <main>
      <h1>BTCUSDT</h1>
      <p>현재가: 100,000</p>
    </main>
  );
}
```

JSX는 HTML처럼 보이지만 TypeScript 안에서 쓰는 React 문법입니다. React는 이 JSX로 어떤 UI가 필요한지 표현하고, ReactDOM은 실제 브라우저 DOM을 다음과 비슷하게 만듭니다.

```html
<div id="root">
  <main>
    <h1>BTCUSDT</h1>
    <p>현재가: 100,000</p>
  </main>
</div>
```

즉, JSX의 `<main>`, `<h1>`, `<p>`는 브라우저 DOM의 실제 HTML 요소가 됩니다. 반면 `<App />`처럼 대문자로 시작하는 것은 사용자 정의 React 컴포넌트이므로, React가 해당 함수를 실행해 반환한 JSX로 바꾼 뒤 DOM에 반영합니다.

```text
<App />
  ↓ App 함수 실행
<main><h1>...</h1></main>
  ↓ ReactDOM이 실제 DOM 생성
<div id="root"><main><h1>...</h1></main></div>
```

## 6. 값이 바뀔 때는 어떻게 갱신할까?

React 앱에서는 상태가 바뀌면 컴포넌트가 새 화면 결과를 다시 계산합니다. ReactDOM은 이전 결과와 새 결과를 비교해 실제 DOM에서 필요한 부분만 갱신합니다.

```tsx
function Price() {
  const [price, setPrice] = useState(100_000);

  return (
    <>
      <p>현재가: {price}</p>
      <button onClick={() => setPrice(101_000)}>가격 변경</button>
    </>
  );
}
```

버튼을 누른 뒤의 흐름은 다음과 같습니다.

```text
사용자 클릭
  ↓
setPrice(101_000)
  ↓
Price 컴포넌트가 새 JSX를 계산
  ↓
ReactDOM이 이전 화면과 비교
  ↓
<p>의 텍스트만 100,000 → 101,000으로 변경
```

개발자가 매번 `document.querySelector(...)`로 DOM을 직접 찾아 수정하지 않아도 되는 이유가 여기에 있습니다. 개발자는 “현재 상태에서 화면이 어떻게 보여야 하는지”를 JSX로 작성하고, ReactDOM이 DOM 반영을 맡습니다.

## 7. React와 ReactDOM을 구분해서 기억하기

```text
React
  - 컴포넌트, JSX, 상태(state)로 UI를 정의한다.
  - "이 상태라면 이런 화면이어야 한다"를 계산한다.

ReactDOM
  - React 결과를 웹 브라우저 DOM에 연결한다.
  - 처음 DOM을 만들고, 변경된 부분을 DOM에 반영한다.
```

React는 웹 브라우저만을 위한 라이브러리가 아닙니다. 예를 들어 React Native는 모바일 화면에 React 컴포넌트를 그립니다. `react-dom/client`는 그중 브라우저 DOM에 그리기 위한 패키지이며, 현재 프로젝트의 `ReactDOM.createRoot(...).render(...)`가 바로 그 시작점입니다.

## 8. 이 프로젝트에서 기억할 한 줄

```text
index.html이 div#root를 만들고, main.tsx의 ReactDOM이 App을 그 안에 렌더링한다.
```
