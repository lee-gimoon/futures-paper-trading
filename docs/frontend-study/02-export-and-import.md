# 프론트엔드 문법 2: export와 import

이 문서는 현재 프로젝트에서 자주 보이는 `export`와 `import`를 정리한 학습 노트입니다.

## 1. `export`는 어떤 언어 문법인가?

`export`는 JavaScript의 **ES Module(ECMAScript Module)** 문법입니다. 하나의 파일에 작성한 값, 함수, 클래스, 타입 등을 다른 파일에서 사용할 수 있도록 내보낼 때 사용합니다.

TypeScript도 JavaScript의 모듈 문법을 그대로 사용합니다. TypeScript에서는 여기에 `type`을 붙여 타입만 내보내는 기능을 추가로 사용할 수 있습니다.

```text
파일 A
  └─ export로 내보냄
          ↓
파일 B
  └─ import로 가져와 사용
```

`import`나 `export`가 있는 파일은 다른 파일과 연결되는 **모듈(module)**로 동작합니다.

---

## 2. 가장 기본적인 사용법

### 내보내기

```ts
// math.ts
export const add = (a: number, b: number) => a + b;

export function multiply(a: number, b: number) {
  return a * b;
}
```

`add`와 `multiply`를 다른 파일에서도 사용할 수 있도록 내보냈습니다.

### 가져오기

```ts
// app.ts
import { add, multiply } from './math';

console.log(add(1, 2));       // 3
console.log(multiply(2, 3));  // 6
```

정리하면 다음과 같습니다.

```text
export: 다른 파일에서 사용할 수 있도록 내보내기
import: 다른 파일에서 내보낸 것을 가져오기
```

`./math`의 `./`는 현재 파일이 있는 폴더를 기준으로 한다는 뜻입니다. 실제 프로젝트의 다음 코드도 같은 방식입니다.

```ts
// frontend/src/App.tsx
import { useOrderBookStream } from './market/hooks/useOrderBookStream';
import { OrderBook } from './market/components/OrderBook';
```

---

## 3. named export: 이름을 지정해서 내보내기

다음처럼 선언 앞에 `export`를 붙이면 named export가 됩니다.

```ts
export const API_URL = '/api';

export function fetchUser() {
  // ...
}

export class UserService {
  // ...
}
```

가져올 때는 중괄호 안에 내보낸 이름을 적습니다.

```ts
import { API_URL, fetchUser, UserService } from './user';
```

named export에서는 이름이 중요합니다.

```ts
// user.ts
export const API_URL = '/api';
```

```ts
// app.ts
import { API_URL } from './user'; // 올바른 이름
```

다음은 이름이 다르므로 바로 가져올 수 없습니다.

```ts
import { URL } from './user'; // 오류: URL이라는 export가 없음
```

### export를 선언과 나누어 작성하기

선언할 때 바로 `export`를 붙이지 않고, 파일 마지막에 한 번에 내보낼 수도 있습니다.

```ts
const add = (a: number, b: number) => a + b;
const subtract = (a: number, b: number) => a - b;

export { add, subtract };
```

두 방식은 같은 의미입니다.

```ts
export const add = (a: number, b: number) => a + b;
```

```ts
const add = (a: number, b: number) => a + b;
export { add };
```

---

## 4. export 이름 바꾸기

내보낼 때 이름을 바꿀 수 있습니다.

```ts
const add = (a: number, b: number) => a + b;

export { add as sum };
```

이 파일을 사용하는 곳에서는 `sum`이라는 이름으로 가져옵니다.

```ts
import { sum } from './math';

console.log(sum(1, 2));
```

가져오는 시점에도 이름을 바꿀 수 있습니다.

```ts
import { add as sum } from './math';

console.log(sum(1, 2));
```

이때 원래 파일의 export 이름은 `add`이고, 현재 파일 안에서만 `sum`이라는 이름으로 사용하는 것입니다.

---

## 5. default export: 기본값 하나를 내보내기

`export default`는 해당 파일의 대표값 하나를 내보내는 문법입니다.

```ts
// greeting.ts
export default function greeting(name: string) {
  return `안녕하세요, ${name}님`;
}
```

가져올 때는 중괄호를 쓰지 않습니다. 또한 가져오는 파일에서 이름을 자유롭게 정할 수 있습니다.

```ts
import greeting from './greeting';
import sayHello from './greeting';

greeting('홍길동');
sayHello('홍길동');
```

두 import는 같은 default export를 가져오지만, 현재 파일에서 붙인 이름만 다릅니다.

### 현재 프로젝트의 default export

`frontend/src/App.tsx`에는 다음 코드가 있습니다.

```tsx
export default function App() {
  return <div>...</div>;
}
```

그리고 `frontend/src/main.tsx`에서 가져옵니다.

```tsx
import App from './App';
```

`App`은 default export이므로 중괄호를 쓰지 않습니다.

```ts
import App from './App';        // default export
import { App } from './App';    // named export일 때의 형태
```

### default export는 파일 하나에 하나만 가능

한 파일에서는 default export를 하나만 작성할 수 있습니다.

```ts
export default function App() {
  // ...
}

// export default function Other() {} // 오류: default export가 이미 있음
```

반면 named export는 여러 개 작성할 수 있습니다.

```ts
export const one = 1;
export const two = 2;
export function three() {}
```

---

## 6. 함수 자체를 내보내는 것과 호출 결과를 내보내는 것

괄호가 있는지 없는지에 따라 중요한 차이가 있습니다.

```ts
function createMessage() {
  return '메시지';
}

export default createMessage;
```

위 코드는 함수 자체를 내보냅니다. 다른 파일에서 가져온 뒤 호출합니다.

```ts
import createMessage from './message';

const message = createMessage();
```

반면 다음 코드는 함수를 바로 호출하고, 반환값을 내보냅니다.

```ts
export default createMessage();
```

이를 나누어 쓰면 더 명확합니다.

```ts
const result = createMessage();
export default result;
```

### 현재 프로젝트의 Vite 설정

`frontend/vite.config.ts`의 다음 코드는 `defineConfig` 함수 자체를 내보내는 것이 아닙니다.

```ts
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
});
```

순서는 다음과 같습니다.

```text
1. import로 defineConfig 함수 자체를 가져옴
2. defineConfig({...})를 호출함
3. 호출 결과인 설정값을 반환받음
4. 반환값을 export default로 내보냄
5. Vite가 그 설정을 읽고 개발 서버에 적용함
```

개념적으로는 다음과 같습니다.

```ts
const config = defineConfig({
  plugins: [react()],
});

export default config;
```

여기서 `import`와 `export`의 역할은 서로 반대입니다.

```ts
import { defineConfig } from 'vite'; // 함수 자체를 가져옴
export default config;               // 설정값을 내보냄
```

---

## 7. TypeScript의 `export type`

TypeScript에서는 타입도 다른 파일로 내보낼 수 있습니다.

```ts
// types.ts
export type User = {
  id: number;
  name: string;
};
```

가져올 때는 `import type`을 사용합니다.

```ts
import type { User } from './types';

const user: User = {
  id: 1,
  name: '홍길동',
};
```

`User`는 실제 실행 시 필요한 값이 아니라 TypeScript의 타입 정보입니다. TypeScript가 JavaScript로 변환될 때 타입 정보는 제거되므로, `import type`이라고 명시하면 “실행되는 값이 아니라 타입만 가져온다”는 의도가 분명해집니다.

현재 프로젝트의 `frontend/src/shared/types.ts`에도 다음과 같은 코드가 있습니다.

```ts
export type OrderBookLevel = {
  price: number;
  quantity: number;
};
```

다른 파일에서는 다음처럼 사용합니다.

```ts
import type { OrderBookLevel } from '../../shared/types';
```

타입과 실제 값을 함께 내보내는 것도 가능합니다.

```ts
export type User = {
  name: string;
};

export const defaultUser: User = {
  name: '홍길동',
};
```

```ts
import { defaultUser } from './user';
import type { User } from './user';
```

---

## 8. 프로젝트 코드에서 여러 export 가져오기

`frontend/src/paper/api/paperApi.ts`에는 여러 API 함수가 named export로 선언되어 있습니다.

```ts
export async function createOrder() {
  // ...
}

export async function listOrders() {
  // ...
}

export async function cancelOrder(id: number) {
  // ...
}
```

다른 파일에서는 필요한 함수를 골라서 가져올 수 있습니다.

```ts
import { createOrder, listOrders } from '../api/paperApi';
```

모든 named export를 하나의 객체처럼 가져오는 방법도 있습니다.

```ts
import * as paperApi from '../api/paperApi';

paperApi.createOrder(/* ... */);
paperApi.listOrders();
```

현재 프로젝트의 `useTrading.ts`와 여러 컴포넌트에서 이 방식을 사용합니다.

```ts
import * as paperApi from '../api/paperApi';
```

`paperApi`는 모듈의 여러 export를 모아 둔 객체처럼 사용할 수 있습니다.

---

## 9. 다시 내보내기(re-export)

한 파일에서 다른 파일의 export를 다시 내보낼 수도 있습니다.

```ts
// components/index.ts
export { OrderBook } from './OrderBook';
export { CandleChart } from './CandleChart';
```

그러면 사용하는 쪽에서는 각 파일의 경로를 따로 적지 않고 한 곳에서 가져올 수 있습니다.

```ts
import { OrderBook, CandleChart } from './components';
```

타입도 다시 내보낼 수 있습니다.

```ts
export type { OrderBookLevel, OrderBookSnapshot } from './types';
```

---

## 10. 자주 헷갈리는 문법 비교

### named export와 named import

```ts
// math.ts
export const add = (a: number, b: number) => a + b;

// app.ts
import { add } from './math';
```

이름이 일치해야 합니다.

### default export와 default import

```ts
// App.tsx
export default function App() {}

// main.tsx
import App from './App';
```

중괄호를 사용하지 않으며, import하는 쪽에서 이름을 바꿔도 됩니다.

```ts
import MainApp from './App';
```

### `export`와 `export default` 비교

```ts
export const add = () => 1;       // named export
export default function App() {}  // default export
```

```ts
import App, { add } from './module';
```

앞의 `App`은 default export이고, 중괄호 안의 `add`는 named export입니다.

---

## 핵심 요약

```text
export                   다른 파일에서 사용할 수 있도록 내보내기
import                   다른 파일에서 내보낸 것을 가져오기
export { add }           named export
import { add }           named import
export default value     파일의 대표값 하나를 내보내기
import value             default export 가져오기
export type User         TypeScript 타입만 내보내기
import type { User }     TypeScript 타입만 가져오기
```

특히 다음 두 코드를 구분해야 합니다.

```ts
export default defineConfig;
```

→ `defineConfig` 함수 자체를 내보냅니다.

```ts
export default defineConfig({});
```

→ `defineConfig({})`를 호출한 뒤 반환된 값을 내보냅니다.

그리고 다음 규칙을 기억하면 됩니다.

> `import`는 가져오기, `export`는 내보내기입니다. 함수 뒤에 `()`가 있으면 함수를 호출한 것이고, 없으면 함수 자체를 값으로 다루는 것입니다.
