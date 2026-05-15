# 공부 노트: 1단계 코드의 전체 생애

작성일: 2026-05-15

[stage-1.md](stage-1.md)에서 만든 1단계 코드가 **언제, 누구에 의해, 어떤 순서로** 실행되는지 시간 축으로 풀어본 것.

> 코드를 읽는다 = 글자 따라가기. **코드를 이해한다 = 시간 따라가기.**
> 파일을 닫고도 머릿속에서 흐름이 재생되면 그 코드는 이해한 것이다.

---

## 왜 "생애"가 중요한가

파일은 **공간**(위→아래로 적힌 글자), 실행은 **시간**(언제 → 무엇이 → 어디서). 둘이 잘 안 맞는다.

```text
파일에서 보이는 순서:              실제 실행 순서:
┌────────────────────────┐        ┌────────────────────────┐
│ @Component              │        │ 1. JVM 시작            │
│ class Streamer {        │        │ 2. Spring이 Streamer    │
│   field initializer     │        │    객체 new            │
│   ↓                     │        │ 3. 한참 뒤 POST 도착   │
│   public connect() {    │        │ 4. Reactor Netty 받음  │
│     ...                 │        │ 5. DispatcherHandler   │
│     .subscribe();       │        │ 6. Controller.start()  │
│   }                     │        │ 7. connect() 호출      │
│ }                       │        │ 8. .subscribe() 발동   │
└────────────────────────┘        │ 9. Binance 메시지 흐름 │
                                   │    (앱 종료까지)        │
                                   └────────────────────────┘
```

특히 Spring/Reactor 같은 프레임워크는 **너의 코드를 누군가가 알아서 부른다.** 그 "누가, 언제"를 모르면 파일을 100번 읽어도 동작이 안 보인다.

→ 버그의 본질은 거의 항상 "타이밍"이라서, 생애를 못 그리면 버그도 못 잡는다.

---

## 한눈에 보는 세 페이즈

1단계 코드의 생애는 세 페이즈로 나뉜다.

```text
앱 시작                        POST 도착                          앱 종료
   │                              │                                  │
   ▼                              ▼                                  ▼
═══Phase 0═══════════════════Phase 1═════════════════════════════════════
   │                              │
   ├ 빈 생성                       ├ Reactor Netty 수신
   ├ 8080 listen                  ├ DispatcherHandler 라우팅
   └ 준비 완료                     ├ Controller.start()
                                  ├ Streamer.connect() ─┐ Phase 2 시작
                                  └ 응답 반환            │
                                                        ▼
                            ═══Phase 2 (앱 종료까지)═══════════════════════
                              │
                              ├ Binance 메시지 100ms마다
                              ├ Flux 파이프 흐름
                              └ log.info(...) 반복
```

각 페이즈를 하나씩 풀어본다.

---

## Phase 0 — 앱 시작 (요청 오기 전)

`./gradlew bootRun` 또는 `java -jar app.jar` 실행 직후.

```text
1. JVM 프로세스 1개 뜸
2. main() → SpringApplication.run() 호출
3. Spring Boot 부팅 시작
4. 컴포넌트 스캔: @Component, @RestController 어노테이션 검색
5. IoC 컨테이너가 빈을 생성:
   ├── BinanceFuturesRawDepthStreamer 객체 1개      ← @Component
   │   └── 필드 초기화: ReactorNettyWebSocketClient 객체 1개도 같이 생성
   └── BinanceFuturesRawDepthController 객체 1개    ← @RestController
       └── 생성자에 Streamer 빈 주입
6. DispatcherHandler가 라우팅 테이블 구축:
   "POST /api/binance-futures/btcusdt/depth/raw/start
    → BinanceFuturesRawDepthController.start()"
7. Reactor Netty가 8080 포트에서 listen 시작 (이벤트 루프 스레드 띄움)
8. 콘솔에 "Started ... in N seconds" 로그
9. 요청 대기 상태
```

이 시점에 JVM 메모리에는 [3층 박스](study-mvc-vs-webflux.md#둘이-어떻게-함께-도나-1)가 다 차 있다. 단, **WebSocket 연결은 아직 없다.** [stage-1.md](stage-1.md)의 "앱 시작 시 자동 연결하지 않는다" 제약 때문.

핵심 포인트:

- 너의 `Streamer`, `Controller` 객체는 이 시점에 **이미 생성돼있다.** "POST가 와야 만들어진다"가 아니다.
- 다만 객체가 있을 뿐, 아무 메서드도 아직 안 불렸다.
- `connect()`는 호출되지 않았으므로 Binance WebSocket은 끊겨 있다.

---

## Phase 1 — POST 요청이 도착

Postman 또는 curl:

```text
POST http://localhost:8080/api/binance-futures/btcusdt/depth/raw/start
```

이 한 번의 요청이 그림의 3층을 위→아래→위로 통과한다.

### ① Reactor Netty 층 (가장 바깥)

```text
[Postman]
   │ HTTP 바이트
   ▼ TCP 8080
[Reactor Netty 이벤트 루프 스레드]
   - 바이트 파싱: URL, Method, Headers, Body 추출
   - ServerHttpRequest 객체 1개 생성
   - DispatcherHandler.handle(request) 호출
```

`Streamer`와 `Controller`는 이 단계에서 **아무것도 모른다.** Reactor Netty가 혼자서 바이트 → 자바 객체 변환.

### ② Spring WebFlux 층 (DispatcherHandler 박스)

```text
[DispatcherHandler.handle(request)]
   - RequestMappingHandlerMapping에 물어봄:
     "POST /api/.../start 은 어느 메서드?"
   - 답: BinanceFuturesRawDepthController.start()
   - Phase 0에서 만들어 둔 Controller 빈을 가져옴
   - start() 메서드 호출
```

라우팅의 근거는 [BinanceFuturesRawDepthController.java:19](../src/main/java/com/example/futurespapertrading/market/BinanceFuturesRawDepthController.java:19)의 `@PostMapping(...)` 어노테이션.

### ③ 너의 메서드 (가장 안쪽 박스)

[BinanceFuturesRawDepthController.java:20-23](../src/main/java/com/example/futurespapertrading/market/BinanceFuturesRawDepthController.java:20):

```java
public String start() {
    rawDepthStreamer.connect();
    return "Binance Futures BTCUSDT raw depth stream started.";
}
```

두 줄밖에 안 한다.

```text
[start() 실행]
   - 줄 1: rawDepthStreamer.connect() 호출
            └→ Streamer.connect()로 잠깐 들어감 → Phase 2 시작 (다음 절에서 자세히)
            └→ connect() 자체는 .subscribe()까지 호출하고 즉시 리턴 (블로킹 X)
   - 줄 2: "Binance Futures BTCUSDT raw depth stream started." 반환
```

### ④ 응답이 그림을 거꾸로 타고 올라감

```text
[너의 메서드] 가 반환한 String
   ▲
[Spring WebFlux: HttpMessageConverter]
   - String → HTTP response body (text/plain 바이트)
   ▲
[Reactor Netty]
   - HTTP 응답 헤더 + 본문 바이트로 직렬화
   - TCP 8080 → Postman으로 송신
   ▲
[Postman 화면]
   - "Binance Futures BTCUSDT raw depth stream started." 표시
```

→ Phase 1 종료. 시작부터 끝까지 **수 ms**.

---

## Phase 2 — WebSocket 메시지가 흘러오기 시작

Phase 1의 ③ 단계에서 `rawDepthStreamer.connect()`가 호출된 순간, **백그라운드에서 별개의 흐름**이 시작된다. 이건 그림의 3층 구조(서버 역할)와는 별도 — 이번엔 같은 Reactor Netty가 **클라이언트** 역할.

[BinanceFuturesRawDepthStreamer.java:27-42](../src/main/java/com/example/futurespapertrading/market/BinanceFuturesRawDepthStreamer.java:27):

```java
public void connect() {
    log.info("Connecting to Binance Futures depth stream: {}", BTCUSDT_DEPTH_STREAM_URI);
    webSocketClient.execute(BTCUSDT_DEPTH_STREAM_URI, session ->
            session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(message -> log.info("Binance Futures raw depth JSON: {}", message))
                    .then())
        .doOnError(error -> log.warn("Binance Futures depth stream failed", error))
        .subscribe();
}
```

### 시작 단계 (connect() 호출되는 그 순간)

```text
[connect() 호출됨]
   │
   ▼
[ReactorNettyWebSocketClient.execute(uri, handler)]
   - 같은 JVM의 Reactor Netty가 이번엔 클라이언트로 동작
   - Binance fstream.binance.com:443 (wss://) TCP+TLS 연결
   - HTTP Upgrade 요청 → WebSocket 핸드셰이크
   - 연결 성공 → WebSocketSession 객체 생성
   │
   ▼
[handler 람다 등록 (실행 아직 X)]
   session.receive()                      // Flux<WebSocketMessage> 반환
     .map(WebSocketMessage::getPayloadAsText)
     .doOnNext(msg → log.info("...", msg))
     .then()
   │
   ▼
[.subscribe()]
   - 이 한 줄이 진짜 발동 스위치
   - 위 체인이 활성화돼서 메시지가 실제로 흐르기 시작
   │
   ▼
[connect() 메서드는 여기서 즉시 리턴]
   - .subscribe()는 블로킹 X — "흐름 시작했다"만 통보
   - 호출자(Controller.start())가 다음 줄로 진행해서 응답 반환
```

### 지속 단계 (앱 종료 때까지 계속)

```text
[Binance fstream.binance.com]
   │ 100ms마다 WebSocket 메시지 push
   ▼
[Reactor Netty 이벤트 루프]
   - 메시지 수신
   - Flux 파이프에 흘려보냄
   │
   ▼
[.map(getPayloadAsText)]
   - WebSocketMessage → String
   │
   ▼
[.doOnNext(log.info)]
   - "Binance Futures raw depth JSON: {...}" 로그 출력
   │
   ▼
(다음 메시지 대기 — 100ms 후 반복)
```

→ 이 흐름은 **앱이 살아있는 동안 계속 반복**. 한 번 시작되면 멈출 트리거가 없다 (1단계의 의도된 단순화 — [stage-1.md의 나중에 보충해야 할 것](stage-1.md#나중에-보충해야-할-것) 참고).

---

## 핵심 takeaways

이 다섯 줄이 머릿속에서 자동 재생되면 1단계 코드는 "이해한" 것.

1. **객체는 Phase 0에서 미리 만들어진다.** 요청이 만들지 않는다. `@Component`, `@RestController` 어노테이션이 그 결정의 근거.
2. **Phase 1과 Phase 2는 동시에 진행 중이다.** Phase 1 응답이 돌아간 뒤에도 Phase 2는 백그라운드에서 계속 흐른다.
3. **`.subscribe()`가 진짜 발동 스위치.** 호출 안 하면 Flux 체인은 "선언만" 돼있고 아무것도 안 흐른다.
4. **연결은 비대칭이다.** 외부 → 우리 (Postman → 서버)는 동기 응답. 우리 → 외부 (우리 → Binance)는 비동기 스트림.
5. **같은 Reactor Netty가 서버이자 클라이언트.** 같은 이벤트 루프 스레드가 들어오는 HTTP 요청과 나가는 WebSocket 메시지를 같이 다중 처리한다.

---

## 다음 단계에서 자랄 부분

[stage-1.md의 어떻게 자랄까](stage-1.md#어떻게-자랄까) 참고. 큰 그림은 그대로 유지되고 각 페이즈 안의 내용물이 두꺼워지는 식:

- **Phase 0** — 빈이 늘어남 (Parser, OrderBook 등). 라우팅 테이블에 GET 엔드포인트 추가.
- **Phase 1** — 새로운 컨트롤러 메서드들 (`/depth/latest` 등)이 같은 패턴으로 추가.
- **Phase 2** — `.doOnNext(log.info)` 자리에 `.map(parse)` → `.doOnNext(orderBook::update)` 같은 단계가 끼어들고, log는 옆 가지로 빠짐.

지금 1단계의 세 페이즈 그림이 머릿속에 박혀있으면 2단계 이후로 갈 때마다 "어디가 두꺼워졌나"만 추가로 학습하면 된다. 처음이 제일 힘들다.
