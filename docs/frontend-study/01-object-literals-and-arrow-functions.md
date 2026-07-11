# 프론트엔드 문법 1: 객체 리터럴과 화살표 함수

이 문서는 현재 프로젝트의 `frontend` 코드에서 자주 보이는 두 가지 문법을 정리한 학습 노트입니다.

- 객체 리터럴: 여러 값을 이름 붙여 하나의 값으로 묶는 문법
- 화살표 함수: 함수를 짧게 작성하는 문법

예제는 JavaScript와 TypeScript에서 모두 볼 수 있습니다. TypeScript에서는 JavaScript 문법에 타입 표기를 추가할 수 있습니다.

## JavaScript와 TypeScript는 브라우저에서 어떻게 실행될까?

브라우저는 기본적으로 JavaScript를 실행합니다.

```html
<script>
  const add = (a, b) => a + b;
  console.log(add(1, 2));
</script>
```

이런 JavaScript는 별도 변환 없이 브라우저가 이해할 수 있습니다. 다만 아주 최신 문법을 오래된 브라우저에서 사용한다면 호환성을 위해 변환 도구가 필요할 수 있습니다.

반면 TypeScript는 브라우저가 직접 실행하지 못합니다.

```ts
const age: number = 20;
```

브라우저는 `: number`처럼 타입을 표시하는 TypeScript 문법을 이해하지 못합니다. 따라서 실행 전에 JavaScript로 변환해야 합니다.

```js
const age = 20;
```

이 변환에는 TypeScript 컴파일러(`tsc`), Vite, Webpack, Babel, esbuild 같은 도구가 사용됩니다. 현재 프로젝트에서는 Vite가 TypeScript와 React 코드를 브라우저가 실행할 수 있는 JavaScript로 처리합니다.

```text
TypeScript 코드
      ↓
Vite / TypeScript 컴파일러
      ↓
JavaScript 코드
      ↓
브라우저 실행
```

핵심은 다음과 같습니다.

> JavaScript는 브라우저가 직접 이해하지만, TypeScript는 JavaScript로 변환된 뒤 브라우저에서 실행됩니다.

---

## 1. 객체 리터럴(object literal)

객체 리터럴은 중괄호 `{}` 안에 `속성명: 값`을 작성해서 객체를 만드는 문법입니다.

```js
const user = {
  name: '홍길동',
  age: 20,
};
```

위 코드는 다음과 같은 객체 하나를 만듭니다.

```text
user
├─ name: '홍길동'
└─ age: 20
```

### 속성에 접근하기

```js
console.log(user.name);     // 홍길동
console.log(user['age']);   // 20
```

일반적인 이름은 점(`.`)으로 접근하고, 속성명이 문자열이거나 변수로 정해질 때는 대괄호(`[]`)를 사용할 수 있습니다.

```js
const field = 'name';
console.log(user[field]);    // 홍길동
```

### 프로젝트의 Vite 설정 객체

`frontend/vite.config.ts`의 `defineConfig({ ... })` 안에 들어가는 값이 바로 객체 리터럴입니다.

```ts
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
```

구조를 펼쳐 보면 다음과 같습니다.

```text
설정 객체
├─ plugins: [react()]             // 배열
└─ server                        // 객체
   └─ proxy                      // 객체
      └─ '/api'                  // 속성명이 문자열인 객체
         ├─ target: 문자열
         ├─ changeOrigin: boolean
         └─ configure: 함수
```

객체를 변수에 먼저 저장한 뒤 함수에 전달해도 결과는 같습니다.

```js
const config = {
  server: {
    port: 5173,
  },
};

defineConfig(config);
```

### 객체 안에 배열과 객체 넣기

객체의 값에는 문자열, 숫자, boolean, 배열, 다른 객체, 함수 등을 모두 넣을 수 있습니다.

```js
const order = {
  symbol: 'BTCUSDT',
  quantity: 0.01,
  options: ['MARKET', 'LIMIT'],
  account: {
    leverage: 5,
  },
};

console.log(order.account.leverage); // 5
```

### TypeScript의 타입 선언과 객체 리터럴 비교

프로젝트의 `frontend/src/shared/types.ts`에는 다음처럼 타입을 정의한 코드가 있습니다.

```ts
export type OrderBookLevel = {
  price: number;
  quantity: number;
};
```

`type OrderBookLevel = { ... }`는 실제 객체를 만드는 것이 아니라, 객체가 어떤 모양이어야 하는지 설명하는 **타입 선언**입니다.

앞의 `export`는 이 타입을 다른 파일에서도 사용할 수 있도록 내보낸다는 뜻입니다. 따라서 다른 파일에서는 다음처럼 가져올 수 있습니다.

```ts
import type { OrderBookLevel } from './types';
```

`export`가 없으면 `OrderBookLevel`은 선언한 파일 안에서만 사용할 수 있습니다. `export type`은 실행되는 값이 아니라 TypeScript의 타입 정보만 내보낼 때 사용하는 형태입니다.

```ts
const level: OrderBookLevel = {
  price: 65000,
  quantity: 0.5,
};
```

이 코드는 `level`이라는 이름의 실제 객체를 만들고, 그 객체가 `OrderBookLevel` 타입을 따르도록 지정한 것입니다.

```text
const              변수 선언
level              변수 이름
: OrderBookLevel   변수의 타입
= { ... }          실제 객체를 대입
```

따라서 `price`와 `quantity`가 빠지거나, 두 속성에 `number`가 아닌 값을 넣으면 TypeScript가 오류를 알려줍니다.

정리하면 다음과 같습니다.

```ts
type User = { name: string };       // 객체의 모양을 정의
const user = { name: '홍길동' };     // 실제 객체를 생성
```

### 객체 펼침 문법(spread)

프로젝트의 `paperApi.ts`에서는 기존 객체에 새로운 속성을 합칠 때 `...`을 사용합니다.

```ts
const input = {
  side: 'BUY',
  type: 'MARKET',
  quantity: 0.01,
};

const requestBody = {
  symbol: 'BTCUSDT',
  ...input,
};
```

결과는 다음과 같습니다.

```js
{
  symbol: 'BTCUSDT',
  side: 'BUY',
  type: 'MARKET',
  quantity: 0.01,
}
```

---

## 2. 화살표 함수(arrow function)

화살표 함수는 `function` 대신 `=>`를 사용해 함수를 작성하는 문법입니다.

```js
// 일반 함수
function add(a, b) {
  return a + b;
}

// 화살표 함수
const add = (a, b) => {
  return a + b;
};
```

함수 본문이 한 줄이면 중괄호와 `return`을 생략할 수 있습니다.

```js
const add = (a, b) => a + b;
```

매개변수가 하나면 괄호도 생략할 수 있지만, TypeScript에서 타입을 적을 때는 괄호를 쓰는 편이 명확합니다.

```js
const double = number => number * 2;

const triple = (number) => number * 3;
```

### TypeScript에서 타입 작성하기

```ts
const add = (a: number, b: number): number => {
  return a + b;
};
```

앞의 `number`는 매개변수 타입이고, 마지막 `: number`는 반환값 타입입니다.

### 프로젝트의 Vite 설정에서 사용한 화살표 함수

```ts
configure: (proxy) => {
  proxy.on('proxyRes', (proxyRes) => {
    proxyRes.headers['x-accel-buffering'] = 'no';
  });
},
```

여기서는 객체의 `configure` 속성에 화살표 함수를 값으로 넣었습니다. 그리고 `proxy.on()`의 두 번째 인자에도 화살표 함수를 넣었습니다.

```text
configure 속성
└─ (proxy) => { ... }
   └─ proxy.on(..., (proxyRes) => { ... })
```

### React 코드에서 사용한 화살표 함수

`useOrderBookStream.ts`에는 이벤트가 발생했을 때 실행할 함수를 전달하는 코드가 있습니다.

```ts
eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  setSnapshot(data);
};
```

의미는 다음과 같습니다.

> `eventSource`에 메시지가 도착하면 `(event) => { ... }` 함수를 실행한다.

`useEffect`의 정리 함수도 화살표 함수입니다.

```ts
useEffect(() => {
  const eventSource = new EventSource('/api/stream');

  return () => {
    eventSource.close();
  };
}, []);
```

### 객체 리터럴과 화살표 함수를 함께 사용하기

객체의 속성값으로 함수를 넣는 것은 매우 흔합니다.

```ts
const button = {
  label: '주문 취소',
  onClick: () => {
    console.log('주문을 취소합니다.');
  },
};

button.onClick();
```

프로젝트의 Vite 설정에서 `configure`가 이 형태이고, React 이벤트 처리 코드에서도 비슷한 방식을 사용합니다.

---

## 오늘의 핵심 요약

```js
// 객체 리터럴: 값을 이름 붙여 묶는다.
const config = {
  port: 5173,
  onStart: () => console.log('시작'),
};

// 화살표 함수: 함수를 간결하게 작성한다.
const square = (number) => number * number;
```

- `{ 속성명: 값 }`은 객체를 만드는 기본 문법입니다.
- 객체는 다른 객체, 배열, 함수도 값으로 가질 수 있습니다.
- `type X = { ... }`는 TypeScript 타입 선언이고, 실제 객체 생성은 `{ ... }`로 합니다.
- `(매개변수) => { 함수 본문 }`은 화살표 함수입니다.
- 객체의 속성에 화살표 함수를 넣으면 이벤트 처리기나 설정 콜백이 됩니다.
