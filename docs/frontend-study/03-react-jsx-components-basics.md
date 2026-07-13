# React, JSX, 컴포넌트 기초

이 문서는 React 코드를 처음 읽기 전에 알아야 할 세 가지 개념을 설명합니다.

- **React**: 상태에 따라 화면을 선언적으로 만들고 갱신하는 UI 라이브러리
- **JSX**: JavaScript/TypeScript 안에서 화면 구조를 HTML처럼 작성하는 문법
- **컴포넌트(Component)**: 화면의 한 부분을 담당하는 재사용 가능한 React 함수

이 세 가지를 이해한 뒤 `03-dom-and-react-dom.md`를 읽으면, `main.tsx`의 `ReactDOM.createRoot(...).render(<App />)`가 무엇을 하는지 더 쉽게 연결할 수 있습니다.

## 1. React란 무엇인가?

React는 웹 화면(UI)을 만들기 위한 JavaScript 라이브러리입니다. 개발자는 “현재 데이터 상태라면 화면이 어떻게 보여야 하는지”를 코드로 작성하고, React는 상태가 바뀔 때 그 설명에 맞도록 화면을 갱신합니다.

예를 들어 현재가를 화면에 표시한다고 생각해 봅시다.

```tsx
function Price() {
  const price = 100_000;
  return <p>현재가: {price}</p>;
}
```

개발자는 `price`라는 값으로 어떤 화면을 보여 줄지만 작성합니다. `{price}`가 들어갈 위치를 직접 `document.querySelector(...)`로 찾아서 바꾸는 일은 React와 ReactDOM이 맡습니다.

```text
데이터 상태: price = 100,000
  ↓
React 컴포넌트가 화면 구조를 계산
  ↓
ReactDOM이 브라우저 DOM에 반영
  ↓
화면: "현재가: 100,000"
```

이처럼 결과 화면을 선언하는 방식을 **선언형 UI**라고 합니다.

```text
명령형 DOM 조작: "h1을 찾고 → 텍스트를 바꿔라"
선언형 React:  "price가 이 값이면 → 이 화면을 보여라"
```

이때 React를 사용하는 개발자는 `h1`을 직접 찾거나 텍스트를 바꾸는 절차를 작성하지 않습니다. `price`처럼 화면에 필요한 값과 그 값에 따른 화면 모양만 JSX로 선언합니다.

값이 바뀌면 React는 새 화면 결과를 계산하고, ReactDOM은 이전 화면과 비교해 실제 DOM에서 바뀌어야 할 부분을 찾아 수정합니다.

```text
개발자
  → "price가 이 값이면 이 화면을 보여라"라고 선언

React
  → 현재 값으로 새 화면 결과를 계산

ReactDOM
  → 필요한 DOM 요소를 찾아 생성·수정·삭제
```

React는 화면을 만들고 갱신하는 데 집중하는 라이브러리입니다. 웹 브라우저의 실제 DOM에 붙이는 역할은 `react-dom` 패키지의 ReactDOM이 담당합니다.

## 2. JSX란 무엇인가?

JSX는 보통 **JavaScript XML**의 약자라고 설명합니다.

```text
JS  = JavaScript
X   = XML에서 온 X
JSX = JavaScript XML
```

XML은 태그로 구조를 표현하는 마크업 형식입니다.

```xml
<price>100000</price>
```

JSX도 태그처럼 보이는 문법을 JavaScript 안에 쓰기 때문에 `X`가 붙었습니다.

```tsx
const title = <h1>BTCUSDT</h1>;
```

다만 JSX가 실제 XML이나 HTML인 것은 아닙니다. 현재 공식적으로는 **JavaScript를 위한 문법 확장(syntax extension)**이라고 설명하는 편이 더 정확합니다.

JSX는 JavaScript/TypeScript 코드 안에서 화면 구조를 HTML처럼 작성하는 문법입니다. 브라우저가 JSX를 그대로 실행하는 것도 아니며, Vite가 `.tsx` 파일의 JSX를 브라우저가 실행할 JavaScript로 변환합니다.

위 코드는 `<h1>` 태그를 화면에 바로 출력하는 것이 아니라, React에게 “`h1` 요소와 BTCUSDT라는 텍스트가 필요하다”고 설명하는 값을 만듭니다. ReactDOM이 이 결과를 실제 DOM으로 반영합니다.

```text
JSX: <h1>BTCUSDT</h1>
  ↓ Vite가 JavaScript로 변환
React가 이해하는 화면 구조
  ↓ ReactDOM이 DOM에 반영
<h1>BTCUSDT</h1> 실제 DOM 요소
```

JSX 안에는 중괄호 `{}`로 JavaScript/TypeScript 값을 넣을 수 있습니다.

```tsx
const symbol = 'BTCUSDT';
const price = 100_000;

const message = <p>{symbol} 현재가: {price}</p>;
```

JSX 태그는 이름의 대소문자에 따라 의미가 다릅니다.

```tsx
<div>일반 HTML 요소</div> // 소문자: 브라우저 DOM 태그
<App />                    // 대문자: 사용자가 만든 React 컴포넌트
```

## 3. 컴포넌트란 무엇인가?

컴포넌트는 화면의 한 부분을 담당하는 재사용 가능한 단위입니다. React에서는 보통 JSX를 반환하는 함수로 컴포넌트를 만듭니다.

```tsx
function Greeting() {
  return <h1>안녕하세요</h1>;
}
```

이 함수를 JSX 태그처럼 사용합니다.

```tsx
function App() {
  return (
    <main>
      <Greeting />
    </main>
  );
}
```

`<Greeting />`을 만나면 React는 `Greeting()` 함수를 실행하고, 그 함수가 반환한 JSX를 화면 구조에 포함합니다.

```text
<App />
  ↓ App 함수 실행
<main><Greeting /></main>
  ↓ Greeting 함수 실행
<main><h1>안녕하세요</h1></main>
  ↓ ReactDOM이 DOM에 반영
```

컴포넌트를 나누면 화면의 역할이 명확해집니다.

```text
App
├─ Header        : 상단 영역
├─ OrderBook     : 호가창
├─ CandleChart   : 캔들 차트
└─ TradingPanel  : 주문·계좌 영역
```

현재 프로젝트도 `App.tsx`가 최상위 컴포넌트이고, `OrderBook.tsx`, `CandleChart.tsx`, `TradingPanel.tsx`처럼 기능별 컴포넌트를 조합해 화면을 만듭니다.

## 4. 컴포넌트에 값을 전달하는 props

컴포넌트는 함수의 매개변수처럼 `props`를 받아 다른 값으로 화면을 만들 수 있습니다.

```tsx
type PriceProps = {
  symbol: string;
  price: number;
};

function Price({ symbol, price }: PriceProps) {
  return <p>{symbol} 현재가: {price}</p>;
}

function App() {
  return <Price symbol="BTCUSDT" price={100_000} />;
}
```

위 코드에서 `App`은 `Price`에 `symbol`, `price` 값을 전달합니다. `Price`는 전달받은 값을 JSX의 `{}` 안에 넣어 화면을 만듭니다.

```text
App
  └─ <Price symbol="BTCUSDT" price={100_000} />
        ↓ props 전달
     Price({ symbol, price })
        ↓ JSX 반환
     "BTCUSDT 현재가: 100000"
```

## 5. 상태가 바뀌면 컴포넌트가 다시 계산된다

화면에 표시해야 하는 값이 바뀌는 경우에는 React의 상태(state)를 사용합니다.

```tsx
import { useState } from 'react';

function Counter() {
  const [count, setCount] = useState(0);

  return (
    <>
      <p>횟수: {count}</p>
      <button onClick={() => setCount(count + 1)}>증가</button>
    </>
  );
}
```

버튼을 클릭하면 다음 흐름이 일어납니다.

```text
버튼 클릭
  ↓
setCount(count + 1)로 상태 변경
  ↓
React가 Counter 컴포넌트를 다시 실행해 새 JSX를 계산
  ↓
ReactDOM이 이전 화면과 비교
  ↓
바뀐 "횟수" 텍스트를 브라우저 DOM에 반영
```

처음에는 `useState`의 내부 동작보다 “상태가 바뀌면 React가 컴포넌트를 다시 계산하고 화면을 갱신한다”는 연결만 기억하면 충분합니다.

## 6. 현재 프로젝트의 시작 흐름

```text
index.html
  └─ <div id="root"></div> : React 화면을 붙일 빈 DOM 영역
  └─ <script src="/src/main.tsx"> : React 앱 시작 파일 요청

main.tsx
  └─ ReactDOM.createRoot(...).render(<App />)
       └─ App 컴포넌트 렌더링 시작

App.tsx
  └─ OrderBook, CandleChart, TradingPanel 등 하위 컴포넌트 조합
```

한 줄로 정리하면 다음과 같습니다.

```text
컴포넌트가 JSX로 화면을 설명하고, React가 상태에 따라 그 설명을 다시 계산하며, ReactDOM이 실제 브라우저 DOM에 반영한다.
```
