# 실시간 호가창 데이터 흐름으로 배우는 React

이 문서는 사용자가 홈페이지에 접속한 뒤 **서버가 보낸 실시간 호가가 브라우저의 호가창으로 표시되고 계속 갱신되는 순서**를 따라간다.

`07-login-form-state-and-events.md`에서는 사용자의 입력과 제출 이벤트가 state를 바꾸는 흐름을 살펴봤다. 이번에는 사용자가 아무 버튼도 누르지 않아도, 서버가 보낸 SSE 메시지가 state를 바꾸고 React가 새 호가 화면을 만드는 흐름을 살펴본다.

학습 범위는 다음과 같다.

```text
페이지 접속
→ App 최초 render
→ useOrderBookStream의 Effect가 SSE 연결 생성
→ 서버가 호가 snapshot 전송
→ EventSource의 onmessage 실행
→ setSnapshot(data)
→ App re-render
→ snapshot을 OrderBook에 props로 전달
→ bids·asks 배열을 화면용 행으로 계산
→ map으로 호가 행 JSX 생성
→ ReactDOM이 실제 DOM을 갱신
→ 다음 snapshot이 도착할 때마다 반복
→ 연결이 더 이상 필요 없으면 Effect cleanup
```

> 이 문서에서는 **호가 가격 클릭과 주문 폼 연동을 다루지 않는다.** 현재 `OrderBook` 코드에 `onPriceClick`이 있지만, 여기서는 서버가 보낸 호가가 화면에 나타나는 과정까지만 집중한다. 가격 클릭은 자식 컴포넌트의 이벤트를 부모 state로 올리는 별도의 후속 주제로 다룰 수 있다.

---

## 0. 먼저 파일 역할을 확인하자

| 파일 | 역할 |
|---|---|
| `frontend/src/App.tsx` | 실시간 호가 `snapshot`을 받아 호가 데이터가 있는지 판단하고 `OrderBook`에 props로 전달한다. |
| `frontend/src/market/hooks/useOrderBookStream.ts` | 브라우저의 `EventSource`로 SSE에 연결하고 최신 snapshot을 React state에 저장한다. |
| `frontend/src/shared/types.ts` | 브라우저가 사용할 호가 snapshot과 호가 한 줄의 TypeScript 타입을 정의한다. |
| `frontend/src/market/components/OrderBook.tsx` | snapshot의 bids·asks 배열을 정렬·누적 계산한 뒤 호가 행 목록을 렌더링한다. |
| `frontend/src/market/engine/quote.ts` | snapshot에서 best bid, best ask, mid price, spread를 계산하는 순수 함수를 제공한다. |
| `frontend/src/styles.css` | 호가 행을 3열 grid로 배치하고 매도·매수 색상을 적용한다. |

백엔드에서는 다음 파일이 브라우저가 연결할 SSE 엔드포인트를 제공한다.

| 파일 | 이 문서에서 필요한 역할 |
|---|---|
| `BinanceFuturesDepthController.java` | `/api/binance-futures/btcusdt/depth/stream`을 `text/event-stream`으로 제공한다. |
| `LatestOrderBookSnapshotStore.java` | 새 snapshot이 발생할 때 구독 중인 SSE 연결로 전달할 스트림을 제공한다. |

이 문서의 React 중심 관계를 먼저 그리면 다음과 같다.

```text
useOrderBookStream
├─ useState: 최신 snapshot 기억
└─ useEffect: EventSource 연결과 정리
          │
          └─ snapshot 반환
                 ↓
                App
                 │ props
                 ↓
             OrderBook
                 │
                 ├─ asks → 정렬·누적 계산 → map → 매도 행 JSX
                 ├─ spread 계산
                 └─ bids → 정렬·누적 계산 → map → 매수 행 JSX
```

## 이 예제에서 가장 먼저 구분할 세 가지

### SSE는 통신 방식이다

SSE(Server-Sent Events)는 서버가 하나의 HTTP 연결을 열어 둔 채 브라우저에 여러 이벤트를 계속 보내는 방식이다. 현재 호가 데이터는 이 연결을 통해 브라우저로 들어온다.

### `EventSource`는 브라우저 기능이다

`EventSource`는 React가 제공하는 클래스가 아니다. 브라우저에 내장된 Web API다. 지정한 URL에 SSE 연결을 만들고, 서버 메시지가 도착하면 `message` 이벤트를 발생시킨다.

### `useEffect`는 React와 외부 시스템의 연결 수명을 맞춘다

React가 SSE 데이터를 직접 수신하지는 않는다. 커스텀 Hook의 Effect가 브라우저 `EventSource`를 생성하고, `EventSource`가 받은 데이터를 React state에 넣는다.

```text
서버/SSE                    브라우저 API                  React
호가 이벤트 전송  →  EventSource.onmessage 실행  →  setSnapshot(data)
```

---

## 1. 전체 데이터 경로부터 확인한다

홈페이지의 호가가 만들어지는 더 큰 경로는 다음과 같다.

```text
Binance Futures
→ 백엔드가 WebSocket으로 호가 수신
→ 백엔드가 OrderBookSnapshot으로 변환
→ LatestOrderBookSnapshotStore에 새 snapshot 발행
→ Spring WebFlux가 snapshot을 SSE 이벤트로 변환
→ 브라우저 EventSource가 SSE 메시지 수신
→ JSON 문자열을 JavaScript 객체로 변환
→ React snapshot state 갱신
→ App과 OrderBook re-render
→ ReactDOM이 실제 DOM 갱신
→ 브라우저가 호가창을 화면에 paint
```

이 중 React 학습의 핵심은 다음 부분이다.

```text
EventSource 메시지
→ state
→ re-render
→ props
→ 배열 계산
→ JSX 목록
→ DOM 갱신
```

백엔드가 Binance와 연결되는 내부 구현이나 Reactor의 `Flux`, `Sinks.Many` 동작은 별도의 백엔드 학습 주제다. 프런트엔드 입장에서는 다음 약속만 알면 된다.

```text
GET /api/binance-futures/btcusdt/depth/stream
Content-Type: text/event-stream

서버가 시간에 따라 OrderBookSnapshot JSON을 계속 보내 준다.
```

### 1-1. 일반 `fetch` 한 번과 SSE 연결은 다르다

일반적인 `fetch` 요청은 보통 요청 하나에 응답 하나를 받고 끝난다.

```text
fetch
브라우저 ── 요청 ──→ 서버
브라우저 ←─ 응답 하나 ── 서버
연결 작업 종료
```

SSE는 서버가 응답 연결을 열어 둔 채 이벤트를 여러 번 보낸다.

```text
EventSource/SSE
브라우저 ── 연결 요청 ──→ 서버
브라우저 ←─ snapshot 1 ── 서버
브라우저 ←─ snapshot 2 ── 서버
브라우저 ←─ snapshot 3 ── 서버
             ...
```

따라서 새로운 호가를 받기 위해 React가 100ms마다 `fetch`를 직접 반복하는 구조가 아니다. 브라우저가 하나의 `EventSource` 연결을 유지하고, 서버가 새 데이터를 보낼 때 `onmessage`가 실행된다.

### 1-2. SSE와 WebSocket도 구분한다

이 프로젝트에는 서로 다른 두 실시간 연결이 있다.

```text
Binance → 백엔드
WebSocket 사용

백엔드 → 브라우저
SSE 사용
```

WebSocket은 양방향 메시지 통신에 사용할 수 있다. SSE는 서버에서 브라우저로 데이터를 보내는 단방향 흐름이다. 현재 브라우저는 호가 데이터를 받기만 하면 되므로 SSE가 그 역할을 맡는다.

---

## 2. 페이지가 처음 렌더링될 때 snapshot은 아직 `null`이다

`App`은 함수 본문 첫 부분에서 `useOrderBookStream()`을 호출한다.

```tsx
export default function App() {
  const snapshot = useOrderBookStream();

  // 다른 Hook과 계산

  return (
    // 현재 snapshot에 맞는 JSX
  );
}
```

`useOrderBookStream`은 다음 state를 만든다.

```tsx
export function useOrderBookStream(): OrderBookSnapshot | null {
  const [snapshot, setSnapshot] = useState<OrderBookSnapshot | null>(null);

  // Effect 등록

  return snapshot;
}
```

### 2-1. 최초 state가 `null`인 이유

첫 render 시점에는 아직 Effect가 실행되지 않았고, 서버 메시지도 받지 않았다. 그래서 최신 호가 객체가 존재하지 않는다.

```text
첫 render
→ useState<OrderBookSnapshot | null>(null)
→ snapshot = null
→ useOrderBookStream이 null 반환
→ App의 snapshot 변수도 null
```

`null`은 오류라는 뜻이 아니라 **아직 표시할 첫 snapshot을 받지 못한 상태**다.

TypeScript 타입도 이 두 상태를 정확히 표현한다.

```tsx
OrderBookSnapshot | null
```

```text
null
→ 아직 호가 데이터 없음

OrderBookSnapshot
→ 화면에 표시할 호가 데이터 있음
```

### 2-2. 커스텀 Hook 파일에 있지만 state는 App의 Hook state다

`useOrderBookStream`은 별도의 컴포넌트가 아니다. JSX도 반환하지 않는다. App이 render 중 호출하는 커스텀 Hook이다.

따라서 `snapshot` state는 전역 변수나 독립적인 singleton state가 아니다. React는 이 state를 **`useOrderBookStream()`을 호출한 App의 Hook state 일부**로 관리한다.

```text
React가 App 렌더링
→ App이 useOrderBookStream 호출
→ 내부 useState 호출 위치를 App의 state 자리와 연결
→ Hook이 현재 snapshot 값을 App에 반환
```

다른 컴포넌트에서 `useOrderBookStream()`을 또 호출하면 기존 연결과 state를 자동 공유하는 것이 아니다. 그 컴포넌트에 연결된 새로운 state와 새로운 Effect가 생기고, 현재 구현이라면 `EventSource` 연결도 하나 더 만들어진다.

그래서 현재 프로젝트는 최상위 `App`에서 Hook을 한 번 호출하고, 같은 snapshot을 필요한 자식에게 props로 내려보낸다.

### 2-3. 첫 화면은 조건부 렌더링으로 대기 문구를 보여 준다

비로그인 화면의 호가 영역은 다음 조건으로 결정된다.

```tsx
{snapshot ? (
  <OrderBook snapshot={snapshot} />
) : (
  <p className="empty">호가 데이터 수신 대기 중...</p>
)}
```

위 코드는 이번 학습 범위에 맞게 가격 클릭 관련 prop을 생략한 형태다.

최초에는 `snapshot=null`이므로 조건이 거짓이다.

```text
snapshot = null
→ <OrderBook />을 이번 React 트리에 넣지 않음
→ 대기 문구를 React 트리에 넣음
→ ReactDOM이 실제 p 요소를 DOM에 생성
```

이 시점에 `OrderBook`은 아직 mount되지 않는다. App과 대기 문구가 먼저 화면에 반영된다.

로그인 사용자 화면의 `AuthenticatedTradingLayout` 안에서도 같은 원리로 snapshot 유무를 검사한다. 로그인 여부에 따라 레이아웃 위치는 달라지지만, **snapshot이 있어야 `OrderBook`을 렌더링한다**는 원칙은 같다.

---

## 3. 첫 commit 뒤 Effect가 SSE 연결을 만든다

`useOrderBookStream`의 전체 Effect는 다음과 같다.

```tsx
useEffect(() => {
  const eventSource = new EventSource(
    '/api/binance-futures/btcusdt/depth/stream'
  );

  eventSource.onmessage = (event) => {
    const data: OrderBookSnapshot = JSON.parse(event.data);
    setSnapshot(data);
  };

  eventSource.onerror = (err) => {
    console.error('SSE error', err);
  };

  return () => {
    eventSource.close();
  };
}, []);
```

### 3-1. 왜 연결을 render 중에 만들지 않을까?

컴포넌트의 render는 현재 props와 state로 JSX를 계산하는 단계다. render 중에 다음 코드를 직접 실행하면 App 함수가 다시 호출될 때마다 새 연결을 만들 위험이 있다.

```tsx
// 이렇게 render 본문에서 직접 만들면 안 된다.
const eventSource = new EventSource('/api/...');
```

snapshot이 도착해 `setSnapshot`이 실행되면 App은 다시 렌더링된다. render마다 `new EventSource(...)`를 실행한다면 데이터 하나를 받을 때마다 연결 수가 늘어날 수 있다.

```text
메시지 수신
→ state 변경
→ re-render
→ 새 EventSource 생성
→ 연결 증가
→ 더 많은 메시지 수신
→ 더 많은 re-render와 연결 생성
```

그래서 네트워크 연결처럼 React 바깥에 존재하는 시스템과의 동기화는 Effect에서 수행한다.

### 3-2. Effect는 첫 render 중이 아니라 commit 뒤에 실행된다

최초 실행 순서는 다음과 같다.

```text
React가 App 함수 호출
→ useOrderBookStream 호출
→ useEffect에 setup 함수 등록
→ snapshot=null로 JSX 계산
→ ReactDOM이 대기 화면 commit
→ 브라우저가 화면 표시
→ React가 Effect setup 실행
→ new EventSource(...)로 SSE 연결 시작
```

`useEffect(...)`를 호출하는 순간 `EventSource` 코드가 render 안에서 즉시 실행되는 것이 아니다. render에서는 Effect를 React에 등록하고, React가 commit 뒤 setup 함수를 실행한다.

### 3-3. `[]`는 이 Effect가 reactive dependency를 사용하지 않는다는 뜻이다

```tsx
useEffect(() => {
  // 연결 setup
  return () => {
    // 연결 cleanup
  };
}, []);
```

현재 Effect 안의 URL은 문자열 상수이고, App의 props나 state 같은 reactive value를 읽지 않는다. 빈 의존성 배열을 사용하므로 snapshot state가 바뀌어 App이 다시 렌더링되어도 이 Effect를 다시 setup하지 않는다.

```text
최초 commit
→ Effect setup
→ EventSource 1개 생성

snapshot 1 수신 → re-render
→ Effect setup 다시 실행하지 않음

snapshot 2 수신 → re-render
→ Effect setup 다시 실행하지 않음
```

`[]`를 단순히 “무조건 딱 한 번”이라고 외우기보다는 다음처럼 이해하는 것이 정확하다.

> 이 Effect는 렌더링 사이에 달라질 reactive dependency가 없으므로, 같은 mount 기간의 일반적인 update에서는 다시 동기화할 필요가 없다.

개발 환경의 `StrictMode`에서는 cleanup이 올바른지 검사하기 위해 초기 setup 뒤에 `cleanup → setup`이 추가로 실행될 수 있다. 운영 빌드의 정상 mount에서는 이 개발용 추가 검사가 없다.

### 3-4. `new EventSource(url)`은 무엇을 하는가?

```tsx
const eventSource = new EventSource(
  '/api/binance-futures/btcusdt/depth/stream'
);
```

이 코드는 다음 역할을 한다.

```text
브라우저 EventSource 객체 생성
→ 지정 URL로 HTTP GET 연결 시작
→ text/event-stream 응답 수신
→ 연결을 열린 상태로 유지
→ SSE 이벤트가 도착할 때 message 이벤트 발생
```

`eventSource` 지역 변수는 Effect setup 한 번의 실행에 속한다. cleanup 함수와 `onmessage` 콜백은 closure를 통해 이 객체와 `setSnapshot`을 계속 사용할 수 있다.

### 3-5. 개발 환경에서는 Vite proxy를 거친다

URL이 다음처럼 `/api`로 시작하는 상대 경로다.

```tsx
'/api/binance-futures/btcusdt/depth/stream'
```

로컬 개발에서 브라우저가 Vite 개발 서버에 접속했다면, `frontend/vite.config.ts`의 proxy 설정이 `/api` 요청을 Spring Boot 서버로 전달한다.

```text
브라우저
→ GET /api/binance-futures/btcusdt/depth/stream
→ Vite 개발 서버
→ proxy
→ Spring Boot localhost:8080
```

운영 Docker 환경에서는 Spring Boot가 React 정적 파일과 `/api`를 같은 서버에서 제공하므로 브라우저가 같은 출처의 Spring Boot로 바로 요청한다.

Vite나 Spring이 React state를 변경하는 것은 아니다. 이들은 SSE HTTP 응답을 전달하고, 브라우저의 `EventSource`가 그 응답을 받는다.

---

## 4. 서버가 SSE 이벤트 하나를 보낸다

백엔드 컨트롤러는 다음 엔드포인트를 제공한다.

```java
@GetMapping(
    path = "/api/binance-futures/btcusdt/depth/stream",
    produces = MediaType.TEXT_EVENT_STREAM_VALUE
)
public Flux<ServerSentEvent<OrderBookSnapshot>> stream() {
    return latestStore.stream()
            .map(snap -> ServerSentEvent.builder(snap).build());
}
```

프런트엔드 학습 관점에서는 다음 세 가지만 기억하면 된다.

1. `produces = text/event-stream`이므로 브라우저가 SSE 응답으로 처리할 수 있다.
2. 연결 한 번에서 `OrderBookSnapshot`이 시간에 따라 여러 번 전달된다.
3. 각 snapshot은 SSE의 `data` 영역에 JSON 형태로 들어온다.

개념적으로 브라우저에 도착하는 한 이벤트는 다음과 비슷하다.

```text
data: {"symbol":"BTCUSDT","eventTime":1720000000000,"bids":[{"price":65000.1,"quantity":0.42}],"asks":[{"price":65000.2,"quantity":0.31}]}

```

마지막 빈 줄은 SSE 이벤트 한 덩어리가 끝났음을 나타낸다. `EventSource`는 이 형식을 해석한 뒤 `data:` 뒤의 내용을 `MessageEvent.data`로 제공한다.

### 4-1. 서버 데이터와 TypeScript 타입의 모양

프런트엔드는 `shared/types.ts`에 같은 데이터 구조를 선언한다.

```tsx
export type OrderBookLevel = {
  price: number;
  quantity: number;
};

export type OrderBookSnapshot = {
  symbol: string;
  eventTime: number;
  bids: OrderBookLevel[];
  asks: OrderBookLevel[];
};
```

한 snapshot의 의미는 다음과 같다.

```text
OrderBookSnapshot
├─ symbol: 종목 이름
├─ eventTime: 이 호가 이벤트의 시간
├─ bids: 매수 호가 배열
│  └─ 각 원소: price + quantity
└─ asks: 매도 호가 배열
   └─ 각 원소: price + quantity
```

`bids`와 `asks`가 배열이므로 호가 한 줄씩 반복하여 화면에 만들 수 있다.

---

## 5. 메시지가 도착하면 `onmessage` 콜백이 실행된다

Effect setup은 `EventSource`에 다음 함수를 등록한다.

```tsx
eventSource.onmessage = (event) => {
  const data: OrderBookSnapshot = JSON.parse(event.data);
  setSnapshot(data);
};
```

### 5-1. 함수 등록과 함수 실행은 다른 시점이다

Effect가 실행될 때 화살표 함수의 본문이 바로 실행되는 것은 아니다.

```text
Effect setup 시점
→ onmessage 속성에 함수 객체를 저장

나중에 SSE 메시지가 도착한 시점
→ 브라우저가 message 이벤트 생성
→ 등록해 둔 함수에 event를 전달해 호출
```

이 구조는 로그인 폼의 이벤트 핸들러와 닮았다.

```tsx
// 사용자가 입력할 때 브라우저가 호출
<input onChange={(e) => setEmail(e.target.value)} />

// 서버 메시지가 도착할 때 브라우저가 호출
eventSource.onmessage = (event) => setSnapshot(...);
```

둘 다 브라우저가 이벤트를 발생시키고 등록된 콜백을 호출한다. 차이는 이벤트의 원인이다.

```text
onChange
→ 사용자의 입력이 원인

onmessage
→ 서버에서 도착한 SSE 데이터가 원인
```

### 5-2. `event.data`는 아직 문자열이다

SSE는 텍스트 형식이다. 서버가 JSON 모양의 데이터를 보냈더라도 `event.data`는 JavaScript 객체가 아니라 문자열이다.

```text
event.data
→ '{"symbol":"BTCUSDT", ... }'
→ typeof event.data === 'string'
```

그래서 `JSON.parse`가 필요하다.

```tsx
const data = JSON.parse(event.data);
```

```text
JSON 문자열
→ JSON.parse
→ JavaScript 객체와 배열
```

변환 후에는 다음처럼 속성에 접근할 수 있다.

```tsx
data.symbol
data.bids
data.asks
```

### 5-3. TypeScript 타입 표기는 런타임 검증이 아니다

현재 코드는 다음 타입 표기를 사용한다.

```tsx
const data: OrderBookSnapshot = JSON.parse(event.data);
```

이 표기는 이후 TypeScript 코드가 `data`를 `OrderBookSnapshot`으로 취급하게 한다. 하지만 브라우저가 실행할 때 서버 JSON의 필드를 실제로 검사해 주는 코드는 아니다.

예를 들어 서버가 잘못된 JSON이나 다른 모양의 객체를 보내도 `: OrderBookSnapshot` 자체가 런타임 오류를 미리 잡지는 못한다. 현재 프로젝트는 백엔드와 프런트엔드가 같은 schema 약속을 지킨다고 가정한다.

더 엄격한 애플리케이션이라면 JSON schema validator나 직접 작성한 type guard로 런타임 구조를 검증할 수 있다. 다만 그것은 현재 React 렌더링 흐름과는 별도의 주제다.

### 5-4. `setSnapshot(data)`가 React 갱신을 시작한다

```tsx
setSnapshot(data);
```

이 setter는 `snapshot` 지역 변수에 객체를 직접 대입하는 함수가 아니다. React가 관리하는 state에 새 값을 반영하도록 요청하고, 그 state를 소유한 App의 다음 render를 예약한다.

```text
onmessage 실행
→ JSON.parse로 새 객체 생성
→ setSnapshot(새 객체)
→ React state 업데이트 요청
→ App re-render 예약
```

`onmessage` 콜백이 직접 DOM 행을 생성하지 않는다는 점이 중요하다. 콜백은 최신 데이터를 state에 넣을 뿐이다. 어떤 DOM이 필요한지는 다음 render에서 JSX가 선언한다.

---

## 6. snapshot state가 바뀌면 App이 다시 렌더링된다

첫 메시지를 받기 전과 받은 후를 비교하면 다음과 같다.

```text
첫 메시지 전
React가 기억하는 snapshot state = null
App 안의 snapshot 지역 변수 = null

첫 메시지 수신
setSnapshot(snapshot1)

다음 render
React가 기억하는 snapshot state = snapshot1
App 안의 새 snapshot 지역 변수 = snapshot1
```

App 함수는 다음 render에서 처음부터 다시 실행된다.

```tsx
export default function App() {
  const snapshot = useOrderBookStream();

  // 나머지 Hook과 계산도 다시 실행

  return (...);
}
```

하지만 `useState(null)`의 초기값 `null`로 돌아가지는 않는다. `null`은 최초 mount 때만 쓰는 초기값이고, React가 최신 state 객체를 기억해 다음 `useState` 호출 위치에 돌려준다.

### 6-1. App이 재렌더링되어도 SSE 연결은 새로 만들지 않는다

App이 다시 실행되면 `useOrderBookStream`과 그 안의 `useEffect(...)` 호출도 코드상 다시 지나간다. 하지만 React는 빈 의존성 배열이 이전과 같음을 확인하므로 기존 Effect 연결을 그대로 유지한다.

```text
App 함수 재호출
→ useOrderBookStream 함수 재호출
→ useState가 최신 snapshot 반환
→ useEffect 등록 위치 확인
→ dependency 변화 없음
→ 기존 EventSource 연결 유지
```

여기서 구분해야 한다.

```text
커스텀 Hook 함수가 다시 호출됨
≠ Effect setup이 다시 실행됨
≠ EventSource를 새로 생성함
```

### 6-2. 조건부 렌더링 결과가 대기 문구에서 OrderBook으로 바뀐다

이제 `snapshot`은 객체이므로 조건이 참이다.

```tsx
{snapshot ? (
  <OrderBook snapshot={snapshot} />
) : (
  <p className="empty">호가 데이터 수신 대기 중...</p>
)}
```

```text
이전 render: snapshot=null
→ <p>호가 데이터 수신 대기 중...</p>

새 render: snapshot=OrderBookSnapshot 객체
→ <OrderBook snapshot={snapshot} ... />
```

React는 같은 위치의 요소 타입이 `p`에서 `OrderBook`으로 바뀐 새 트리를 계산한다. commit 과정에서 대기 문구 DOM을 제거하고 `OrderBook`이 반환한 DOM 구조를 추가한다. 이때 `OrderBook`이 처음 mount된다.

### 6-3. snapshot은 props로 아래 방향으로 전달된다

```tsx
<OrderBook snapshot={snapshot} />
```

위 코드는 가격 클릭 관련 prop을 생략하고 `snapshot`의 전달만 나타낸 것이다.

```text
App의 snapshot state 값
→ <OrderBook snapshot={snapshot}>
→ React가 props 객체 구성
→ OrderBook(props) 호출
```

개념적으로 React가 전달하는 props는 다음 모양이다.

```tsx
{
  snapshot: {
    symbol: 'BTCUSDT',
    eventTime: 1720000000000,
    bids: [...],
    asks: [...]
  }
}
```

`OrderBook`은 매개변수에서 `snapshot`을 구조 분해한다.

```tsx
export function OrderBook({ snapshot }: Props) {
  // snapshot으로 화면 계산
}
```

이 코드도 이번 주제에 필요한 매개변수만 남긴 축약 예시다.

App에서는 `snapshot`이 `OrderBookSnapshot | null`이지만, `OrderBook`의 prop 타입은 `OrderBookSnapshot`이다.

```tsx
type Props = {
  snapshot: OrderBookSnapshot;
};
```

실제 `Props`에는 가격 클릭 callback도 있지만, snapshot 렌더링을 이해하는 데 필요한 계약은 위 부분이다.

부모가 조건부 렌더링으로 `null`을 걸러낸 뒤에만 `OrderBook`을 만들기 때문에 자식은 `snapshot`이 실제 객체라고 믿고 사용할 수 있다.

---

## 7. OrderBook은 받은 snapshot으로 화면용 데이터를 계산한다

`OrderBook`은 자체 `useState`나 `useEffect`를 사용하지 않는다.

```tsx
export function OrderBook({ snapshot }: Props) {
  const askRows = buildAskRows(snapshot.asks);
  const bidRows = buildBidRows(snapshot.bids);

  const quote = deriveQuote(snapshot);
  const spread = quote ? quote.spread : 0;

  return (...);
}
```

위 예시는 실제 컴포넌트에서 가격 클릭 매개변수만 생략한 것이다.

현재 props가 같으면 같은 화면용 계산 결과와 JSX를 반환하는 표시 컴포넌트다.

```text
입력
→ snapshot prop

계산
→ askRows
→ bidRows
→ spread

출력
→ JSX
```

### 7-1. 서버 데이터와 화면용 `Row`는 모양이 다르다

서버가 보낸 호가 한 줄에는 가격과 수량이 있다.

```tsx
type OrderBookLevel = {
  price: number;
  quantity: number;
};
```

화면에서는 누적 수량도 보여 줘야 하므로 `OrderBook` 파일 안에서 화면 전용 타입을 정의한다.

```tsx
type Row = {
  price: number;
  quantity: number;
  cumulative: number;
};
```

```text
OrderBookLevel
price + quantity
       ↓ 누적 계산
Row
price + quantity + cumulative
```

`Row`는 서버가 보내거나 React가 특별히 관리하는 객체가 아니다. 현재 snapshot으로 render할 때 만드는 일반 JavaScript 객체다.

### 7-2. 매도 호가는 복사하고 정렬한다

```tsx
function buildAskRows(asks: OrderBookLevel[]): Row[] {
  const sorted = [...asks].sort((a, b) => a.price - b.price);
  let cum = 0;
  const rows = sorted.map((lvl) => {
    cum += lvl.quantity;
    return {
      price: lvl.price,
      quantity: lvl.quantity,
      cumulative: cum,
    };
  });
  return rows.reverse();
}
```

처리 순서는 다음과 같다.

```text
snapshot.asks
→ spread로 새 배열 복사
→ 가격 오름차순 정렬
→ best ask부터 수량 누적
→ 화면 배치를 위해 배열 순서 반전
```

예를 들어 다음 매도 호가가 있다고 하자.

```text
가격       수량
100.0      1.0
101.0      2.0
102.0      3.0
```

best ask인 100부터 누적하면 다음과 같다.

```text
가격       수량       누적
100.0      1.0        1.0
101.0      2.0        3.0
102.0      3.0        6.0
```

호가창에서는 높은 매도 가격을 위에, 시장과 가까운 best ask를 spread 바로 위에 두기 위해 마지막에 배열을 뒤집는다.

```text
화면 위
102.0      3.0        6.0
101.0      2.0        3.0
100.0      1.0        1.0  ← best ask
spread
화면 아래
```

### 7-3. 왜 `[...asks]`로 먼저 복사할까?

JavaScript의 `sort()`는 호출한 원본 배열 자체의 순서를 변경한다.

```tsx
asks.sort(...); // props 안의 배열을 직접 변경할 수 있음
```

하지만 `snapshot.asks`는 부모가 prop으로 전달한 데이터다. 컴포넌트는 props를 읽어서 JSX를 계산해야 하며, 받은 props를 직접 변경하면 안 된다.

그래서 spread 문법으로 새 배열을 만든 다음 새 배열만 정렬한다.

```tsx
const sorted = [...asks].sort(...);
```

```text
asks
→ 원본 배열: 그대로 유지

[...asks]
→ 원소 참조를 담은 새 배열
→ 이 새 배열의 순서만 sort로 변경
```

`OrderBookLevel` 객체 내부를 복사하는 깊은 복사는 아니지만, 여기서는 원소의 속성을 수정하지 않고 배열의 순서만 바꾸므로 새 배열 복사로 충분하다.

### 7-4. 매수 호가는 높은 가격부터 누적한다

```tsx
function buildBidRows(bids: OrderBookLevel[]): Row[] {
  const sorted = [...bids].sort((a, b) => b.price - a.price);
  let cum = 0;
  return sorted.map((lvl) => {
    cum += lvl.quantity;
    return {
      price: lvl.price,
      quantity: lvl.quantity,
      cumulative: cum,
    };
  });
}
```

매수자는 높은 가격을 제시할수록 현재 시장과 가깝다. 그래서 가장 높은 best bid부터 낮은 가격 방향으로 누적한다.

```text
spread
100.0      1.0        1.0  ← best bid
 99.0      2.0        3.0
 98.0      3.0        6.0
화면 아래
```

매도 행과 달리 정렬 결과를 마지막에 뒤집지 않는다.

### 7-5. spread도 현재 props에서 계산한 파생 값이다

```tsx
const quote = deriveQuote(snapshot);
const spread = quote ? quote.spread : 0;
```

`deriveQuote`는 bids와 asks에서 best bid와 best ask를 찾고 차이를 계산한다.

```text
bestBid = bids 중 가장 높은 가격
bestAsk = asks 중 가장 낮은 가격
spread  = bestAsk - bestBid
```

`spread`는 별도 state로 저장하지 않는다. 이미 현재 snapshot만 있으면 계산할 수 있는 값이기 때문이다.

만약 snapshot과 spread를 각각 state로 저장하면 두 state의 동기화를 따로 관리해야 한다.

```text
권장되는 현재 구조
snapshot 하나 저장
→ render 중 spread 계산

불필요하게 복잡한 구조
snapshot 저장
+ spread도 별도 저장
→ 둘이 항상 같은 snapshot 기준인지 관리 필요
```

`askRows`, `bidRows`, `spread`는 모두 현재 props에서 파생되는 값이다. 현재 호가 깊이는 한쪽당 20개 정도이므로 이 계산을 render 중 직접 수행하는 구조가 이해하기 쉽고 충분히 작다. 계산 비용이 실제 성능 문제가 된다는 측정 없이 `useMemo`를 먼저 추가할 필요는 없다.

---

## 8. `map`이 호가 배열을 JSX 행 배열로 바꾼다

매도 호가 렌더링 부분은 다음과 같다.

```tsx
<div className="asks">
  {askRows.map((row) => (
    <div
      key={`ask-${row.price}`}
      className="row ask"
    >
      <span>{row.price.toFixed(2)}</span>
      <span>{row.quantity.toFixed(3)}</span>
      <span>{row.cumulative.toFixed(3)}</span>
    </div>
  ))}
</div>
```

현재 실제 코드에는 클릭 관련 props도 있지만, 위 예시는 이번 학습 범위에 맞게 화면 출력 부분만 남긴 것이다.

### 8-1. `map`은 원소마다 새 값을 만든다

일반 JavaScript에서 `map`은 배열의 각 원소를 콜백에 넣고, 콜백의 반환값으로 새 배열을 만든다.

```tsx
const prices = [100, 101, 102];
const labels = prices.map((price) => `${price} USDT`);
```

```text
[100, 101, 102]
→ map
→ ['100 USDT', '101 USDT', '102 USDT']
```

React 코드에서는 콜백이 문자열 대신 JSX를 반환한다.

```tsx
const elements = askRows.map((row) => (
  <div>{row.price}</div>
));
```

```text
Row 객체 배열
→ map
→ React 요소 배열
```

JSX 안의 `{...}`에는 JavaScript 표현식의 결과를 넣을 수 있으므로 이 React 요소 배열도 자식 목록으로 렌더링할 수 있다.

### 8-2. 소괄호를 사용한 화살표 함수는 JSX를 암시적으로 반환한다

```tsx
askRows.map((row) => (
  <div>...</div>
))
```

`=> (` 다음의 JSX 표현식이 각 호출의 반환값이다. 중괄호를 쓴다면 `return`이 필요하다.

```tsx
askRows.map((row) => {
  return <div>...</div>;
})
```

두 코드는 같은 목적을 수행한다.

### 8-3. `key`는 React가 이전 행과 새 행을 연결하는 단서다

```tsx
key={`ask-${row.price}`}
```

다음 snapshot이 도착하면 새로운 `askRows` 배열과 새로운 React 요소 배열을 만든다. React는 각 요소의 `key`를 사용해 이전 목록의 어느 행과 새 목록의 어느 행이 같은 항목인지 판단한다.

```text
이전 asks
ask-101
ask-102
ask-103

새 asks
ask-101
ask-102
ask-104
```

React는 key를 기준으로 다음처럼 대응시킬 수 있다.

```text
ask-101 → 이전 행과 대응
ask-102 → 이전 행과 대응
ask-103 → 새 목록에서 사라짐
ask-104 → 새로 등장
```

현재 호가 데이터에서는 한쪽 호가 목록 안의 가격이 각 가격 단계를 구별하므로 가격을 key의 기반으로 사용한다. `ask-`와 `bid-` 접두사도 어느 쪽의 가격인지 명확하게 만든다.

`key`에 관해 기억할 점은 다음과 같다.

- 같은 형제 목록 안에서 고유해야 한다.
- 렌더링할 때마다 무작위로 바뀌면 안 된다.
- React가 목록 비교에 사용하는 특별한 정보이며 일반 prop으로 컴포넌트에 전달되지 않는다.
- 실제 DOM에 사용자에게 보이는 속성으로 표시하기 위한 값도 아니다.

### 8-4. `toFixed`는 화면에 표시할 문자열을 만든다

```tsx
row.price.toFixed(2)
row.quantity.toFixed(3)
row.cumulative.toFixed(3)
```

`toFixed`는 숫자의 표시 자릿수를 맞춘 문자열을 반환한다.

```text
65000.1.toFixed(2) → '65000.10'
0.4.toFixed(3)     → '0.400'
```

이것은 React 기능이 아니라 JavaScript 숫자 메서드다. React는 계산된 문자열을 `<span>`의 text content로 화면에 반영한다.

### 8-5. 매수 목록도 같은 원리다

```tsx
<div className="bids">
  {bidRows.map((row) => (
    <div key={`bid-${row.price}`} className="row bid">
      <span>{row.price.toFixed(2)}</span>
      <span>{row.quantity.toFixed(3)}</span>
      <span>{row.cumulative.toFixed(3)}</span>
    </div>
  ))}
</div>
```

매도와 매수는 정렬 방향과 CSS class가 다르지만, 배열을 JSX 목록으로 변환하는 React 원리는 같다.

---

## 9. OrderBook이 반환한 JSX가 실제 호가창 DOM이 된다

`OrderBook`의 전체 화면 구조를 단순화하면 다음과 같다.

```tsx
return (
  <div className="orderbook">
    <div className="header">...</div>
    <div className="asks">...</div>
    <div className="spread">...</div>
    <div className="bids">...</div>
  </div>
);
```

React가 계산하는 요소 트리는 대략 다음과 같다.

```text
div.orderbook
├─ div.header
│  ├─ span "price"
│  ├─ span "quantity"
│  └─ span "cumulative"
├─ div.asks
│  ├─ div.row.ask (ask-...)
│  ├─ div.row.ask (ask-...)
│  └─ ...
├─ div.spread
│  └─ "spread ..."
└─ div.bids
   ├─ div.row.bid (bid-...)
   ├─ div.row.bid (bid-...)
   └─ ...
```

### 9-1. React가 JSX를 계산하고 ReactDOM이 DOM을 반영한다

역할을 구분하면 다음과 같다.

| 주체 | 이 흐름에서 하는 일 |
|---|---|
| EventSource | 서버의 SSE 메시지를 받고 `onmessage` 콜백을 호출한다. |
| React | 최신 snapshot state로 App과 OrderBook을 호출해 새 요소 트리를 계산한다. |
| ReactDOM | 이전 결과와 새 결과를 비교해 필요한 실제 DOM 변경을 적용한다. |
| 브라우저 | DOM과 CSS를 바탕으로 layout과 paint를 수행해 픽셀을 표시한다. |

`OrderBook` 함수가 직접 `document.createElement`나 `appendChild`를 호출하지 않는다. 컴포넌트는 현재 데이터라면 어떤 구조가 필요한지 JSX로 반환한다.

### 9-2. CSS가 세 열과 색상을 만든다

React가 `className`을 실제 DOM의 `class`에 반영하면 브라우저가 `styles.css` 규칙을 적용한다.

```css
.orderbook .header,
.orderbook .row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
}

.orderbook .ask {
  color: #ef4444;
}

.orderbook .bid {
  color: #22c55e;
}
```

```text
React/ReactDOM
→ header, row, ask, bid class를 DOM에 반영

브라우저 CSS 엔진
→ price / quantity / cumulative를 3열로 배치
→ ask는 빨간색, bid는 초록색으로 표시
```

React가 빨간 픽셀이나 grid 열을 직접 그리는 것이 아니다. ReactDOM이 DOM을 만들고, 브라우저가 CSS와 DOM으로 최종 화면을 그린다.

---

## 10. 다음 snapshot이 도착하면 같은 흐름이 반복된다

첫 snapshot으로 `OrderBook`이 mount된 뒤에도 SSE 연결은 계속 살아 있다.

```text
EventSource 연결 유지
→ snapshot 2 도착
→ onmessage(event2)
→ JSON.parse(event2.data)
→ setSnapshot(snapshot2)
→ App re-render
→ OrderBook에 snapshot2 prop 전달
→ askRows·bidRows·spread 다시 계산
→ 새 JSX 목록 계산
→ ReactDOM이 필요한 DOM만 갱신
```

### 10-1. 새 메시지마다 연결을 다시 만드는 것이 아니다

전체 시간 흐름을 연결과 render로 나누면 다음과 같다.

```text
EventSource 연결 수명
setup ───────────────────────────────────────── cleanup
           │              │              │
           ▼              ▼              ▼
       message 1      message 2      message 3
           │              │              │
           ▼              ▼              ▼
       render 1       render 2       render 3
```

하나의 연결에서 여러 message를 받는다. 각 message가 state update를 만들지만, 빈 의존성 배열 덕분에 일반적인 re-render마다 연결을 다시 setup하지 않는다.

### 10-2. 컴포넌트 함수는 다시 실행되지만 페이지 전체가 새로고침되지는 않는다

snapshot이 바뀌면 App과 현재 App이 렌더링하는 관련 자식 컴포넌트가 다시 호출될 수 있다. `OrderBook`도 새 snapshot props로 다시 호출된다.

그러나 이것은 브라우저 페이지 전체 새로고침이 아니다.

```text
페이지 새로고침
→ 기존 JavaScript 실행 환경 종료
→ HTML과 JS를 서버에서 다시 받음
→ React 앱을 처음부터 시작

React re-render
→ 현재 JavaScript 실행 환경 유지
→ 컴포넌트 함수를 다시 호출해 새 요소 트리 계산
→ 필요한 DOM만 commit
```

### 10-3. 새 배열을 만들었다고 모든 DOM을 반드시 제거하는 것은 아니다

`buildAskRows`, `buildBidRows`, `map`은 render마다 새 JavaScript 배열과 React 요소를 만든다. 하지만 ReactDOM이 매번 모든 실제 DOM 행을 무조건 지우고 다시 만드는 것은 아니다.

React는 요소 타입과 `key` 등을 사용해 이전 목록과 새 목록을 비교한다. 같은 가격 key의 행이 유지되면 기존 DOM을 재사용하면서 바뀐 text만 수정할 수 있다. 사라진 가격 행은 제거하고 새 가격 행은 추가한다.

```text
새 JavaScript 객체·배열 생성
≠ 실제 DOM 전체를 반드시 새로 생성
```

render 단계의 계산과 commit 단계의 DOM 변경을 구분해야 한다.

### 10-4. state에는 최신 snapshot 하나만 남는다

`setSnapshot(data)`는 과거 snapshot 목록에 새 항목을 추가하는 코드가 아니다. `snapshot` state 한 자리를 새 객체로 교체한다.

```text
snapshot state

null
→ snapshot1
→ snapshot2
→ snapshot3
```

현재 호가창은 과거 호가 이력을 전부 렌더링하는 화면이 아니라 **가장 최근 한 시점의 호가 상태**를 렌더링한다.

이전 snapshot 객체는 다른 코드가 참조하지 않으면 나중에 JavaScript 가비지 컬렉션의 정리 대상이 될 수 있다. React state에는 최신 snapshot만 연결되어 있다.

---

## 11. Effect cleanup은 SSE 연결을 닫는다

Effect는 setup 함수 끝에서 cleanup 함수를 반환한다.

```tsx
return () => {
  eventSource.close();
};
```

React가 cleanup을 실행하면 그 Effect setup에서 만들었던 `EventSource`의 연결을 닫는다.

```text
Effect setup
→ EventSource 생성

Effect cleanup
→ 같은 EventSource.close()
```

### 11-1. cleanup이 필요한 이유

연결을 만든 컴포넌트가 더 이상 존재하지 않는데 연결이 계속 살아 있으면 불필요한 네트워크 연결과 콜백이 남을 수 있다.

setup과 cleanup은 서로 거울처럼 대응한다.

| setup | cleanup |
|---|---|
| SSE 연결 생성 | SSE 연결 종료 |
| 외부 메시지 구독 시작 | 외부 메시지 구독 중지 |

현재 `useOrderBookStream`은 App이 호출하므로 App이 mount된 동안 연결을 유지한다. 로그인 여부가 바뀌어 공개 화면과 인증 사용자 화면이 전환되어도 App 자체는 계속 같은 위치에 있으므로 SSE Effect도 유지된다.

### 11-2. cleanup이 실행될 수 있는 시점

React의 일반적인 Effect 규칙에서 cleanup은 다음 경우 실행된다.

1. 컴포넌트가 unmount될 때
2. dependency가 바뀌어 Effect를 다시 setup하기 직전
3. 개발 환경의 `StrictMode`가 초기 Effect를 검사하는 추가 setup·cleanup 과정

현재 Effect의 dependency는 빈 배열이므로 같은 mount 기간의 state update 때문에 2번이 발생하지는 않는다. 하지만 cleanup을 작성해야 App이 React 트리에서 제거되는 경우와 StrictMode 검사에도 안전하다.

브라우저 탭 자체를 닫으면 브라우저도 해당 페이지의 네트워크 연결을 정리한다. 다만 정상적인 React 구조에서는 페이지 종료에만 기대지 않고, Effect가 만든 외부 연결을 Effect cleanup에서 명시적으로 정리한다.

### 11-3. 개발 중 연결 로그가 두 번 보일 수 있는 이유

`main.tsx`는 App을 `React.StrictMode`로 감싼다.

```tsx
<React.StrictMode>
  <App />
</React.StrictMode>
```

개발 환경에서는 Effect의 정리 코드가 setup을 제대로 되돌리는지 확인하기 위해 다음 과정이 추가될 수 있다.

```text
setup
→ EventSource 연결

cleanup
→ EventSource.close()

setup
→ 실제 사용할 EventSource 다시 연결
```

그래서 개발 서버에서 SSE 연결 로그가 초기 한 번보다 더 보일 수 있다. 이를 곧바로 운영 환경의 중복 연결 버그라고 판단하면 안 된다. 반대로 cleanup이 없다면 StrictMode가 실제로 연결 누수 문제를 드러낼 수 있다.

---

## 12. 오류와 재연결은 현재 어떻게 처리되는가?

현재 Hook은 `onerror`에서 오류를 콘솔에 기록한다.

```tsx
eventSource.onerror = (err) => {
  console.error('SSE error', err);
};
```

### 12-1. `onerror`는 React 오류 UI state가 아니다

이 코드는 `console.error`만 호출한다. 따라서 SSE 오류가 발생해도 현재 사용자 화면에 별도의 오류 문구를 렌더링하지 않는다.

```text
SSE 오류
→ onerror 실행
→ 개발자 도구 console에 기록
→ React error state 변경 없음
→ 오류 전용 UI도 없음
```

오류를 화면에 보여 주고 싶다면 Hook이 별도의 connection state나 error state를 관리하고 App에 반환하도록 확장할 수 있다. 하지만 현재 문서는 실제 구현의 snapshot 흐름만 설명한다.

### 12-2. 일시적으로 연결이 끊기면 EventSource가 재연결을 시도한다

`EventSource`는 연결이 끊겼을 때 기본적으로 재연결을 시도한다. 그래서 현재 `onerror`는 직접 새 `EventSource`를 만들지 않는다.

```text
연결 오류
→ onerror 실행
→ EventSource가 재연결 시도
```

직접 `eventSource.close()`를 호출하면 연결을 종료하고 자동 재연결도 멈춘다. 그래서 React cleanup에서 `close()`를 호출하는 것이 중요하다.

### 12-3. 잘못된 JSON은 현재 별도로 처리하지 않는다

```tsx
JSON.parse(event.data)
```

서버가 올바르지 않은 JSON을 보내면 `JSON.parse`가 예외를 던질 수 있다. 현재 `onmessage`에는 `try/catch`가 없으므로 해당 메시지 처리에서 `setSnapshot`까지 도달하지 못한다.

현재 백엔드는 `OrderBookSnapshot`을 Spring/Jackson으로 직렬화하므로 정상 흐름에서는 올바른 JSON이 온다는 계약에 의존한다.

---

## 13. LoginForm 흐름과 비교한다

두 예제의 공통 React 원리는 같다.

```text
어떤 사건 발생
→ 이벤트 콜백 실행
→ state setter 호출
→ 컴포넌트 re-render
→ 현재 state로 새 JSX 계산
→ ReactDOM이 DOM 갱신
```

사건의 출처와 state의 역할은 다르다.

| 구분 | LoginForm | 실시간 OrderBook |
|---|---|---|
| state 변경의 시작 | 사용자의 입력·제출 | 서버가 보낸 SSE message |
| 브라우저 이벤트 | `change`, `submit` | `message`, `error` |
| 콜백 | `onChange`, `handleSubmit` | `eventSource.onmessage` |
| 주요 state | email, password, error, submitting | 최신 snapshot |
| Effect의 주요 역할 | 기존 로그인 세션 확인 | 실시간 연결 setup·cleanup |
| 렌더링 형태 | 입력과 조건부 문구 | 배열을 호가 행 목록으로 변환 |
| 데이터 수명 | LoginForm이 unmount되면 지역 state 폐기 | App이 mount된 동안 최신 snapshot 유지 |

호가창을 통해 새로 강조되는 React 개념은 다음과 같다.

- 외부 시스템과의 연결을 `useEffect`로 동기화한다.
- setup에서 연 연결은 cleanup에서 닫는다.
- 커스텀 Hook이 연결 코드와 state를 한 역할로 묶을 수 있다.
- 부모가 state를 소유하고 자식에게 props로 내려보낼 수 있다.
- 배열은 `map`으로 React 요소 목록으로 바꿀 수 있다.
- 목록의 `key`는 이전 항목과 새 항목을 대응시키는 데 사용된다.
- props로 받은 배열을 직접 변경하지 않고 복사한 뒤 정렬한다.
- 현재 props에서 계산할 수 있는 값은 불필요하게 별도 state로 만들지 않는다.

---

## 14. 전체 state와 컴포넌트 타임라인

| 단계 | App에 연결된 snapshot state | Effect / EventSource | 조건부 UI | OrderBook |
|---|---|---|---|---|
| App 최초 render | `null` | 아직 setup 전 | 수신 대기 문구 | 없음 |
| 첫 commit 뒤 | `null` | Effect setup, 연결 시작 | 수신 대기 문구 | 없음 |
| 첫 message 수신 | `setSnapshot(snapshot1)` 요청 | 연결 유지 | 다음 render 준비 | 없음 |
| 첫 갱신 commit | `snapshot1` | 연결 유지 | 호가창 | mount |
| 다음 message 수신 | `setSnapshot(snapshot2)` 요청 | 같은 연결 유지 | 호가창 | 새 props로 re-render |
| 계속 수신 | 최신 snapshot으로 교체 | 같은 연결 유지 | 호가창 계속 갱신 | 반복 re-render |
| App unmount | state 폐기 | cleanup에서 `close()` | 제거 | unmount |

## 15. 데이터와 코드 실행의 방향

```text
[외부 데이터]
Spring SSE
→ EventSource
→ onmessage(event)
→ JSON.parse(event.data)
→ setSnapshot(data)

[React state와 render]
App의 snapshot state 변경
→ App re-render
→ 조건부 렌더링
→ OrderBook에 snapshot prop 전달

[화면 계산]
snapshot.asks
→ buildAskRows
→ askRows.map
→ 매도 행 JSX

snapshot
→ deriveQuote
→ spread JSX

snapshot.bids
→ buildBidRows
→ bidRows.map
→ 매수 행 JSX

[브라우저 화면]
React 요소 트리
→ ReactDOM commit
→ 실제 DOM
→ CSS layout·paint
```

## 16. 자주 헷갈리는 표현 정리

| 표현 | 실제 의미 |
|---|---|
| React가 SSE를 받는다 | 정확히는 브라우저 `EventSource`가 받고, 콜백이 React setter를 호출한다. |
| `useEffect`가 매 render마다 연결한다 | Hook 호출은 render마다 지나가지만, 빈 dependency가 유지되므로 setup은 일반 update마다 다시 실행되지 않는다. |
| `setSnapshot`이 화면을 직접 고친다 | setter는 state update와 render를 요청한다. 실제 DOM 반영은 JSX 계산 뒤 ReactDOM이 한다. |
| `OrderBook`이 데이터를 가져온다 | 데이터 연결과 state는 `useOrderBookStream`/App이 맡고, `OrderBook`은 props로 받는다. |
| `map`이 DOM을 만든다 | `map`은 React 요소 배열을 만든다. 실제 DOM은 commit에서 ReactDOM이 반영한다. |
| 새 snapshot이면 DOM 전체를 다시 만든다 | 새 요소 트리는 다시 계산하지만 ReactDOM은 이전 결과와 비교해 필요한 변경을 반영한다. |
| `key`가 화면에 표시된다 | key는 React의 목록 비교 정보이며 일반적인 화면 데이터가 아니다. |
| `: OrderBookSnapshot`이 JSON을 검사한다 | TypeScript의 정적 타입 표기이며 런타임 schema 검증은 아니다. |

## 17. 스스로 확인할 질문

1. 최초 render에서 `snapshot`이 `null`인 이유는 무엇인가?
2. snapshot이 없을 때 `OrderBook` 대신 어떤 JSX가 렌더링되는가?
3. `EventSource`는 React 기능인가, 브라우저 기능인가?
4. SSE 연결을 App 함수 본문에서 직접 만들면 어떤 문제가 생길 수 있는가?
5. `useEffect(..., [])`의 setup이 snapshot re-render마다 다시 실행되지 않는 이유는 무엇인가?
6. `eventSource.onmessage = 함수`를 실행했을 때 함수 본문도 즉시 실행되는가?
7. `event.data`에 `JSON.parse`가 필요한 이유는 무엇인가?
8. `setSnapshot(data)`가 직접 변경하는 것은 DOM인가, React state인가?
9. 커스텀 Hook 파일에 선언된 snapshot state가 App에 연결된 state인 이유는 무엇인가?
10. App은 `null`일 수 있는 snapshot을 어떻게 `OrderBookSnapshot`만 받는 `OrderBook`에 안전하게 전달하는가?
11. `buildAskRows`가 `asks.sort(...)` 대신 `[...asks].sort(...)`를 사용하는 이유는 무엇인가?
12. `askRows.map(...)`의 결과는 DOM 배열인가, React 요소 배열인가?
13. 호가 행에 `key`가 필요한 이유는 무엇인가?
14. snapshot이 바뀔 때 `spread`를 별도 state로 저장하지 않아도 되는 이유는 무엇인가?
15. App이 재렌더링될 때도 기존 EventSource 연결이 유지되는 이유는 무엇인가?
16. cleanup의 `eventSource.close()`는 무엇을 정리하는가?
17. 개발 환경의 StrictMode에서 초기 SSE 연결이 추가로 생성되고 닫힐 수 있는 이유는 무엇인가?

---

## 마지막 핵심

실시간 호가창 흐름을 한 문장으로 줄이면 다음과 같다.

> Effect가 브라우저의 EventSource를 SSE 서버와 연결하고, 메시지 콜백이 최신 snapshot을 state에 저장하면, React가 그 state를 props로 받은 OrderBook을 다시 렌더링하고 ReactDOM이 호가 목록의 실제 DOM을 갱신한다.

조금 더 짧게 줄이면 다음과 같다.

```text
SSE message
→ state
→ props
→ JSX 목록
→ DOM 갱신
```

이 예제의 중심은 서버 메시지를 받을 때마다 DOM을 직접 찾아 수정하는 것이 아니다. **최신 snapshot을 state로 만들고, 현재 snapshot이라면 호가창이 어떤 모습이어야 하는지를 JSX로 다시 선언하는 것**이다.

## 참고 자료

- React 공식 문서: [useEffect](https://react.dev/reference/react/useEffect)
- React 공식 문서: [Rendering Lists](https://react.dev/learn/rendering-lists)
- MDN: [EventSource](https://developer.mozilla.org/en-US/docs/Web/API/EventSource)
- MDN: [Using server-sent events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
