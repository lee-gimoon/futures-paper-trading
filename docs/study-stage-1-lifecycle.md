# 공부 노트: 1단계 코드의 전체 생애

작성일: 2026-05-16

1단계 코드가 `java -jar`부터 raw JSON 로그까지 시간 축으로 어떻게 흐르는지 한 장에 정리.

기준 그림은 [`three-roles-webflux-netty-thread.svg`](study-mvc-vs-webflux.md)의 5단계 흐름. 거기에 1단계만의 반전 — 사용자 POST 이후 우리 앱이 outbound WebSocket **클라이언트**로 변신 — 을 4박스 더 붙인 버전.

![stage1-execution-flow](https://raw.githubusercontent.com/lee-gimoon/futures-paper-trading/b2c97f5/docs/images/stage1-execution-flow.svg)

---

## 빨간 점선의 의미

점선 위쪽 (**Phase A**, 박스 1~4) — 표준 흐름. JVM이 뜨고 main 스레드가 Netty를 부팅해서 event loop 스레드 N개가 8080을 듣는다. 여기까지는 우리 코드가 **한 줄도 안 돈다.**

점선 아래쪽 (**Phase B**, 박스 5~8) — 1단계만의 반전. `roadmap.md`의 "앱 시작 시 자동 연결하지 않는다" 원칙 때문에, 사용자가 `POST /api/binance-futures/btcusdt/depth/raw/start`를 보내야 비로소 `BinanceFuturesRawDepthStreamer.connect()`가 호출되고, 같은 Netty event loop이 이번엔 Binance로 outbound WebSocket을 띄운다.

## 손(= OS가 띄운 Netty event loop 스레드 1개, 예: `reactor-http-nio-3` — 코드가 아닌, 코드를 실제로 실행하는 주체)이 똑같다

같은 event loop 스레드(`reactor-http-nio-*`)들이 들어오는 8080 요청도, 나가는 Binance WebSocket 메시지도 같이 처리한다. **서버 코드와 클라이언트 코드가 같은 손 위에서 돈다.** 박스 8의 빨간 테두리가 그 반전 포인트.

## 실제 실행 흐름 예시 — `reactor-http-nio-3`의 하루

가정: event loop 스레드 8개가 떠 있다 (`reactor-http-nio-1` ~ `reactor-http-nio-8`). 그중 손 3이 첫 POST 요청을 잡았다고 하자.

### 손 3의 시간 축

```text
T=0ms      [잠] selector로 자기 담당 소켓 감시 중

T=120ms    [깨어남] 8080에 POST /api/.../depth/raw/start 도착
           ├ HTTP 파싱 → WebFlux → Controller.start()
           ├ Streamer.connect() → webSocketClient...subscribe()
           │  └ Reactor가 Binance 연결 작업을 N개 손 중 하나에 배정
           │    (예시 가정: 같은 손 3에 떨어졌다)
           └ "started." 응답 송신
T=125ms    [잠]

T=128ms    [깨어남] 자기 큐에 쌓인 Binance 연결 작업 처리
           └ TLS handshake → WebSocket upgrade → 연결 성공
T=132ms    [잠]

T=232ms    [깨어남] Binance에서 첫 depth20 메시지 도착 (100ms 후)
           └ .map(getPayloadAsText) → .doOnNext(log.info)
T=234ms    [잠]

T=340ms    [깨어남] 8080에 다른 GET 요청 도착 (Binance 메시지 아님!)
           └ Controller 호출 → 응답
T=343ms    [잠]

T=432ms    [깨어남] Binance에서 두 번째 메시지 → log.info
T=534ms    [깨어남] Binance에서 세 번째 메시지 → log.info
T=620ms    [깨어남] 8080에 또 다른 POST 도착 → Controller → 응답
T=735ms    [깨어남] Binance 메시지 → log.info
...
(앱 종료까지 같은 패턴 반복)
```

이 식으로 **손 3 1개가 8080·Binance를 가리지 않고** 이벤트 단위로 휙휙 바꿔가며 처리한다. 깨어 있는 시간은 이벤트당 수 ms, 나머지 99%는 잠들어 있음.

(만약 Reactor가 Binance 연결을 손 5에 배정했다면 같은 흐름이 손 3과 손 5에 나뉜다. 어느 손에 배정되든 **하나의 손이 들어오는 일·나가는 일을 같이** 처리한다는 점은 동일.)

### Netty event loop 손 5 — 매니저-수천 고객 관계

```text
[손 5의 selector]
   ├── Binance 소켓 (현재 idle)
   ├── 8080 연결 #1 (idle)
   ├── 8080 연결 #2 ★데이터 도착!★
   ├── 8080 연결 #3 (idle)
   ├── ... (수천 개 등록 가능)
   └── 8080 연결 #N ★write 가능!★

손 5: selector가 "깨어난 소켓" 목록 줌 → 그것들만 빠르게 처리
      idle 소켓은 selector가 알아서 무시
```

- 손 5가 "Binance 연결 영구 담당" = **Binance 데이터 도착 시 처리 권한이 손 5에게만 있다**는 뜻. 손 5의 시간 전부를 차지한다는 의미가 아님.
- Binance가 100ms 동안 idle인 그 99ms 동안 — 손 5는 **다른 등록된 소켓들의 이벤트를 처리**.
- 손 5의 selector엔 Binance 1개만이 아니라 **수천 개 소켓이 같이 등록** 가능. (이게 Tomcat의 "1스레드 = 1연결 전용"과 결정적 차이. Tomcat 스레드는 연결 1개에 통째 묶이고 idle 시간에도 다른 일 못 함.)

### 대조 — Tomcat이라면

```text
T=120ms    [스레드 #47]   POST 받음 → 처리 → 응답 → 풀 반납
T=125ms    [스레드 #52]   Binance 연결 시작 → 여기 묶임!
T=232ms    [스레드 #52]   Binance 메시지 대기 중 — 다른 일 못 함
T=340ms    [스레드 #88]   GET 처리
T=432ms    [스레드 #52]   Binance 메시지 → log.info → 다시 대기
...
스레드 200개 중 #52는 앱 종료까지 Binance에만 묶임.
```

200개 중 1개가 외부 stream 1개에 통째로 점유됨. 외부 연결이 늘어날수록 가용 스레드가 줄어든다. WebFlux는 같은 손이 Binance 연결을 들고 있어도, 그 손이 **잠들어 있는 99%의 시간**에 다른 일을 같이 한다. 그래서 손 8개로 수천 개 연결을 처리 가능 — 박스 3의 *"손 1개가 연결 수천 개를 돌봄"* 의 의미.

## `.subscribe()`가 진짜 발동 스위치

박스 7 코드의 마지막 줄 `.subscribe()`. 이 한 줄이 없으면 위에 짜둔 `webSocketClient.execute(...).doOnNext(...)` 체인은 **선언만 돼있고 아무것도 안 흐른다.** Flux/Mono는 "구독자가 붙는 순간"부터 일하기 시작하는 콜드 스트림이어서, `.subscribe()`를 호출해야 비로소 Binance 연결 작업이 event loop에 예약된다. 호출 자체는 즉시 반환되므로 `connect()`는 막히지 않고 다음 줄로 진행, Controller도 곧바로 `"...started."` 응답을 돌려준다.

## 왜 Tomcat 안 쓰고 event loop(= Reactor Netty의 .jar 코드)?

**현재 1단계에서는 Tomcat이어도 문제 없음.** 외부 stream이 Binance 1개뿐이라 스레드 1개가 아래 패턴만 무한 반복하면 됨:

```text
[스레드 #52]  Binance 연결 성립 → 자기 큐에 메시지 대기 시작
T=100ms      [깨어남] 메시지 도착 → log.info → 다시 대기
T=200ms      [깨어남] 메시지 도착 → log.info → 다시 대기
T=300ms      [깨어남] 메시지 도착 → log.info → 다시 대기
... (앱 종료까지 반복)
```

스레드 #52가 99% 자고 1%만 일하지만 **다른 stream이 없으니 그게 문제 안 됨.** 1MB stack 1개만 점유, Tomcat 풀 200개에서 보면 1/200. 1단계 스케일만 보면 "Tomcat을 안 쓸 이유"가 안 보이는 게 맞음. 우위는 **스케일에서 비로소 드러남**.

### 스케일이 커지면 (로드맵 6~8단계)

- **스트림은 곱하기로 늘어남**: 심볼(BTC, ETH…) × 스트림 종류(depth, trade, kline, mark, user data) × 멀티 유저 = 수십~수백 개.
- **잠든 스레드도 공짜가 아님**: 1MB stack/스레드 + OS scheduler 부담. 300 스트림 = 300MB. event loop 8개 = 8MB.
- **외부 WebSocket 클라이언트는 사실 Tomcat 풀과 별개** (보통 별도 라이브러리가 자기 스레드 띄움). 풀이 다를 뿐 "외부 stream 1개 = 전용 스레드 1개" 본질은 동일.
- **로드맵 6~8단계**: REST snapshot 재구독, 주문 매칭, 멀티 심볼/계좌. 1단계는 그 미래를 위한 선투자.

### 세 .jar의 역할 — 자세히

기준 표:

| .jar | 역할 |
|---|---|
| `reactor-core` | 약속 타입(`Mono`/`Flux`)과 변환 연산자만. 추상 데이터 흐름. |
| `reactor-netty` | event loop + NIO. 그 약속 안에 실제 네트워크 바이트를 흘려보냄. |
| `spring-webflux` | reactor-netty raw HTTP를 `@Controller` 컨벤션으로 짤 수 있게 함. WebClient/WebFilter 부가. |

아래로 각 jar를 풀어서 — 특히 **WebFlux와 Reactor Netty의 분담**을 명확히.

#### `reactor-core.jar` — 약속 타입과 변환 연산자

순수 데이터 흐름 추상화. **네트워크도 Spring도 없음.** 메모리 안 데이터로만 노는 라이브러리.

제공하는 것:

- `Mono<T>` — "값 0~1개를 받을 약속"
- `Flux<T>` — "값 0~N개를 받을 약속"
- 변환 연산자 — `.map()`, `.filter()`, `.flatMap()`, `.doOnNext()`, `.then()` 등
- `.subscribe()` — 약속을 발동시키는 메서드 (이미지 박스 7의 그 한 줄)

→ **스레드 안 만듦. 네트워크 안 다룸.** "이런 식으로 비동기 데이터를 다루자"는 표준 인터페이스만 정의하는 라이브러리.

#### 잠깐 — "비동기 데이터"란?

**비동기의 본질** (같은 사실의 두 가지 표현):

- 호출자 시점: **결과를 기다리지 않고 다음 줄로 진행.**
- 프로그램 흐름 시점: **완료 순서가 코드 작성 순서와 달라짐** (통상 "실행 흐름이 달라짐"이라고 표현).

따라서 **비동기 데이터** = "지금 이 자리에 없고, 나중에 도착할 데이터". `Mono`/`Flux`는 그 약속을 담는 컨테이너.

```java
// 동기 — 줄이 끝나면 u에 값이 실제로 들어 있음
User u = userRepository.findById(1L);

// 비동기 — 줄이 끝나도 m 안엔 값이 없음, "나중에 도착할 User 약속"만
Mono<User> m = webClient.get()...bodyToMono(User.class);
```

주의: 위의 "실행 흐름이 달라짐"이 만약 **"여러 흐름이 동시에 갈라져 실행"**의 의미라면 그건 비동기가 아니라 **동시성/병렬성**. 비동기는 **단일 스레드에서도 가능** — Reactor는 같은 event loop 스레드가 여러 비동기 약속을 동시에 들고 있다가 도착 순서대로 처리. 흐름은 1개지만 완료 순서는 뒤섞임. (손 3 타임라인이 그 말 그대로.)

#### `reactor-netty.jar` — event loop + 실제 네트워크 I/O

reactor-core를 가져다 쓰면서, 그 위에 **실제 TCP/HTTP/WebSocket 바이트 처리**를 얹은 라이브러리. Spring과 무관하게 단독 사용 가능 (Node.js와 같은 위상).

제공하는 것:

- **event loop 스레드 생성** — 이미지 박스 2의 `new Thread().start() × N`이 여기 코드. `reactor-http-nio-*` 스레드들의 출처.
- **NIO selector 루프** — 한 손이 수천 연결을 다중 감시하는 무한 루프
- **HTTP 서버** — `HttpServer.create().port(8080).route(...)`
- **HTTP/WebSocket 클라이언트** — `HttpClient.create().websocket()` (우리 1단계 코드가 쓰는 것)

→ **실제 스레드를 만드는 유일한 jar.** 8개 손으로 수천 연결을 다중 처리하는 능력의 출처. Spring 없이도 단독으로 HTTP 서버를 돌릴 수 있음.

```java
// reactor-netty만으로 HTTP 서버 — Spring 없음
HttpServer.create()
    .port(8080)
    .route(routes -> routes
        .get("/hello", (req, res) ->
            res.sendString(Mono.just("Hello from Netty!"))))
    .bindNow();
```

#### `spring-webflux.jar` — @Controller 컨벤션 + WebClient/WebFilter

여기가 헷갈리기 쉬운 곳 — **WebFlux는 reactive 자체를 정의하지 않는다.** reactor-netty + reactor-core가 이미 reactive 풀스택을 다 제공. WebFlux는 그 위에 **Spring 스타일 어노테이션과 부가 기능**을 얹는 어댑터.

제공하는 것:

1. **`@Controller` 라우팅 (`DispatcherHandler`)** — `@PostMapping`, `@GetMapping` 어노테이션을 읽어 URL→메서드로 매핑. 이미지 박스 6의 "WebFlux 라우팅 → start()"의 정체.
2. **요청 바인딩** — JSON 바디 → `@RequestBody User user`, `@PathVariable Long id` 같은 파라미터로 역직렬화
3. **응답 직렬화** — 컨트롤러가 반환한 `Mono<User>`를 WebFlux가 **자기가 대신 `.subscribe()`** 해서 → User 객체 → JSON 바이트 → 응답 송신
4. **`WebClient`** — reactive HTTP 클라이언트 API (속은 reactor-netty의 HttpClient를 감싼 것)
5. **`WebFilter`** — 인증/로깅/CORS 같은 reactive 미들웨어 체인
6. **`ServerHttpRequest`/`ServerHttpResponse` 추상화** — Reactor Netty든 Undertow든 같은 인터페이스로 다룰 수 있게

→ **스레드 안 만듦. event loop 안 만듦.** 본질은 *"Reactor Netty의 raw HTTP를 Spring 어노테이션으로 짤 수 있게 해주는 어댑터 + 편의 기능"*.

```java
// 위 reactor-netty 예시와 동일한 결과지만, 람다 지옥 대신 어노테이션
@RestController
public class HelloController {
    @GetMapping("/hello")
    public Mono<String> hello() {
        return Mono.just("Hello from WebFlux!");
    }
}
```

#### WebFlux의 본질 = "Servlet 없이 Reactor Netty 위에서 도는 Spring 웹 프레임워크"

흔히 "WebFlux의 우위 = `Mono`/`Flux` 반환"이라고 말하지만, **Spring 5 이후 MVC도 `Mono`/`Flux` 반환을 받는다.** 진짜 차이는 그 아래 런타임.

|  | Spring MVC | Spring WebFlux |
|---|---|---|
| .jar | `spring-webmvc.jar` | `spring-webflux.jar` |
| 기본 서버 | Tomcat (Servlet) | Reactor Netty (Servlet 없음) |
| 스레드 모델 | 풀 200개, 요청당 1개 점유 | event loop 8개, NIO selector |
| `Mono` 처리 | Servlet async로 boundary에서만 다리 놓기 | end-to-end reactive, 다리 필요 없음 |

즉 **WebFlux = Reactor Netty 위에서 Servlet 없이 도는 Spring 웹 프레임워크**. `Mono`/`Flux` 중심이 되는 건 자연스러운 결과이지, WebFlux의 정의 자체는 아님.

#### WebFlux가 event loop을 지키는 메커니즘 — 강제가 아닌 '유도'

WebFlux가 컴파일 단계에서 블로킹을 막지는 않음. 컨트롤러 안에 `Thread.sleep(1000)` 적어도 컴파일 됨. 다만:

- reactive 생태계(WebClient, R2DBC, Reactor Netty의 WebSocket client 등)가 **전부 `Mono`/`Flux` 반환**으로 통일.
- 우리가 그 파이프 안에 머무르면 자연스럽게 non-blocking이 됨.
- 일부러 블로킹 코드(`Thread.sleep`, JDBC, 동기 HTTP)를 끼우려면 reactive 파이프 사이에 `.block()` 같은 어색한 호출이 필요. 그 자리에서 event loop이 멈추고, 그 손이 담당하던 다른 모든 연결도 같이 멈춤 → 사고.

→ WebFlux는 **"타입이 다르니까 블로킹 코드를 쓰기가 오히려 귀찮아지게"** 만드는 방식으로 event loop의 이득을 지킨다.

#### 비유로 정리

- **`reactor-core`** = 약속 양식 표준 ("이 양식으로 비동기 약속을 주고받자")
- **`reactor-netty`** = 식당 운영 방식 ("종업원 1명이 테이블 10개 동시 담당")
- **`spring-webflux`** = 식당 매니저 ("주문은 어노테이션 종이로 받고, 결과는 약속으로 받아둔")
- **`spring-webmvc`** = "한 손님 끝낼 때까지 못 떠남" 규칙 → event loop 위에 못 올림

#### WebFlux의 실제 클래스들 — 1단계 코드가 거치는 메서드

추상적으로 "WebFlux가 라우팅·바인딩·직렬화를 한다"고 말했는데, `spring-webflux.jar` 안에 실제로 들어 있는 클래스/메서드가 1단계 코드를 어떻게 처리하는지 스택 트레이스에서 볼 이름들로 구체화.

#### 핵심 클래스

| 클래스 | 역할 |
|---|---|
| `DispatcherHandler` | WebFlux의 메인 엔진. 모든 HTTP 요청이 여기를 거쳐감 |
| `RequestMappingHandlerMapping` | `@PostMapping`, `@GetMapping` 어노테이션 → 메서드 매핑 테이블 구축 |
| `RequestMappingHandlerAdapter` | 매칭된 컨트롤러 메서드를 실제로 invoke (리플렉션) |
| `ResponseBodyResultHandler` | 컨트롤러가 반환한 `Mono`/`Flux`를 `.subscribe()` → HTTP 응답 바이트로 직렬화 |
| `ReactorNettyWebSocketClient` | reactor-netty의 raw WebSocket을 Spring 인터페이스로 wrap (★ 1단계가 직접 쓰는 것 ★) |
| `WebClient` | reactive HTTP 클라이언트 (속은 reactor-netty `HttpClient` 감쌀) |
| `WebFilter` | 인증/로깅/CORS 미들웨어 체인 |
| `RouterFunction` / `HandlerFunction` | 어노테이션 대신 람다로 라우팅 짜는 함수형 API |
| `EnableWebFlux` | `@Configuration`에서 WebFlux 자동 설정 켜는 어노테이션 |

#### 우리 `POST /api/.../depth/raw/start` 한 번이 거치는 11단계

```text
1.  TCP 8080에 바이트 도착
    ↓ reactor-netty (event loop + NIO)
2.  reactor-netty의 HttpServer가 HTTP 헤더/URL 파싱
    ↓
3.  ReactorHttpHandlerAdapter.apply(req, res)         ← spring-web 브리지
    ↓
4.  HttpWebHandlerAdapter.handle(exchange)            ← spring-web
    ↓
5.  ★ DispatcherHandler.handle(exchange) ★            ← spring-webflux
    "이 요청 어떻게 처리할지 매핑 봐줘"
    ↓
6.  ★ RequestMappingHandlerMapping.getHandler(exchange) ★  ← spring-webflux
    부팅 시 구축해둔 매핑 테이블에서 검색:
      POST /api/binance-futures/btcusdt/depth/raw/start
        → BinanceFuturesRawDepthController.start()
    ↓
7.  ★ RequestMappingHandlerAdapter.handle(...) ★      ← spring-webflux
    리플렉션으로 controller.start() 호출
    ↓
8.  ── 우리 코드 진입 ──
    public String start() {
        rawDepthStreamer.connect();
        return "Binance Futures BTCUSDT raw depth stream started.";
    }
    ── 우리 코드 끝 ──
    ↓
9.  ★ ResponseBodyResultHandler.handleResult(...) ★   ← spring-webflux
    "started." String을 HTTP 응답 바이트로 직렬화
    ↓
10. ServerHttpResponse.writeWith(buffer)              ← spring-web
    ↓ reactor-netty
11. NIO selector로 응답 바이트 8080에 송신
```

★ 표시가 spring-webflux의 4개 클래스가 일하는 단계.

#### 1단계 코드 각 줄이 어느 WebFlux 클래스를 트리거하나

`BinanceFuturesRawDepthController.java`:

```java
@RestController                                   // RequestMappingHandlerMapping이 부팅 시 스캔
public class BinanceFuturesRawDepthController {

    @PostMapping("/api/.../depth/raw/start")      // 매핑 테이블에 등록 (6번 단계에서 검색됨)
    public String start() {                       // RequestMappingHandlerAdapter가 리플렉션으로 호출 (7번)
        rawDepthStreamer.connect();
        return "...started.";                      // ResponseBodyResultHandler가 직렬화 (9번)
    }
}
```

`BinanceFuturesRawDepthStreamer.java`:

```java
@Component
public class BinanceFuturesRawDepthStreamer {

    private final WebSocketClient webSocketClient =
        new ReactorNettyWebSocketClient();        // ★ spring-webflux 클래스
                                                   //    속에 reactor-netty의 HttpClient.websocket() 들어있음
    public void connect() {
        webSocketClient.execute(URI, session ->   // ← ReactorNettyWebSocketClient의 execute()
            session.receive()                      // ← reactor-netty: Flux<WebSocketMessage>
                .map(WebSocketMessage::getPayloadAsText)
                                                   // ← ☆ reactor-core 연산자 ☆
                .doOnNext(msg -> log.info(...))    // ← ☆ reactor-core ☆
                .then())                           // ← ☆ reactor-core ☆
            .subscribe();                          // ← ☆ reactor-core: 발동 스위치 ☆
    }
}
```

→ 1단계 한 파일 안에 **WebFlux 4개 클래스(★)** (`RequestMappingHandlerMapping` + `Adapter` + `ResponseBodyResultHandler` + `ReactorNettyWebSocketClient`), **reactor-netty의 NIO/WebSocket 코드**, **reactor-core의 연산자들(☆)** 이 동시 협업.

#### `.subscribe()`는 누구 거? — 정의자와 호출자 구분

위 표에서 `ResponseBodyResultHandler`가 *"`Mono`/`Flux`를 `.subscribe()` → 직렬화"*라고 적혀 있고, 코드 주석에선 `.subscribe()`를 ☆ reactor-core ☆로 표시했다. 모순처럼 보이지만:

> **`.subscribe()` 메서드 정의는 reactor-core.** 그걸 호출하는 주체는 그때그때 다름. 우리 1단계는 **우리 코드가 직접** 호출 (`Streamer.connect()` 끝줄). Mono 반환 컨트롤러를 짜았다면 **WebFlux의 `ResponseBodyResultHandler`가 대신** 호출했을 것.

| 질문 | 답 |
|---|---|
| `.subscribe()`는 누구 소유 메서드? | reactor-core (`Mono`/`Flux` 클래스의 메서드) |
| 1단계 `Streamer.connect()`의 `.subscribe()` 호출자? | 우리 코드가 직접 |
| Mono 반환 컨트롤러였다면 호출자? | WebFlux의 `ResponseBodyResultHandler` |

비유: `String.length()`도 JDK가 정의했지만 호출은 우리 코드도 Spring 코드도 누구나 함 — **메서드 정의자와 호출자는 별개**.

#### "`Mono`/`Flux`로 유도"가 구체적으로 어떻게 보이는가

같은 "유저 조회" 컨트롤러를 두 가지 방식으로 비교.

**Spring MVC (동기 반환)**

```java
@GetMapping("/user/{id}")
public User getUser(Long id) {
    User u = userRepository.findById(id);  // 여기서 200ms 멈춤
    return u;                              // 200ms 후 반환
}
```

- 반환 타입: `User` — 값 그 자체.
- 메서드를 부른 스레드가 DB 응답 올 때까지 200ms **막힌다.** 그동안 다른 일 못 함.

**Spring WebFlux (Mono 반환)**

```java
@GetMapping("/user/{id}")
public Mono<User> getUser(Long id) {
    return userRepository.findById(id);    // Mono 즉시 반환
    // 메서드는 1ms도 안 걸리고 끝남
}
```

- 반환 타입: `Mono<User>` — 값이 아니라 **"값을 받을 약속"**.
- 메서드를 부른 event loop 스레드는 Mono만 받아들고 곧바로 다음 일로 넘어감. DB가 응답하면 event loop이 그제서야 깨어나 콜백을 실행해서 응답 송신.

**핵심 차이:**

- 동기: "결과 들고 와" → 그동안 너는 멈춰 있어.
- Mono 반환: "결과 약속해" → 네 스레드는 계속 일해.

**한 줄**: 1단계엔 오버킬, 6단계부터 필수. event loop은 Reactor Netty가 만들고, WebFlux는 우리 코드가 그걸 막지 않게 지킨다.
