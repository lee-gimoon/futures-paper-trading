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

## 1. 이 프로젝트에서 snapshot이 `EventSource.onmessage`까지 도착하는 흐름

### 일반 HTTP 응답과 SSE 응답의 차이

SSE(Server-Sent Events)는 서버가 브라우저에 이벤트를 계속 보내는 통신 방식이다.

일반적인 HTTP 요청은 응답 body 하나를 완료한다.

```text
브라우저 ── GET 요청 ──→ 서버
브라우저 ←─ 응답 하나 ── 서버
                         응답 완료
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

### Binance 호가가 Store의 `Flux`로 들어온다

이 프로젝트에서 SSE로 보낼 값은 컨트롤러가 새로 만드는 것이 아니다. Binance WebSocket 메시지를 파싱해 만든 `OrderBookSnapshot`을 `LatestOrderBookSnapshotStore`에 넣는 것에서 시작한다.

```java
// BinanceFuturesRawDepthStreamer
OrderBookSnapshot snapshot = snapshotParser.parse(message);
latestStore.update(snapshot);
```

`update(snapshot)`은 같은 snapshot을 두 곳에 전달한다.

```java
// LatestOrderBookSnapshotStore
private final Sinks.Many<OrderBookSnapshot> sink =
        Sinks.many().replay().limit(1);

public void update(OrderBookSnapshot snapshot) {
    latest.set(snapshot);         // /depth/latest 같은 단발 조회용 최신 값
    sink.tryEmitNext(snapshot);   // 현재 SSE 구독자에게 새 값 발행
}

public Flux<OrderBookSnapshot> stream() {
    return sink.asFlux();
}
```

`sink.tryEmitNext(snapshot)`이 실행되면 `sink.asFlux()`를 구독 중인 모든 SSE 연결에 snapshot 하나가 흘러간다. `replay().limit(1)`으로 만든 Sink라서 이미 발행된 snapshot이 있으면 새 SSE 구독자는 최신 한 건도 바로 받는다.

```text
Binance WebSocket 메시지
→ snapshotParser.parse(message)
→ latestStore.update(snapshot)
→ sink.tryEmitNext(snapshot)
→ latestStore.stream()의 Flux에서 snapshot 하나 emit
```

### 브라우저가 `EventSource`로 이 `Flux`를 구독한다

`EventSource`는 영어 그대로 **“이벤트가 나오는 출처”**라는 뜻이다. 이 프로젝트에서 실제 이벤트 출처는 snapshot을 계속 보내는 Spring Boot SSE endpoint이고, `EventSource`는 React 기능이 아니라 그 출처에 연결하는 브라우저 내장 SSE 클라이언트다. Hook의 Effect가 다음 객체를 한 번 만든다.

`EventSource`는 브라우저가 제공하는 SSE 연결용 생성자다. `new EventSource(url)`은 “이 URL은 이벤트가 계속 나오는 출처다. 여기에 연결해서 이벤트를 받아 줘.”라는 뜻으로, 서버에 연결해 SSE 이벤트를 계속 받아 줄 JavaScript 객체를 만든다.

```tsx
const eventSource = new EventSource(
  '/api/binance-futures/btcusdt/depth/stream'
);
```

생성 즉시 브라우저가 이 URL로 HTTP `GET` 요청을 보낸다. 개발 환경에서는 상대 경로 `/api/...` 요청이 Vite proxy를 거쳐 Spring Boot로 전달된다.

```text
브라우저 EventSource
→ GET /api/binance-futures/btcusdt/depth/stream
→ Vite proxy
→ Spring WebFlux의 BinanceFuturesDepthController.stream()
→ latestStore.stream() 구독
```

`proxy`는 영어로 “대리인, 대신 처리하는 중개자”라는 뜻이다. 여기서 Vite proxy는 브라우저가 Vite 개발 서버로 보낸 `/api/...` 요청을 Spring Boot의 `localhost:8080`으로 대신 전달하고, Spring Boot의 응답도 다시 브라우저에 전달한다.

`stream()` 메서드는 브라우저가 연결할 때 한 번 실행되어 `Flux` 파이프라인을 반환한다. 이후 snapshot마다 이 메서드를 다시 호출하는 것이 아니라, 이미 구독한 `Flux`에서 값이 나올 때 아래 `map`만 한 번씩 실행된다.

```java
return latestStore.stream()
        .map(snap -> ServerSentEvent.builder(snap).build());
```

`EventSource` 하나와 서버의 SSE 구독 하나가 대응한다. snapshot이 여러 번 와도 같은 `EventSource`와 같은 HTTP 응답을 계속 사용한다.

### `ServerSentEvent`가 실제 SSE 텍스트가 되고 `onmessage`가 실행된다

`map`이 만드는 `ServerSentEvent<OrderBookSnapshot>`은 서버 JVM 안의 Java 객체다. Spring WebFlux의 SSE writer가 이 객체 안의 snapshot을 Jackson으로 JSON 문자열로 바꾸고, 다음 SSE 텍스트를 같은 HTTP 응답에 쓴다.

```text
data: {"symbol":"BTCUSDT","eventTime":1720000000000,"bids":[...],"asks":[...]}

```

`data: JSON\n\n`에서 첫 줄바꿈은 `data:` 줄의 끝이고, 두 번째 줄바꿈은 빈 줄이다. 이 빈 줄이 SSE 이벤트 한 건의 끝을 나타낸다. 네트워크 바이트가 꼭 한 줄씩 도착하는 것은 아니며, `EventSource`가 받은 내용을 내부 버퍼에 모아 빈 줄을 발견했을 때 이벤트 한 건으로 완성한다.

현재 백엔드는 별도의 `event:`, `id:`, `retry:`를 설정하지 않고 snapshot만 보낸다. 그래서 브라우저는 기본 메시지 이벤트로 처리하고, Hook은 `onmessage`에 콜백을 등록한다.

```tsx
eventSource.onmessage = (event) => {
  const data: OrderBookSnapshot = JSON.parse(event.data);
  setSnapshot(data);
};
```

위 코드는 Effect가 실행될 때 콜백을 **등록**한다. 나중에 Spring이 보낸 SSE 텍스트에서 빈 줄을 발견하면 브라우저가 이 함수를 호출한다.

```text
ServerSentEvent Java 객체
→ Spring WebFlux: data: {JSON}\n\n
→ EventSource: 빈 줄을 이벤트 끝으로 해석
→ event.data: '{JSON}' 문자열
→ JSON.parse(event.data)
→ setSnapshot(data)
```

`event.data`는 아직 JSON 문자열이고, `JSON.parse` 뒤의 `data`가 `OrderBookSnapshot` JavaScript 객체다.

### 현재 Hook의 오류·재연결·종료 처리

현재 Hook은 오류를 콘솔에 남기기만 한다.

`EventSource` 연결이 일시적으로 끊기면 브라우저가 같은 URL로 자동 재연결을 시도한다. 그래서 현재 코드가 `onerror` 안에서 새 `EventSource`를 직접 만들지는 않는다.

```tsx
eventSource.onerror = (err) => {
  console.error('SSE error', err);
};
```

여기서 다시 `new EventSource(...)`를 실행하면 브라우저의 자동 재연결과 겹쳐 연결이 중복될 수 있다.

`useEffect` cleanup의 `close()`는 사용자가 이 화면을 떠날 때 연결을 의도적으로 닫고 자동 재연결도 끝낸다.

```tsx
eventSource.close();
// readyState === EventSource.CLOSED
```

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
