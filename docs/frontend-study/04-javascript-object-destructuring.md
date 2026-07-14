# JavaScript 객체 구조 분해 할당

이 문서는 `AuthenticatedTradingLayout` 컴포넌트에서 사용하는 **객체 구조 분해 할당(destructuring assignment)** 문법을 설명한다.

```tsx
function AuthenticatedTradingLayout({
  snapshot,
  midPrice,
  limitFill,
  onPriceClick,
  onUnauthorized,
}: TradingLayoutProps) {
  const { portfolio, orders, fills, loading, error, refresh } = useTrading(onUnauthorized);
  // ...
}
```

구조 분해 할당은 객체 안의 값을 꺼내 각각의 변수에 담는 JavaScript 문법이다. React나 TypeScript 전용 문법이 아니다.

## 1. 객체에서 값을 하나씩 꺼내기

먼저 다음과 같은 객체가 있다고 하자.

```ts
const market = {
  symbol: 'BTCUSDT',
  midPrice: 64_000,
};
```

일반적인 방식으로 값을 꺼내면 다음과 같다.

```ts
const symbol = market.symbol;
const midPrice = market.midPrice;
```

`market.symbol`은 `market` 객체 안의 `symbol` 속성 값을 뜻한다.

## 2. 구조 분해 할당으로 한 번에 꺼내기

위 코드는 구조 분해 할당으로 이렇게 작성할 수 있다.

```ts
const { symbol, midPrice } = market;
```

위 한 줄은 아래 코드와 같은 결과를 만든다.

```ts
const symbol = market.symbol;
const midPrice = market.midPrice;
```

왼쪽의 `{ symbol, midPrice }`는 `market` 객체에서 같은 이름의 속성을 찾아 변수로 꺼내라는 뜻이다.

```text
market
├─ symbol: 'BTCUSDT'  → symbol 변수
└─ midPrice: 64_000   → midPrice 변수
```

`const { ... } = 객체`는 단순한 비공식 축약이 아니라 JavaScript에 정식으로 포함된 문법이다. 속성을 여러 개 사용할 때 반복되는 `객체.속성` 표기를 줄여 준다.

## 3. 함수의 매개변수에서 바로 구조 분해하기

함수는 객체를 매개변수로 받을 수 있다.

```ts
function showMarket(market: { symbol: string; midPrice: number }) {
  console.log(market.symbol);
  console.log(market.midPrice);
}
```

함수 안에서 객체의 속성을 바로 사용하려면 `market.symbol`처럼 매번 객체 이름을 적어야 한다. 매개변수 위치에서 구조 분해하면 더 짧게 쓸 수 있다.

```ts
function showMarket({ symbol, midPrice }: { symbol: string; midPrice: number }) {
  console.log(symbol);
  console.log(midPrice);
}
```

이 함수는 아래처럼 호출한다.

```ts
showMarket({ symbol: 'BTCUSDT', midPrice: 64_000 });
```

호출한 쪽이 전달한 객체에서 `symbol`, `midPrice`를 꺼내 함수 안의 변수로 만든다.

## 4. `AuthenticatedTradingLayout`에 적용하기

프로젝트의 타입은 다음과 같다.

```ts
type TradingLayoutProps = {
  snapshot: OrderBookSnapshot | null;
  midPrice: number | null;
  limitFill: { price: number; n: number } | null;
  onPriceClick: (price: number) => void;
  onUnauthorized: () => void;
};
```

`TradingLayoutProps`는 객체 타입이다. 이 타입을 따르는 객체는 `snapshot`, `midPrice` 등 다섯 속성을 가져야 한다.

컴포넌트의 선언은 다음과 같다.

```tsx
function AuthenticatedTradingLayout({
  snapshot,
  midPrice,
  limitFill,
  onPriceClick,
  onUnauthorized,
}: TradingLayoutProps) {
  // ...
}
```

여기에는 두 문법이 함께 쓰였다.

```tsx
{ snapshot, midPrice, limitFill, onPriceClick, onUnauthorized } // JavaScript 객체 구조 분해
: TradingLayoutProps                                            // TypeScript 타입 표기
```

동작을 길게 풀어 쓰면 아래와 같다.

```tsx
function AuthenticatedTradingLayout(props: TradingLayoutProps) {
  const { snapshot, midPrice, limitFill, onPriceClick, onUnauthorized } = props;

  // ...
}
```

두 버전은 같은 값을 사용한다. 앞의 짧은 버전은 함수가 받을 때 바로 구조 분해한 것이다.

```text
App이 props 객체 전달
  ↓
AuthenticatedTradingLayout이 객체 수신
  ↓
snapshot, midPrice, limitFill 등을 각각의 변수로 꺼냄
```

## 5. 훅이 반환한 객체도 구조 분해할 수 있다

다음 코드에서도 같은 문법을 사용한다.

```ts
const { portfolio, orders, fills, loading, error, refresh } = useTrading(onUnauthorized);
```

`useTrading(onUnauthorized)`는 거래와 관련된 여러 값을 하나의 객체로 반환한다. 구조 분해를 사용하지 않으면 대략 다음처럼 작성해야 한다.

```ts
const trading = useTrading(onUnauthorized);

const portfolio = trading.portfolio;
const orders = trading.orders;
const fills = trading.fills;
const loading = trading.loading;
const error = trading.error;
const refresh = trading.refresh;
```

구조 분해 버전은 이 객체의 속성들을 한 줄에서 꺼내므로, 이후 코드에서 `trading.orders` 대신 `orders`를 바로 쓸 수 있다.

```ts
const openOrders = orders.filter((order) => order.status === 'OPEN');
```

## 6. 속성 이름과 변수 이름을 다르게 만들기

기본 구조 분해는 속성 이름과 변수 이름이 같아야 한다.

```ts
const { midPrice } = market;
```

다른 변수명을 원하면 `속성이름: 변수이름`을 사용한다.

```ts
const { midPrice: currentPrice } = market;

console.log(currentPrice); // 64_000
```

이것은 아래와 같은 뜻이다.

```ts
const currentPrice = market.midPrice;
```

## 7. 핵심 정리

```ts
const { a, b } = object;
```

는 다음과 같은 의미다.

```ts
const a = object.a;
const b = object.b;
```

그리고 함수 매개변수에서 사용하는 다음 문법은:

```ts
function example({ a, b }: SomeType) {}
```

아래 두 작업을 한 번에 작성한 것이다.

```ts
function example(props: SomeType) {
  const { a, b } = props;
}
```

- `{ a, b }`: JavaScript 객체 구조 분해
- `: SomeType`: TypeScript가 객체 모양을 검사하도록 하는 타입 표기
- React에서는 props나 커스텀 훅의 반환값처럼 여러 값을 가진 객체를 다룰 때 자주 사용한다.
