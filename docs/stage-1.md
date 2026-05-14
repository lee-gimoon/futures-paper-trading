# 1단계: Binance Futures 호가 raw JSON 출력

작성일: 2026-05-14

## 1단계의 목적

```text
Binance USDⓈ-M Futures의 BTCUSDT partial depth stream에 연결해서
받은 JSON 문자열을 그대로 로그에 찍는 것.
```

파싱도, 메모리 보관도, 화면 표시도 아직 안 한다.
"WebSocket으로 진짜 데이터가 들어오는가"만 눈으로 확인하는 단계.

추가 제약 한 줄:

```text
앱 시작 시 자동 연결하지 않는다. HTTP POST 요청이 들어왔을 때만 연결한다.
```

이 한 줄 때문에 파일이 두 개로 갈렸다.

---

## 용어 한 줄 정리

WebSocket을 보면 머릿속에 그냥 이 이미지가 떠오르면 된다.

- **WebSocket** — 끊지 않는 통로. 한 번 열어두면 끊지 않고 계속 양쪽이 메시지를 주고받는다.
- **HTTP** — 묻고 끊는 일회성 통신. 한 번 묻고 답을 받으면 곧바로 끊어진다.

비유로는 WebSocket이 **전화**(여보세요 — … — 계속 통화 중),
HTTP가 **편지**(띵동 — 받음 — 안녕)에 가깝다.

---

## `@100ms`의 의미: 실제 거래소 vs 우리

`@100ms`는 **호가창 사진의 dispatch 주기**이지, "거래가 100ms마다 일어난다"는 뜻이 아니다.

- **실제 Binance**: 매칭 엔진은 μs 단위 실시간. 사람들의 주문은 즉시 체결된다.
  100ms는 외부 구독자에게 호가창 사진을 보내는 throttle 주기일 뿐.
- **우리 모의 거래소 (1~5단계)**: 그 사진을 그대로 받아 호가창의 진실로 쓴다.
  사진 사이의 깜빡임은 못 본다. paper trading의 의도된 단순화 — 손실이 아니라 설계 선택.

참고: Binance USDⓈ-M Futures의 WebSocket Market Streams가 partial depth stream에 대해
지원하는 throttle 옵션은 `500ms`, `250ms`, `100ms` 세 가지뿐이다.
**100ms가 가장 빠른 옵션**이고 그보다 빠른 건 제공하지 않는다.

용어:
- **dispatch** — "외부로 보내는" 행위. 여기서는 Binance 서버가 외부 구독자에게 호가창 메시지를 송출하는 것.
- **throttle** — "속도를 의도적으로 제한한다"는 뜻. 원래 변화마다 보낼 수도 있지만 100ms마다로 묶어 보내도록 제한해둔 것.

비유: 영화는 24fps로 보이지만, 영화 속 사건은 그 사이에도 연속해서 일어난다.

---

## 파일 두 개

위치: `src/main/java/com/example/futurespapertrading/market/`

### 1. `BinanceFuturesRawDepthStreamer.java` — 일꾼

WebSocket과 실제로 통신하는 부분.

```text
- wss://fstream.binance.com/ws/btcusdt@depth20@100ms 에 연결
- 들어오는 메시지 payload를 문자열로 꺼냄
- 그 문자열을 그대로 log.info(...)로 출력
- 연결 실패 시 log.warn(...)
```

핵심 메서드: `connect()`
한 번 호출되면 백그라운드에서 100ms마다 메시지가 흐르고 로그가 찍힌다.

HTTP를 전혀 모른다. 이 클래스에 `@RestController`, `@PostMapping` 같은 HTTP 어노테이션이 하나도 없다.
Spring `@Component`로 등록된 빈일 뿐이다 — "Spring이 관리해주는 평범한 객체"라는 가장 일반적인 표식.
누가 `connect()`를 부르든 상관 안 한다. 지금은 컨트롤러가 부르지만 테스트든 스케줄러든 동작은 똑같다.

→ 호출 방법이 바뀌어도 Streamer 코드는 그대로 둔다.

#### 이 이름을 붙인 이유

전체 이름: `BinanceFutures` + `Raw` + `Depth` + `Streamer`

- `Binance` — 어느 거래소인지 명시.
- `Futures` — 현물이 아니라 USDⓈ-M 선물. WebSocket 호스트가 `fstream.binance.com`인데 그 `f`가 futures를 뜻한다. 호스트 한 글자가 상품군을 가른다.
- `Raw` — 파싱 안 한 원본 JSON 문자열만 다룬다는 표식. 1단계 제약("받은 JSON 문자열을 그대로 로그에 찍는다. 파싱하지 않는다")을 이름에 박아둔 것. 2단계에서 파싱 컴포넌트(`BinanceFuturesDepthParser`)가 생기면 `Raw`가 빠진 형제로 갈라진다. 즉 **`Raw`가 붙은 것 = 아직 문자열 / 안 붙은 것 = 자바 객체로 가공됨**이라는 약속을 미리 깔아두는 셈.
- `Depth` — Binance가 호가창(order book) 데이터를 부르는 공식 용어. "depth of market"의 줄임말이다. stream 주소 `btcusdt@depth20@100ms`와 REST 엔드포인트 `/fapi/v1/depth`에 그대로 박혀 있다. 같은 단어를 코드에 쓰면 공식 문서·디버깅할 때 추적이 편하다.
- `Streamer` — "스트림을 받는 주체"라는 뜻. Binance가 push하는 끊임없는 데이터 흐름(stream)을 WebSocket으로 구독해서 받는 역할이라 붙였다. `XxxLogger`로 지으면 "로그만 찍는 유틸"처럼 보이고, SLF4J의 `Logger` 타입과 혼동되므로 피했다. 본질은 "데이터 받아오는 일꾼"이다.

### 2. `BinanceFuturesRawDepthController.java` — 스위치

HTTP 요청 한 번을 받아서 일꾼을 깨우는 부분.

```text
- POST /api/binance-futures/btcusdt/depth/raw/start 를 받는다
- 받으면 streamer.connect()를 호출한다
- "started" 문자열을 응답으로 돌려준다
```

WebSocket을 전혀 모른다. Streamer가 어떻게 동작하든 알 바 아니고,
"버튼을 누르면 켜라"만 한다.

#### 왜 POST인가

GET·POST를 보면 머릿속에 그냥 이 이미지가 떠오르면 된다.

- **GET** = "달라는 부탁". 데이터 좀 달라고 묻는 단순 조회. 서버는 안 바뀐다.
- **POST** = "시키는 명령". 서버에게 뭔가 일으키라고 시키는 것. 서버 상태가 바뀐다.

여기서는 내가 `/start` 요청을 보내서 서버 상태가 "WebSocket 연결 없음 → Binance와 연결되어 데이터가 흘러들어오는 상태"로 바뀌었다. 단순 조회가 아니라 동작 트리거였다. 그래서 POST를 썼다.

#### 이 이름을 붙인 이유

전체 이름: `BinanceFutures` + `Raw` + `Depth` + `Controller`

앞 네 토큰(`Binance` / `Futures` / `Raw` / `Depth`)의 의미는 Streamer와 동일하다(위 1번 참고). 핵심은 마지막 토큰이다.

- `Controller` — Spring MVC의 표준 suffix. `@RestController`나 `@Controller`로 어노테이션된 클래스, 즉 HTTP 요청을 받아 라우팅·응답하는 컴포넌트에 관례적으로 붙인다. 이 suffix를 보면 자바 개발자는 곧바로 "아, HTTP 입구구나"라고 읽는다. 다른 이름(`Endpoint`, `Handler` 등)도 가능하지만 Spring 진영의 표준 관례를 따르는 게 가장 무난.

같은 `BinanceFuturesRawDepth` 접두사를 Streamer와 공유하는 것은 의도된 것이다. 짝꿍 관계를 이름만 봐도 알 수 있도록 묶었다.

---

## 둘의 상관관계

```text
[브라우저/Postman]
       │ POST /api/binance-futures/btcusdt/depth/raw/start
       ▼
[BinanceFuturesRawDepthController]   ← HTTP 입구
       │ rawDepthStreamer.connect()
       ▼
[BinanceFuturesRawDepthStreamer]     ← 실제 WebSocket 클라이언트
       │ WebSocket 연결
       ▼
[Binance fstream.binance.com]
       │ 100ms마다 raw JSON push
       ▼
[애플리케이션 로그]                  ← 사람이 눈으로 확인
```

Spring 생성자 주입으로 연결된다.

```java
public BinanceFuturesRawDepthController(BinanceFuturesRawDepthStreamer rawDepthStreamer) {
    this.rawDepthStreamer = rawDepthStreamer;
}
```

Spring이 시작될 때 Streamer 빈을 만들어 Controller 생성자에 끼워 넣는다.
Controller는 자기 손으로 `new`하지 않는다.

---

## 왜 두 개로 나눴나

로드맵 1단계의 제약 한 줄에서 모든 게 떨어진다.

> "앱 시작 시 자동 연결하지 않고, HTTP POST 요청이 들어왔을 때만 연결한다."

### Controller가 따로 있는 이유

1. **자동 연결 금지** → `@PostConstruct`, `ApplicationRunner` 같은 자동 실행 훅을 못 쓴다 → `connect()`를 누군가가 외부에서 불러줘야 한다.
2. **HTTP가 그 외부 트리거** → HTTP 요청을 받는 책임을 Spring MVC 컨트롤러가 맡는다.

반대로 "자동 연결해도 된다"였다면 Streamer 하나에 `@PostConstruct`만 붙이면 끝. → Controller가 존재하는 이유는 "HTTP 트리거"라는 제약 한 줄이다.

### 두 책임을 한 클래스에 안 박는 이유

- **스위치(HTTP 입구)**: 어떤 URL이 어떤 메서드를 호출하는지
- **일꾼(WebSocket 클라이언트)**: 어디에 연결해서 메시지를 어떻게 처리하는지

이 둘이 한 클래스에 섞이면 책임이 흐려진다. WebSocket 로직만 따로 테스트하기 어렵고, 트리거 방법이 늘어날 때마다(스케줄러 등) WebSocket 코드를 또 손대야 한다. 그래서 Controller와 Streamer는 한 책임씩만 갖도록 분리한다.

---

## 나중에 보충해야 할 것

```text
JSON 파싱
중복 connect 방지 동시성 코드 (synchronized, volatile)
재연결
연결 status 노출
@PreDestroy 정리 (JVM 종료에 맡긴다)
테스트 주입용 부생성자
```

지금 단계에서는 "raw JSON이 로그에 찍히는가"만 본다.
위 항목들은 실제로 문제가 보일 때 해당 단계에서 도입한다.

---

## 어떻게 자랄까

- **Streamer**: 2단계에서 raw 로그를 유지한 채로 "파싱 결과를 다른 빈에 넘긴다" 한 줄이 추가된다.
- **Controller**: 2단계 이후 GET 엔드포인트(`/depth/latest` 등)가 늘어난다. 본질은 그대로 "HTTP 입구".
- 즉 지금의 1:1 짝꿍 구조가 곧 N:1 (여러 컨트롤러 endpoint가 한 streamer를 바라봄)로 변형된다.
