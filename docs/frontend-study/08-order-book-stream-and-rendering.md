# 실시간 호가창: SSE 수신부터 React 렌더링까지

이 문서는 다음 코드 흐름만 따라간다.

```text
서버가 호가 snapshot 전송
→ 브라우저 EventSource가 수신
→ setSnapshot(data)
→ App re-render
→ OrderBook에 props 전달
→ 호가 행 계산과 렌더링
```

관련 파일은 세 개가 중심이다.

| 파일 | 역할 |
|---|---|
| `frontend/src/market/hooks/useOrderBookStream.ts` | SSE 연결을 만들고 최신 snapshot을 state에 저장한다. |
| `frontend/src/App.tsx` | snapshot 유무에 따라 대기 문구나 `OrderBook`을 렌더링한다. |
| `frontend/src/market/components/OrderBook.tsx` | bids·asks를 화면용 행으로 계산해 출력한다. |

---

## 1. SSE와 `EventSource`부터 이해한다

### 1-1. SSE는 서버가 계속 보내는 HTTP 응답이다

SSE(Server-Sent Events)는 서버가 브라우저에 이벤트를 계속 보내는 통신 방식이다.

일반적인 HTTP 요청은 응답 하나를 받고 끝난다.

```text
브라우저 ── GET 요청 ──→ 서버
브라우저 ←─ 응답 하나 ── 서버
                         연결 종료
```

SSE도 HTTP `GET`으로 시작하지만 서버가 응답을 끝내지 않는다. 연결을 열어 둔 채 데이터가 생길 때마다 이벤트를 추가로 보낸다.

```text
브라우저 ── GET /depth/stream ──→ 서버
브라우저 ←─ snapshot 1 ───────── 서버
브라우저 ←─ snapshot 2 ───────── 서버
브라우저 ←─ snapshot 3 ───────── 서버
                  ...
```

서버 응답의 `Content-Type`은 다음과 같다.

```http
Content-Type: text/event-stream
```

`text/event-stream`은 브라우저에 “이 응답 body는 SSE 형식”이라고 알리는 HTTP 헤더다. 이 헤더 자체가 연결을 유지하는 것은 아니며, 응답을 계속 열어 두는 이유는 뒤에서 볼 서버 `Flux`가 완료되지 않기 때문이다.

`EventSource`는 이 헤더가 있어야 뒤의 `data: ...`와 빈 줄을 SSE 이벤트로 해석해 `onmessage`를 호출한다.

이 프로젝트의 Spring 컨트롤러도 이 형식으로 응답한다.

```java
@GetMapping(
    path = "/api/binance-futures/btcusdt/depth/stream",
    produces = MediaType.TEXT_EVENT_STREAM_VALUE
)
public Flux<ServerSentEvent<OrderBookSnapshot>> stream() {
    return latestStore.stream()
            .map(snapshot -> ServerSentEvent.builder(snapshot).build());
}
```

`stream()` 메서드가 snapshot마다 다시 호출되는 것은 아니다. 브라우저가 연결할 때 반환한 `Flux`를 Spring WebFlux가 구독하고, 이후 새 snapshot이 발행될 때마다 같은 HTTP 응답에 SSE 이벤트를 쓴다.

### 1-2. SSE 이벤트는 어떤 모습인가?

서버가 보내는 실제 텍스트는 개념적으로 다음과 같다.

```text
data: {"symbol":"BTCUSDT","eventTime":1720000000000,"bids":[...],"asks":[...]}

data: {"symbol":"BTCUSDT","eventTime":1720000000100,"bids":[...],"asks":[...]}

```

핵심 규칙은 간단하다.

- `data:` 뒤가 이벤트의 데이터다.
- 빈 줄 하나가 이벤트 한 개의 끝을 나타낸다.
- 연결은 닫히지 않으므로 다음 이벤트가 같은 응답을 통해 계속 온다.

SSE 자체는 JSON 전용 형식이 아니다. `data:`에는 문자열이 들어가며, 이 프로젝트가 호가 객체를 전달하기 위해 그 문자열을 JSON으로 정한 것이다. 그래서 브라우저에서 `JSON.parse(event.data)`가 필요하다.

SSE에는 `data` 외에도 다음 필드가 있다.

```text
event: orderbook
id: 152
retry: 3000
data: {"symbol":"BTCUSDT", ...}

```

| 필드 | 의미 |
|---|---|
| `data` | 브라우저가 받을 실제 문자열 데이터 |
| `event` | 이벤트 이름. 생략하면 기본 `message` 이벤트가 된다. |
| `id` | 마지막 이벤트 식별자. 재연결할 때 이어받는 데 사용할 수 있다. |
| `retry` | 연결이 끊긴 뒤 재연결하기까지 기다릴 시간을 밀리초로 지정한다. |

현재 백엔드는 `data`만 보내므로 프런트엔드는 `onmessage`로 받는다.

```tsx
eventSource.onmessage = (event) => {
  // event.data에 data: 뒤의 문자열이 들어온다.
};
```

서버가 `event: orderbook`처럼 이름을 붙인다면 `onmessage` 대신 이름으로 구독해야 한다.

```tsx
eventSource.addEventListener('orderbook', (event) => {
  console.log(event.data);
});
```

### 1-3. `EventSource`는 무엇인가?

`EventSource`는 React 기능이나 설치한 라이브러리가 아니다. 브라우저에 내장된 SSE 클라이언트 Web API다.

```tsx
const eventSource = new EventSource(
  '/api/binance-futures/btcusdt/depth/stream'
);
```

이 한 줄을 실행하면 브라우저가 다음 작업을 맡는다.

```text
EventSource 객체 생성
→ URL로 HTTP GET 요청
→ text/event-stream 응답 확인
→ 연결 유지
→ SSE 형식의 이벤트 구분
→ 이벤트가 올 때 등록된 콜백 호출
→ 연결이 일시적으로 끊기면 재연결 시도
```

즉, 개발자가 응답 스트림의 줄을 직접 읽어 `data:`와 빈 줄을 파싱하지 않아도 된다. `EventSource`가 SSE 형식을 해석하고 `MessageEvent`로 바꿔 준다.

### 1-4. `EventSource`에서 주로 쓰는 API

```tsx
const eventSource = new EventSource(url);

eventSource.onopen = () => {
  console.log('연결됨');
};

eventSource.onmessage = (event) => {
  console.log(event.data);
};

eventSource.onerror = (error) => {
  console.error('SSE 오류', error);
};

eventSource.close();
```

| API | 실행 시점 또는 역할 |
|---|---|
| `new EventSource(url)` | 객체 생성과 동시에 SSE 연결을 시작한다. |
| `onopen` | 연결이 열렸을 때 실행된다. |
| `onmessage` | 이름 없는 기본 메시지를 받을 때마다 실행된다. |
| `onerror` | 연결 문제 등이 발생했을 때 실행된다. 보통 이후 자동 재연결을 시도한다. |
| `close()` | 연결을 직접 닫고 자동 재연결도 중지한다. |

현재 상태는 `readyState`로 확인할 수 있다.

| 값 | 상수 | 의미 |
|---:|---|---|
| `0` | `EventSource.CONNECTING` | 최초 연결 중이거나 재연결 중 |
| `1` | `EventSource.OPEN` | 연결이 열려 이벤트를 받을 수 있음 |
| `2` | `EventSource.CLOSED` | 연결이 닫혀 더 이상 재연결하지 않음 |

예를 들면 다음처럼 볼 수 있다.

```tsx
console.log(eventSource.readyState === EventSource.OPEN);
```

### 1-5. 콜백 등록과 실행 시점은 다르다

```tsx
eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  setSnapshot(data);
};
```

위 코드는 함수를 지금 실행하는 코드가 아니라 `onmessage` 자리에 함수를 등록하는 코드다.

```text
Effect 실행 시점
→ onmessage에 함수 등록

나중에 서버 메시지가 도착
→ 브라우저가 등록된 함수 호출
→ event 인자 전달
→ JSON.parse와 setSnapshot 실행
```

`event`는 브라우저가 만든 `MessageEvent` 객체이고, `event.data`는 서버의 `data:` 필드에서 꺼낸 문자열이다.

```tsx
event.data
// '{"symbol":"BTCUSDT","eventTime":1720000000000,...}'

const data = JSON.parse(event.data);
// { symbol: 'BTCUSDT', eventTime: 1720000000000, ... }
```

### 1-6. 자동 재연결은 어떻게 동작하는가?

SSE 연결이 일시적으로 끊기면 `EventSource`는 보통 다음 순서로 동작한다.

```text
연결 끊김
→ error 이벤트
→ readyState가 CONNECTING
→ 일정 시간 뒤 같은 URL로 다시 연결
→ 성공하면 open 이벤트
```

그래서 현재 코드는 `onerror` 안에서 새 `EventSource`를 직접 만들지 않는다.

```tsx
eventSource.onerror = (err) => {
  console.error('SSE error', err);
};
```

여기서 다시 `new EventSource(...)`를 실행하면 브라우저의 자동 재연결과 겹쳐 연결이 중복될 수 있다.

서버가 `id:`를 보냈다면 브라우저는 재연결 요청에 마지막 이벤트 ID를 전달할 수 있고, 서버는 그다음 이벤트부터 재전송할 수 있다. 현재 프로젝트는 별도의 이벤트 `id`와 재전송 이력을 관리하지 않는다. 대신 백엔드의 `replay(1)` 스트림은 **이미 발행된 snapshot이 하나 이상 있으면** 새 구독자에게 최신 한 건을 보낸다. 따라서 그 상태에서 재연결하면 호가창은 최신 상태를 다시 받을 수 있다.

`close()`를 직접 호출하면 자동 재연결도 끝난다.

```tsx
eventSource.close();
// readyState === EventSource.CLOSED
```

### 1-7. `fetch`, polling, WebSocket과 무엇이 다른가?

| 방식 | 연결과 데이터 방향 | 이 호가창에서의 의미 |
|---|---|---|
| 단발 `fetch` | 요청 1회 → 응답 1회 | 최신 호가 한 번만 조회할 때 적합 |
| polling | 일정 시간마다 `fetch` 반복 | 새 데이터가 없어도 계속 요청해야 함 |
| SSE + `EventSource` | HTTP 연결 1개, 서버 → 브라우저 | 서버가 새 snapshot이 생길 때 계속 전송 |
| WebSocket | 연결 1개, 양방향 메시지 | 양쪽이 자주 메시지를 주고받을 때 적합 |

이 프로젝트에는 두 종류의 실시간 연결이 있다.

```text
Binance ── WebSocket ──→ Spring 백엔드
Spring  ── SSE ─────────→ 브라우저 EventSource
```

브라우저 호가창은 서버에 메시지를 계속 보낼 필요 없이 받기만 하므로 SSE가 단순하다. 주문처럼 브라우저가 서버로 보내는 요청은 별도의 HTTP API를 사용한다.

### 1-8. 네이티브 `EventSource`의 제약

`EventSource`는 단순한 SSE 수신에 편리한 대신 요청을 세밀하게 구성하는 API는 아니다.

- 요청 방식은 `GET`이다.
- 요청 body를 넣을 수 없다.
- 생성자에서 임의의 HTTP header를 지정할 수 없다.
- 다른 출처에 연결하면 서버의 CORS 허용이 필요하다.
- 쿠키가 필요한 교차 출처 연결은 `{ withCredentials: true }` 옵션을 사용한다.

```tsx
const eventSource = new EventSource('https://api.example.com/stream', {
  withCredentials: true,
});
```

현재 URL은 상대 경로다.

```tsx
'/api/binance-futures/btcusdt/depth/stream'
```

로컬 개발에서는 브라우저가 같은 출처의 Vite 서버로 요청하고, Vite proxy가 Spring Boot로 전달한다.

```text
브라우저 → Vite /api/... → Spring Boot localhost:8080
```

운영에서는 Spring Boot가 프런트 정적 파일과 `/api`를 같은 출처에서 제공한다. 따라서 현재 코드는 별도의 CORS나 인증 header 설정 없이 연결할 수 있다.

---

## 2. Hook이 `EventSource`와 React state를 연결한다

실제 코드는 다음과 같다.

```tsx
export function useOrderBookStream(): OrderBookSnapshot | null {
  const [snapshot, setSnapshot] =
    useState<OrderBookSnapshot | null>(null);

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

  return snapshot;
}
```

### 2-1. 처음에는 `snapshot`이 `null`이다

```tsx
const [snapshot, setSnapshot] =
  useState<OrderBookSnapshot | null>(null);
```

첫 render에서는 아직 Effect도, SSE 메시지도 실행되지 않았다. 따라서 `null`은 오류가 아니라 **첫 snapshot을 기다리는 상태**다.

### 2-2. 연결을 `useEffect` 안에서 만드는 이유

`EventSource`는 React 바깥의 네트워크 연결이다. render 본문에서 만들면 App이 다시 렌더링될 때마다 연결이 추가될 수 있다.

```tsx
// 잘못된 위치: render할 때마다 실행될 수 있다.
const eventSource = new EventSource('/api/...');
```

Effect를 사용하면 React 컴포넌트의 수명에 맞춰 연결을 열고 닫을 수 있다.

```text
첫 render와 commit
→ Effect setup
→ EventSource 연결 생성

App unmount
→ Effect cleanup
→ EventSource.close()
```

빈 dependency 배열 `[]`이므로 snapshot 변경으로 다시 렌더링되어도 같은 mount 동안 연결을 새로 만들지 않는다.

개발 환경의 `StrictMode`에서는 cleanup 검사를 위해 초기에 `setup → cleanup → setup`이 한 번 더 보일 수 있다.

### 2-3. 메시지를 객체로 바꿔 state에 저장한다

```tsx
eventSource.onmessage = (event) => {
  const data: OrderBookSnapshot = JSON.parse(event.data);
  setSnapshot(data);
};
```

실행 순서는 다음과 같다.

```text
SSE의 data 문자열
→ JSON.parse
→ OrderBookSnapshot 모양의 JavaScript 객체
→ setSnapshot(data)
→ React에 state 갱신 요청
```

`const data: OrderBookSnapshot`은 TypeScript가 이후 코드를 검사할 때 사용할 타입 표기다. 서버 JSON의 실제 구조를 런타임에 검증해 주지는 않는다.

---

## 3. state가 바뀌면 App이 `OrderBook`에 전달한다

App은 Hook이 반환한 최신 snapshot을 받는다.

```tsx
export default function App() {
  const snapshot = useOrderBookStream();

  // ...
}
```

실제 호가창까지의 컴포넌트 경로는 로그인 여부에 따라 다르다.

```text
비로그인: App → OrderBook
로그인:   App → AuthenticatedTradingLayout → OrderBook
```

두 경로 모두 `snapshot`이 처음에는 `null`이므로 대기 문구를 렌더링한다. 아래 코드는 비로그인 화면의 직접 경로다.

```tsx
{snapshot ? (
  <OrderBook
    snapshot={snapshot}
    onPriceClick={handlePriceClick}
  />
) : (
  <p className="empty">호가 데이터 수신 대기 중...</p>
)}
```

첫 메시지에서 `setSnapshot(data)`가 실행되면 App이 다시 렌더링된다. 이제 `snapshot`은 객체이므로 `OrderBook`이 선택되고, 그 객체가 props로 내려간다.

```text
snapshot = null
→ 대기 문구

snapshot = OrderBookSnapshot 객체
→ <OrderBook snapshot={snapshot} />
```

새 메시지가 올 때마다 state 한 자리를 최신 객체로 교체한다. 과거 snapshot 배열을 쌓는 구조가 아니다.

```text
null → snapshot1 → snapshot2 → snapshot3
```

---

## 4. `OrderBook`이 화면용 행을 계산한다

서버 데이터 타입은 가격과 수량만 가진다.

```tsx
export type OrderBookLevel = {
  price: number;
  quantity: number;
};
```

화면에는 누적 수량도 필요하므로 `Row`를 따로 만든다.

```tsx
type Row = {
  price: number;
  quantity: number;
  cumulative: number;
};
```

### 4-1. 매도 호가

```tsx
function buildAskRows(asks: OrderBookLevel[]): Row[] {
  const sorted = [...asks].sort((a, b) => a.price - b.price);
  let cum = 0;

  const rows = sorted.map((level) => {
    cum += level.quantity;
    return {
      price: level.price,
      quantity: level.quantity,
      cumulative: cum,
    };
  });

  return rows.reverse();
}
```

```text
asks 복사
→ 낮은 가격부터 정렬
→ best ask부터 수량 누적
→ 화면에서는 높은 가격이 위로 오도록 reverse
```

`sort()`는 원본 배열을 변경하므로 `[...asks]`로 props 배열을 복사한 뒤 정렬한다.

### 4-2. 매수 호가

```tsx
function buildBidRows(bids: OrderBookLevel[]): Row[] {
  const sorted = [...bids].sort((a, b) => b.price - a.price);
  let cum = 0;

  return sorted.map((level) => {
    cum += level.quantity;
    return {
      price: level.price,
      quantity: level.quantity,
      cumulative: cum,
    };
  });
}
```

매수는 가장 높은 가격인 best bid부터 내림차순으로 정렬하고 그대로 누적한다.

### 4-3. spread

```tsx
const quote = deriveQuote(snapshot);
const spread = quote ? quote.spread : 0;
```

spread는 현재 snapshot만 있으면 계산할 수 있는 파생 값이다. 별도 state로 저장하지 않고 render 중 계산한다.

---

## 5. `map`으로 호가 행을 렌더링한다

```tsx
<div className="asks">
  {askRows.map((row) => (
    <div
      key={`ask-${row.price}`}
      className="row ask"
      onClick={() => onPriceClick?.(row.price)}
    >
      <span>{row.price.toFixed(2)}</span>
      <span>{row.quantity.toFixed(3)}</span>
      <span>{row.cumulative.toFixed(3)}</span>
    </div>
  ))}
</div>
```

`map`은 `Row[]`를 React 요소 배열로 바꾼다. `key`는 다음 snapshot을 렌더링할 때 React가 같은 가격 행을 대응시키는 기준이다.

매수 목록도 같은 구조다.

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

새 snapshot마다 컴포넌트는 새 행 배열과 JSX를 계산한다. ReactDOM은 이전 결과와 비교해 바뀐 텍스트, 추가된 행, 사라진 행 등 필요한 실제 DOM만 반영한다. 페이지 전체를 새로고침하는 것이 아니다.

---

## 6. 연결은 유지되고 최신 화면만 반복 갱신된다

전체 실행 흐름은 다음과 같다.

```text
App 첫 render: snapshot = null
→ 대기 문구 commit
→ Effect가 EventSource 연결 생성
→ 서버가 snapshot 1 전송
→ onmessage
→ JSON.parse
→ setSnapshot(snapshot1)
→ App re-render
→ OrderBook에 snapshot1 전달
→ 행 계산과 DOM 갱신
→ 서버가 snapshot 2 전송
→ 같은 EventSource의 onmessage
→ setSnapshot(snapshot2)
→ 같은 과정 반복
```

중요한 점은 **메시지마다 `EventSource`를 새로 만드는 것이 아니라 하나의 연결에서 여러 메시지를 받는다는 것**이다.

```text
EventSource setup ───────────────────────── cleanup
                       │       │       │
                    message1 message2 message3
                       │       │       │
                    render1  render2  render3
```

App이 사라지면 Effect cleanup이 연결을 닫는다.

```tsx
return () => {
  eventSource.close();
};
```

이를 빼면 더 이상 화면에서 사용하지 않는 네트워크 연결과 콜백이 남을 수 있다.

현재 `onerror`는 콘솔 기록만 한다. 오류 문구를 화면에 보여 주려면 Hook이 `error`나 `connectionState`도 React state로 관리해 반환하도록 확장해야 한다.

---

## 핵심 정리

```text
Spring SSE
→ 브라우저 EventSource
→ onmessage(event)
→ JSON.parse(event.data)
→ setSnapshot(data)
→ App re-render
→ OrderBook props
→ bids·asks 계산
→ map으로 JSX 생성
→ ReactDOM 갱신
```

- SSE는 하나의 HTTP 응답을 열어 두고 서버가 이벤트를 계속 보내는 방식이다.
- `EventSource`는 SSE 연결, 형식 해석, 이벤트 발생, 자동 재연결을 담당하는 브라우저 API다.
- React의 `useEffect`는 `EventSource` 연결의 생성과 정리를 컴포넌트 수명에 맞춘다.
- `onmessage`는 문자열 JSON을 객체로 바꾸고 최신 snapshot state를 교체한다.
- `OrderBook`은 snapshot props에서 화면용 값을 계산하고 JSX 목록을 반환한다.

## 참고 자료

- React 공식 문서: [useEffect](https://react.dev/reference/react/useEffect)
- React 공식 문서: [Rendering Lists](https://react.dev/learn/rendering-lists)
- MDN: [EventSource](https://developer.mozilla.org/en-US/docs/Web/API/EventSource)
- MDN: [Using server-sent events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
