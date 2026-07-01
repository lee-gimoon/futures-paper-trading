# 🔁 Reactor pub/sub 구조: 데이터 흐름과 구독 신호

Binance WebSocket depth 스트림 `connect()` 코드를, 이 프로젝트가 쓰는 비동기 스트림 도구의 관점에서 분석. 핵심은 **신호는 위로 거슬러 올라가고(upstream), 데이터는 아래로 흘러내린다(downstream)**는 양방향성.

## 용어 정리: 무엇이 무엇인가

"Reactor pub/sub"을 쓸 때 실제로 같이 굴러가는 라이브러리·사양 4개:

| 층 | 정체(역할) | 엔진(실제 일하는 컴포넌트) | 엔진이 하는 일 | 이 프로젝트에서의 모습 |
| --- | --- | --- | --- | --- |
| **Spring WebFlux** | **리액티브 웹 프레임워크** — HTTP/WebSocket을 어노테이션 컨트롤러·함수형 라우팅으로 노출하고, 요청/응답을 Reactor 타입(`Mono`/`Flux`)으로 다룸. `WebClient`·`WebSocketHandler`·필터·JSON 인코더 같은 "Spring 스타일 웹 개발 경험"을 reactive로 제공. 기본 서버/클라이언트 엔진은 Reactor Netty. | `DispatcherHandler` (+ `HandlerMapping`, `HandlerAdapter`, `HandlerResultHandler`) | 들어온 HTTP/WebSocket 요청을 적절한 핸들러(컨트롤러·`WebSocketHandler`)로 라우팅·디스패치, 핸들러가 반환한 `Mono`/`Flux`를 응답으로 인코딩 | `WebSocketHandler`, `WebSocketSession`, `WebSocketMessage` (`org.springframework.web.reactive.socket`) — Binance 연결이 쓰는 묶음 |
| **Reactor Netty** | **Netty(비동기 네트워크 프레임워크) 위에 만들어진 라이브러리** — TCP/UDP/HTTP(1·2)/WebSocket 통신을 `Mono`/`Flux`로 다룰 수 있게 해줌. WebFlux의 기본 클라이언트·서버 엔진. | Netty의 `EventLoopGroup` (NIO selector 스레드들) + `Channel`/`ChannelPipeline` | 소켓 I/O, 바이트 버퍼, TLS, WebSocket 프레임 파싱 — **raw 바이트 ↔ 자바 객체** 변환. 콜백 기반 결과를 `Mono`/`Flux`로 감싸서 위 층에 넘김 | `webSocketClient.execute(URI, handler)` 내부에서 Binance와 핸드셰이크·프레임 처리 |
| **Project Reactor** | **흐름 엔진(라이브러리)** — Reactive Streams 사양을 구현한 자바 라이브러리. `Mono`/`Flux` 자체가 `Publisher<T>` 구현체이고, 그 위에 합성 연산자·스케줄링·backpressure를 얹음. WebFlux와 Reactor Netty가 의존하는 코어. | `Subscription`/`Subscriber` 기반 demand-driven 실행기 + `Schedulers`(스레드풀) | 파이프라인을 `.subscribe()` 시점에 평가, **backpressure**(요청량 신호) 전파, 연산자 합성·스케줄링 | `Mono`, `Flux`, `.map()`, `.doOnNext()`, `.then()`, `.subscribe()` |
| **Reactive Streams** | **사양(spec)** — 자바 인터페이스 4개(`Publisher<T>`, `Subscriber<T>`, `Subscription`, `Processor`)와 그 계약(특히 **backpressure**)을 정의. JDK 9부터 `java.util.concurrent.Flow`로 표준 라이브러리에 편입. | (엔진 없음 — 규칙만 정의) | 위 세 엔진이 따라야 할 계약(인터페이스 4개 + backpressure 룰) | 직접 보이진 않음 — `Mono`/`Flux`가 따르는 규칙 |

**읽는 팁**

- **가로 한 줄** = "이 층이 무엇이고(정체) → 안에서 누가 일하고(엔진) → 그 엔진이 뭘 하고(엔진의 일) → 우리 코드에 어떤 얼굴로 나타나는지(프로젝트의 모습)"
- **세로(아래→위)** = 의존 방향. 아래 층이 위 층에게 `Mono`/`Flux`를 건네주고, 위 층은 그것을 자기 도메인 언어(HTTP/비즈니스)로 해석

**표를 읽는 핵심**: 이 4개는 같이 동작하는 부품들이고, 우리가 쓰는 `Mono`/`Flux` 한 줄 뒤에서 네 개가 동시에 굴러간다. **위의 두 줄은 우리 코드가 직접 만지는 것**, **아래의 두 줄은 뒤에서 자동으로 굴러가는 것**.

- `@GetMapping`, `WebSocketHandler` 같은 웹 어노테이션·인터페이스 → **Spring WebFlux**가 제공
- `Mono`, `Flux`, `.map()`, `.subscribe()` → **Project Reactor**가 제공
- WebSocket 핸드셰이크·프레임 조립은 우리가 안 만지고 → **Reactor Netty**가 자동 처리
- 위 셋이 다 따르는 공통 약속(`Publisher`/`Subscriber`/`Subscription`/`Processor` 인터페이스) → **Reactive Streams** 사양

즉 우리가 코드에서 직접 타이핑하는 건 위쪽 두 줄뿐이고, 아래쪽 둘은 그 위쪽 두 줄이 실제로 굴러가도록 받쳐주는 부품. WebFlux starter 한 줄(`spring-boot-starter-webflux`)이 이 넷을 통째로 끌어온다.

### API 코드로 보는 4개의 맞물림

이 프로젝트의 Binance WebSocket 연결 코드를 부품별로 라벨링해보면, 한 줄짜리 체이닝 안에 네 부품이 다 등장한다:

```java
WebSocketHandler handler = session ->                  // ① WebFlux 인터페이스
    session.receive()                                  // ① WebFlux 메서드 + ② Reactor 타입(Flux) 리턴
        .map(WebSocketMessage::getPayloadAsText)       // ③ Reactor 연산자
        .doOnNext(this::logRaw)                        // ③ Reactor 연산자
        .then();                                       // ③ Reactor 연산자 (Mono<Void>)

webSocketClient.execute(URI.create(URL), handler)      // ① WebFlux API → ④ Reactor Netty가 실제 굴림
    .subscribe();                                      // ⑤ Reactive Streams 계약 발동
```

| 라벨 | 소속 | 하는 일(역할) |
| --- | --- | --- |
| ① | **Spring WebFlux** | 우리가 직접 만지는 인터페이스 — `WebSocketHandler`, `WebSocketSession`, `webSocketClient`. 패키지는 `org.springframework.web.reactive.socket.*` |
| ② | **Project Reactor** | `session.receive()`의 리턴 타입이 `Flux<WebSocketMessage>` — WebFlux가 Reactor 타입을 1급으로 채택했기 때문에 WebFlux API가 Reactor 타입을 그대로 노출 |
| ③ | **Project Reactor** | `.map()`·`.doOnNext()`·`.then()` 같은 합성 연산자. `reactor.core.publisher.Flux`/`Mono`의 메서드들 |
| ④ | **Reactor Netty** | 안 보이지만 실제 TCP 핸드셰이크·WebSocket 프레임 조립·바이트 I/O를 처리. WebFlux의 `WebSocketClient` 인터페이스의 기본 구현체가 `ReactorNettyWebSocketClient` |
| ⑤ | **Reactive Streams** | `.subscribe()`가 발동시키는 `Publisher<T>.subscribe(Subscriber)` 계약. `Mono`/`Flux`는 이 사양의 `Publisher<T>` 구현체이고, 구독 신호·데이터·종료 신호가 다 이 계약 위에서 흐름 |

#### 의존 방향: 위가 아래에 의존

- **Spring WebFlux**의 모든 공개 시그니처가 `Mono`/`Flux`를 리턴 → **Project Reactor에 의존**
- **Spring WebFlux**의 WebSocket·HTTP 구현체가 Netty 위에서 동작 → **Reactor Netty에 의존**
  - 여기서 "구현체"는 **WebFlux가 자기 인터페이스를 자기가 구현한 어댑터 클래스**(예: `WebSocketSession` 인터페이스 ↔ `ReactorNettyWebSocketSession` 구현체)를 가리킨다. 이 구현체는 `spring-webflux.jar` 안에 들어 있지만, 내부에서 Reactor Netty 클래스(`WebsocketInbound` 등)를 직접 import·호출하므로 **Reactor Netty에 의존**. 반면 인터페이스(`WebSocketSession`) 자체는 Netty를 한 줄도 import하지 않아 **엔진 독립적** — Undertow·Jetty용 구현체도 같은 인터페이스를 구현해서 교체 가능.
- **Project Reactor**의 `Mono`/`Flux`가 `org.reactivestreams.Publisher<T>`를 구현 → **Reactive Streams에 의존**
  - 여기서 "구현"은 Project Reactor가 **외부 사양(Reactive Streams)의 인터페이스를 자기 클래스로 구현**한다는 뜻 (`Publisher<T>` ↔ `Mono`/`Flux`). 인터페이스는 `reactive-streams.jar`에, 구현체는 `reactor-core.jar`에 따로 들어있고, `reactor-core`가 `import org.reactivestreams.Publisher`를 박고 있어 reactive-streams jar 없이는 컴파일 안 됨.
  - 인터페이스 자체는 특정 구현에 묶이지 않아 RxJava의 `Flowable` 같은 다른 구현체와도 호환됨 — 그래서 Reactive Streams는 **여러 라이브러리가 상호운용하라고 만든 표준 사양**.
- **Reactive Streams**는 인터페이스 4개만 정의 — 의존할 곳 없는 최하단

> [!NOTE]
> 💡 **"위에서 동작한다" = "의존한다"** — WebFlux의 구현체 클래스(`ReactorNettyWebSocketSession`, `ReactorHttpHandlerAdapter` 등) 안에 `import io.netty.*`, `import reactor.netty.*`가 박혀있고, 그 jar가 없으면 WebFlux 코드 자체가 컴파일·실행이 안 된다. 그래서 **"WebFlux는 Netty 위에서 동작한다 = WebFlux의 구현체가 Netty(와 Reactor Netty)의 구현체를 호출한다 = WebFlux는 Netty에 의존한다"** 가 같은 의미.
>
> 단, **WebFlux도 자기 몫의 일(라우팅·컨트롤러 디스패치·타입 변환·JSON 인코딩·필터 체인 등)은 직접 자기 코드로 수행**하고, **저수준 네트워크 I/O(소켓·바이트·프레임·EventLoop)만 Netty에 위임**한다. 즉 "WebFlux는 Netty를 호출만 한다"가 아니라 **"WebFlux는 자기 일을 하면서 저수준 I/O 부분만 Netty에 위임한다"** 가 정확.

즉 한 줄짜리 `session.receive().map(...).then().subscribe()` 안에서:

- 진입점(`session`, `.receive()`)은 **WebFlux**가 만든 인터페이스
- 리턴 타입으로 받는 `Flux`/`Mono`와 연산자(`.map`·`.then`)는 **Project Reactor**
- `session.receive()` 안에서 실제로 회선을 굴리는 일은 **Reactor Netty**
- 그 모든 `.subscribe()`/신호 전파가 따르는 약속은 **Reactive Streams**

네 부품이 각자 맡은 영역이 분명히 다르고, 그래서 한 줄 코드에 다 같이 등장할 수 있다.

### 의존성으로는 어떻게 들어오나

이 프로젝트의 `build.gradle`에는 starter 한 줄만 있다:

```javascript
implementation 'org.springframework.boot:spring-boot-starter-webflux'
```

이 한 줄이 끌고 오는 4층 (transitive dependency):

| 층 | 의존성 좌표 | 이 페이지에서의 정체 |
| --- | --- | --- |
| 사양 | `org.reactivestreams:reactive-streams` | `Publisher`/`Subscriber`/`Subscription`/`Processor` 인터페이스 4개 |
| 흐름 엔진 | `io.projectreactor:reactor-core` | **Project Reactor** 본체 — `Mono`/`Flux`와 모든 합성 연산자 |
| I/O 엔진 | `io.projectreactor.netty:reactor-netty-http` | **Reactor Netty** — Netty 위에 Reactor를 얹어 만든 비동기 HTTP/WebSocket 서버·클라이언트 |
| 웹 프레임워크 | `org.springframework:spring-webflux` | **Spring WebFlux** — 컨트롤러·라우터·`WebClient`를 Reactor 타입으로 노출 |

즉 webflux starter 하나가 **Reactive Streams 사양 → Project Reactor 구현 → Reactor Netty(전송) → Spring WebFlux(웹)** 4층을 통째로 깔아준다.

### Spring WebFlux는 정확히 뭐하는 계층이냐

Reactor Netty까지만 있으면 "8080 포트 잡고 HTTP 바이트 주고받는 비동기 서버"는 띄울 수 있다. 하지만 그건 raw HTTP 입출구일 뿐, "`GET /api/btcusdt/depth`가 오면 이 메서드를 호출하라" 같은 웹앱 개발자가 친숙한 추상화는 없다. **WebFlux는 그 위에 "Spring 스타일 웹앱 개발 경험"을 reactive로 얹어놓는 프레임워크**다. 구체적으로 제공하는 것:

- **라우팅** — `DispatcherHandler`가 URL을 보고 어느 핸들러로 보낼지 결정 (MVC의 `DispatcherServlet` 자리)
- **컨트롤러 API** — `@RestController`·`@GetMapping`·`@RequestBody`·`@PathVariable` 어노테이션, 또는 함수형 라우팅(`RouterFunction`)
- **HTTP 추상화** — `ServerHttpRequest`·`ServerHttpResponse` (Servlet API에 의존하지 않는 자체 타입)
- **WebSocket** — `WebSocketHandler`·`WebSocketSession`·`WebSocketMessage` (이 프로젝트의 Binance 연결이 정확히 이 묶음을 쓰는 중)
- **JSON 변환** — Jackson 기반 인코더/디코더 자동 등록
- **필터 체인** — `WebFilter`로 인증·로깅·CORS 같은 횡단 관심사 처리
- **HTTP 클라이언트** — `WebClient` (블로킹 `RestTemplate`의 reactive 후속)
- **생태계 연동** — Spring Security·Bean Validation을 reactive 방식으로 통합

즉 MVC가 "Servlet API + Tomcat" 위에서 한 일(컨트롤러·라우팅·JSON 변환·필터·보안)을, WebFlux는 **"자체 reactive 추상화 + Reactor Netty"** 위에서 하는 셈. 두 진영이 **같은 추상화 레벨, 다른 사상**으로 짜였다.

> [!NOTE]
> 💡 **API = 인터페이스(계약), 엔진 = 구현체(저수준 I/O를 실제로 하는 코드)** — Servlet API와 WebFlux의 HTTP 추상화 인터페이스(`ServerHttpRequest`, `WebSocketSession` 등)는 둘 다 인터페이스 모음일 뿐이고, 저수준 I/O는 Tomcat·Reactor Netty 같은 엔진이 한다.

Reactor만 단독으로 쓰고 싶다면 `io.projectreactor:reactor-core` 하나만 추가하면 되고, 그러면 위 4층 중 **아래 두 줄(사양 + Reactor)만** 들어온다 — Netty도 WebFlux도 안 따라온다.

### Netty와 Reactor: 그 밑의 두 엔진

"엔진"이라는 단어가 이 스택 안에서 두 군데에서 쓰이는데 **층이 다르다.**

- **Netty** = **네트워크 I/O 엔진**. TCP 소켓 열고 닫기, 바이트 버퍼, 이벤트 루프 스레드, 실제 회선 위에서 바이트 주고받기 → **"선(wire), 바이트"를 다루는 계층**. WebFlux는 기본적으로 Netty 위에서 돌아간다(대체 옵션: Undertow, Servlet 3.1+ Tomcat).
- **Project Reactor** = **비동기 흐름 엔진**. `Publisher`/`Subscriber` 계약, 연산자 체이닝(`.map`/`.flatMap`/`.then`), backpressure 같은 async 합성 로직 → **"흐름(신호)과 구독"을 다루는 계층**.

둘이 겹치는 지점: Binance가 보낸 raw 바이트는 **Netty가 받아서 WebSocket 프레임으로 조립한 뒤**, 그 프레임을 `Flux<WebSocketMessage>`라는 **Reactor 객체에 실어서** 우리 코드로 흘려보낸다. 우리가 `.map()`/`.doOnNext()`로 손대는 건 전부 Reactor 영역의 일이고, 그 아래에서 회선을 굴리는 건 Netty의 일.

그런데 “바이트 → Flux 변환” 다리는 사실 Netty도 Reactor도 아닌, **Reactor Netty**라는 별도 라이브러리가 맡는다. Netty는 자기 콜백(`ChannelHandler` 등)으로 이벤트를 알릴 뿐 `Flux`를 모르고, Project Reactor는 소켓을 모른다 — Reactor Netty가 그 사이를 잇는 어댑터. 즉 엄밀히는 **세 부품**(Netty + Reactor Netty + Project Reactor)이 협업하는 구조고, 이 섹션 제목의 “두 엔진”은 그 둘을 이어주는 어댑터를 생략한 표현.

```javascript
[ WebFlux ]            ← 컨트롤러, 라우팅, WebSocketClient 추상화
[ Reactor Netty ]      ← Netty 콜백을 Mono/Flux로 노출하는 어댑터
[ Project Reactor ]    ← Mono/Flux, .map/.then, backpressure
[ Netty ]              ← TCP 소켓, 이벤트 루프, 바이트 I/O
[ OS / NIC ]           ← 진짜 네트워크
```

### Reactor의 본질: 비동기 값 합성

프로그래밍에서 **합성(composition) = 작은 것 여러 개를 조합해 큰 하나를 만든다**는 뜻 (수학의 `g(f(x))` 함수 합성에서 유래). Reactor 맥락에선 **비동기 값/흐름을 이어붙여 새 비동기 값/흐름을 만드는 것**.

- **단일 흐름에 변환 잇기**: `session.receive().map(...).filter(...).then()`처럼 한 줄짜리 파이프로 줄줄이 변환을 이어붙임
- **여러 비동기 값을 묶기**: `Mono.zip(userMono, productMono).map(...)`처럼 둘 다 도착하면 합쳐 새 값을 만듦

그래서 용어 풀이:

- **합성 연산자** = `.map`, `.flatMap`, `.zip`, `.merge`, `.then`처럼 **Publisher를 받아서 새 Publisher를 만들어내는 메서드들**. 입력도 출력도 Publisher라서 줄줄이 이을 수 있음.
- **비동기 값 합성 라이브러리** = "**아직 도착 안 한 값들**을 콜백 없이 위 연산자로 합치고 변형할 수 있게 해주는 라이브러리" — 콜백 지옥의 대안.

> [!IMPORTANT]
> 📌 **Kafka/Redis pub/sub과의 차이** — 같은 단어지만 카테고리가 다름. 세 가지가 다름:
>
> - **목적**: Reactor는 in-process **비동기 값 합성**, Kafka는 프로세스 간 **메시지 라우팅·디커플링**
> - **관계**: Reactor는 보통 **1:1** (구독마다 새 파이프, cold), Kafka는 **1:N 브로드캐스트**
> - **흐름 제어**: Reactor는 **backpressure가 사양에 내장** (Subscriber가 `request(n)`으로 수요 통제), Kafka는 그런 계약 없음

### `Flux<WebSocketMessage>`로 감싸는 이유

WebSocketMessage는 **언제·몇 건이 올지 모르고, 도중에 끊길 수 있고, 처리 속도를 맞춰야 하는** 데이터. raw 콜백으로 다루면 코드가 복잡해지고 직접 관리할 게 많아짐. Flux로 감싸면 그 복잡함을 라이브러리가 떠안아줌.

| 얻는 것 | 의미 | raw 콜백이라면 |
| --- | --- | --- |
| **타입으로 약속 명시** | `Flux<WebSocketMessage>` 시그니처만 봐도 "0\~∞건이 시간 차이를 두고 흐른다"가 컴파일 타임에 박힘 | 함수 이름·문서에 의존 |
| **합성(composition)** | `.map → .filter → .doOnNext` 줄줄이 이어붙여 한 줄 파이프로 | 콜백 중첩 또는 한 콜백에 다 붙음 |
| **backpressure 자동** | `request(n)` 신호가 위로 전파되어 Netty의 TCP read까지 늦춤 | 직접 큐·플래그 만들어 관리 |
| **에러·완료가 같은 채널** | `.onErrorResume`/`.timeout`/`.doFinally`가 데이터와 같은 파이프에서 동작 | `onMessage`/`onError`/`onClose` 따로 처리 |
| **lazy 평가** | 구독 전엔 아무 일도 안 일어남 → 파이프 재사용·테스트 가능 | 콜백은 등록 즉시 발화 |
| **WebFlux 시그니처와 결합** | `.then()`으로 `Mono<Void>` 만들어 `WebSocketHandler.handle()` 계약(반환 타입 `Mono<Void>` — 세션 종료 시점 신호) 충족 | 종료 시점을 따로 신호화 |

**한 줄 요약**: WebSocketMessage 한 건은 그냥 데이터지만, **시간 흐름 위에 여러 건이 나타나는 순간 그 흐름 자체를 다룰 도구가 필요해진다** — 그 도구가 `Flux<T>`.

> [!NOTE]
> 🛠️ **Flux가 이 복잡함을 떠안을 수 있는 이유 — 진짜 엔진(구현체)이기 때문** — `Flux`는 단순 인터페이스가 아니라 Project Reactor (`reactor-core`)의 **추상 클래스 + 200개 넘는 구체 구현체**(`FluxMap`, `FluxFlatMap`, `FluxPublishOn`, `FluxRetryWhen` 등) 모음. 각 클래스 안에 신호 전파·backpressure 카운팅·동시성 안전·스케줄링 코드가 가득 들어있다. 우리가 `.map(...)` 한 번 호출하면 `FluxMap` 인스턴스가 메모리에 생기고, `.subscribe()`를 호출하면 엔진이 Subscriber 체인을 만들어 신호를 흘려보낸다. **Netty가 바이트 I/O를 위한 진짜 코드를 갖듯, Reactor도 신호·구독 처리를 위한 진짜 코드를 갖는다** — 위 표의 "라이브러리가 떠안아줌"은 추상적 표현이 아니라 실제 동작하는 코드를 가리킨다.

**코드로 보면** (간략화한 의사 코드):

```java
abstract class Flux<T> {
    // ✅ 공통 메서드들 (body 있음)
    public <R> Flux<R> map(Function<T, R> fn) {
        return new FluxMap<>(this, fn);
    }
    public Flux<T> filter(Predicate<T> p) {
        return new FluxFilter<>(this, p);
    }
    // ... 200개 넘는 연산자

    // ❌ 빠진 메서드 — 자식이 구현해야 함
    public abstract void subscribe(CoreSubscriber<? super T> actual);
}

// 자식 클래스가 빠진 subscribe를 채움
class FluxMap<T, R> extends Flux<R> {
    Flux<T> source;
    Function<T, R> mapper;

    public void subscribe(CoreSubscriber<? super R> actual) {
        source.subscribe(new MapSubscriber<>(actual, mapper));
        // ↑ 진짜 동작 코드 (map 전용 로직)
    }
}
```

즉 우리가 코드에서 다루는 `Flux<T>`는 **추상 클래스**(껍데기 + 연산자 목록)이고, `.map(...)`을 호출하는 순간 `FluxMap` 같은 **구체 구현체 인스턴스**가 메모리에 생성된다 — 그 안에 실제 `subscribe()` 처리 로직이 들어있음. 우리는 항상 `Flux<T>` 타입만 보니까 구체 클래스 이름을 알 필요는 없음.

### Reactor는 WebFlux 전용 부품이 아니다

앞에서 Reactor를 "WebFlux의 흐름 엔진"으로 소개했지만, 사실 **Project Reactor는 단독 라이브러리**다. Spring 없는 순수 자바 앱에서도, 배치 잡에서도, 심지어 Spring MVC 컨트롤러에서도 `Mono`/`Flux`만 가져다 쓸 수 있다 (`io.projectreactor:reactor-core` 의존성 한 줄). 내 코드 안에서 `import reactor.core.publisher.Flux`로 가져오는 그 `Flux`가 WebFlux 내부에서 흐르는 그 `Flux`와 **완전히 같은 클래스**다.

그럼 WebFlux와 Reactor는 어떤 관계냐 — **WebFlux는 Reactor 타입을 자기 공개 API의 1급 타입으로 채택했다.** 컨트롤러 시그니처, 라우터 함수, `WebClient`, 필터 체인, 인코더/디코더 — 전부 `Mono`/`Flux`가 1급 타입이다. 흔히 "HTTP 스택 전체를 Reactor 타입으로 박아넣었다"고도 표현한다.

> [!IMPORTANT]
> 📌 **"HTTP 스택 전체를 Reactor 타입으로 박아넣었다"의 진짜 의미** — 컨트롤러 한 곳만 그런 게 아니라 **HTTP 파이프라인 모든 단계** 사이를 흐르는 모든 데이터가 `Mono`/`Flux`로 포장돼 있다는 것.

그래서 차이를 한 줄로 정리하면:

- **Reactor만 쓴다** = "내 코드 안에서 async 흐름 합성하고 싶다" (어디서든 가능)
- **WebFlux 쓴다** = "HTTP 입구부터 출구까지 그 흐름을 끊지 않고 흘리고 싶다" (자체 reactive HTTP 추상화 + Reactor Netty를 묶은 웹 프레임워크)

> [!IMPORTANT]
> 🔒 **WebFlux를 쓰는 동안엔 `Mono`/`Flux`가 사실상 의무** — Reactor가 단독 라이브러리인 건 맞지만, WebFlux의 모든 공개 시그니처가 `Mono`/`Flux`로 박혀있어 **그 프레임 안에서는 강제**다 — 컨트롤러·핸들러 리턴 타입을 `CompletableFuture`나 평범한 객체로 둘 수가 없다. 단 **경계(boundary)에서는 다른 비동기 타입과 변환 가능** — `Mono.fromFuture(...)`로 `CompletableFuture`를 받아 `Mono`로 감싸서 흘리거나, `mono.toFuture()`로 밖으로 내보내는 식. 즉 외부 라이브러리가 `CompletableFuture`를 리턴해도 호출은 가능하지만, WebFlux의 파이프라인을 타는 순간 결국 `Mono`/`Flux`로 포장돼야 한다.

> [!NOTE]
> 🤝 **`Mono`/`Flux`를 쓴다** = "이 값은 나중에 도착한다. 직접 받지 말고 구독해라"는 **비동기 계약을 타입으로 표현하는 것**. 그 약속이 들어간 메서드를 부르는 쪽도 같은 계약을 이어가야 **끝까지 비동기가 유지된다**.

### Spring MVC에서도 Reactor를 쓸 수 있나? → 쓸 수는 있지만 반쪽이다

Spring 5부터 MVC 컨트롤러도 `Mono<T>` / `Flux<T>` 반환 타입을 받아준다. 하지만 결정적 차이가 있다.

- **MVC + Reactor**: Servlet 스레드(블로킹)가 요청을 받음 → 컨트롤러에서 `Mono` 리턴 → 그 시점부터만 비동기 → 결과 나오면 다시 Servlet에 밀어넣음. **엣지(컨트롤러 입출구)에서만 reactive**, 내부 I/O는 여전히 thread-per-request라 동시 연결이 많아지면 스레드 풀이 동난다.
- **WebFlux + Reactor**: Netty 이벤트 루프가 받음 → 디코더부터 컨트롤러, 인코더, 바이트 출력까지 **전부 `Publisher`/`Subscriber`로 흐름** → 끝까지 backpressure 보존.

#### 단계별로 보면

| 단계 | MVC + Reactor | WebFlux + Reactor |
| --- | --- | --- |
| TCP 바이트 수신 | Servlet 스레드 블로킹 read | Netty 이벤트 루프 non-blocking |
| HTTP 파싱 | Tomcat 동기 파싱 | reactive HTTP codec (청크 단위) |
| 요청 바디 디코딩 | `HttpMessageConverter` 동기 (전체 바이트 다 받은 후) | `Decoder` reactive (청크 단위) |
| 컨트롤러 실행 | `Mono` 리턴받고 subscribe 후 대기 | `Mono`가 reactive 체인의 일부 |
| 응답 인코딩 | Jackson 한 방에 `byte[]` 생성 (동기) | `Encoder`가 `Flux<DataBuffer>`로 청크 단위 |
| TCP 송신 | `OutputStream.write()` 블로킹 | non-blocking, backpressure 가능 |
| 스레드 모델 | thread-per-request (요청 1건 = 스레드 1개 점유) | event loop (스레드 몇 개로 수천 연결) |

**위 표의 단계들 사이에서 스레드가 어떻게 행동하는지를 압축하면** — "다음 단계"는 **위 표의 각 행이 하나의 단계**일 때, 한 단계가 끝나고 그 **바로 다음 행의 단계**로 넘어가는 시점을 가리킨다 (예: "TCP 바이트 수신" 단계가 끝나고 "HTTP 파싱" 단계로 넘어가는 사이, "요청 바디 디코딩" 단계가 끝나고 "컨트롤러 실행" 단계로 넘어가는 사이). 그때 다음 단계가 필요로 하는 데이터(다음 TCP 청크, DB 쿼리 결과, 외부 API 응답, TCP 송신 버퍼 공간 등)가 **아직 안 도착한 상황**에서 스레드가 어떻게 행동하느냐가 둘을 가르는 핵심.

| 상황 | MVC | WebFlux |
| --- | --- | --- |
| 다음 단계 데이터가 **딜레이 없이 이미 와있음** | 스레드가 그대로 진행 | 스레드가 그대로 진행 |
| 다음 단계 데이터가 **아직 안 옴 (딜레이)** | 스레드가 그 자리에서 멈춰서 기다림 **(동기)** | 스레드가 다른 일 하러 감 **(비동기)** |

> [!NOTE]
> 💡 **"반쪽"의 의미** — MVC + Mono를 쓰면 **컨트롤러 본문이 비동기로 도는 동안만** Servlet 스레드를 풀로 돌려보낼 수 있다. 그 앞(파싱·바디 디코딩)과 뒤(인코딩·송신)은 여전히 블로킹이라:
>
> - 스레드 풀 절약 효과 제한적
> - backpressure 끝까지 못 흘림 (인코더에서 끊김)
> - 응답 스트리밍 불가능 (한 방에 만들어 보냄)

즉 MVC에서 `Mono`를 리턴하는 건 "쓸 수는 있는데 진짜 이점은 못 살린다"에 가깝다. WebFlux는 그 흐름을 끊지 않고 회선 바이트까지 밀어내려가는 점에서 다르다.

---

## 한눈에 보기: `connect()`의 4시점별 조립 흐름

| 시점 | 무엇이 조립되나 |
| --- | --- |
| 컴파일 | 아무것도 안 됨 (람다 바이트코드만 존재) |
| 앱 부팅 시 `@PostConstruct`로 `connect()` 자동 호출 | **바깥 Mono 2개 조립**: `execute(...)` → `.doOnError(...)` |
| `subscribe()` 호출 → 연결 성공 → 람다 바디 실행 | **안쪽 파이프라인** (`session.receive()...then()`) 조립 |
| 람다 리턴 직후 (프레임워크가 내부 subscribe) | 데이터 신호가 실제로 흐름 |

> [!NOTE]
> 💡 **조립(assembly)** = 연산자 호출 한 번에 새 `Mono`/`Flux` 객체가 메모리에 만들어지는 일 — 아직 데이터는 안 흐르고 파이프라인 설계도만 쌓는 단계.

**"바깥 Mono 2개 조립"** = `webSocketClient.execute(...)`가 호출되어 **`connectionPublisher`**(연결 시도를 품은 cold `Mono<Void>`)가 생성되고, 이어서 `.doOnError(...)`가 그것을 감싼 **`observablePublisher`**(에러 훅이 붙은 `Mono<Void>`)를 생성하는 — 그 두 객체가 만들어지고 변수에 참조가 박히는 그 순간. (이 프로젝트 현재 코드 기준 — 나중에 `.timeout()`/`.retryWhen()` 등이 추가되면 조립 단계가 늘어난다.)

## 먼저: 네 가지 시점을 구분해야 함

Reactor 이해 시 가장 흔한 오해 — "코드를 위에서 아래로 읽으면 그 순서대로 실행된다". 사실은 시점이 네 개로 나뉜다. 특히 `WebSocketHandler` 람다처럼 **조립 코드가 람다 안에 들어있는 경우, "조립" 자체가 바깥·안쪽 두 번으로 쪼개진다.**

**(1) 바깥 조립 시점 (Outer assembly)** — 앱 부팅 시 Spring이 `@PostConstruct`로 `connect()`를 자동 호출하는 순간. 안에서 `client.execute(URI.create(URL), sessionHandler)` 한 줄이 실행되어 **바깥 `Mono<Void>` 객체 `connectionPublisher`** 가 만들어지고, 이어 `.doOnError(...)` 한 줄이 실행되어 그것을 감싼 **`observablePublisher`** 가 만들어진다 — 바깥 Mono 객체 2개가 양파 껍질처럼 포개진 상태. 이때 `sessionHandler`는 그냥 람다 객체 레퍼런스로 들고만 있을 뿐, **람다 바디는 단 한 줄도 실행되지 않는다** — `session.receive()`도, `.map()`도, `.doOnNext()`도, `.then()`도 호출 안 됨. Binance 접속도 없고 `session` 객체도 아직 존재하지 않는다. 이게 "cold"의 의미.

**(2) 구독 시점 (Subscription time)** — 바깥 Mono에 `subscribe()`가 호출되는 순간 — 이게 **Subscribe #1**. (보통은 Spring WebFlux가 컨트롤러 반환값을 보고 알아서 해주지만, 이 프로젝트의 `connect()`는 손으로 직접 호출.) 여기서 비로소 불이 켜진다 (cold → hot). WebSocket 클라이언트가 Binance에 TCP/TLS/WS 핸드셰이크를 시도한다.

**(3) 안쪽 조립 시점 (Inner assembly)** — 핸드셰이크 성공 → **프레임워크(`spring-webflux.jar` 안의 `ReactorNettyWebSocketClient.execute()` 내부 콜백 코드)** 가 `WebSocketSession` 객체를 만들어 `sessionHandler.handle(session)`을 호출 → **그제서야 람다 바디가 열린다 — 즉, 안쪽 파이프라인의 조립이 비로소 시작된다.** 이 시점에 비로소

- `session.receive()` → Flux 1 생성
- `.map(WebSocketMessage::getPayloadAsText)` → Flux 2 생성
- `.doOnNext(log raw)` → Flux 3 생성
- `.doOnNext(logParsedSnapshot)` → Flux 4 생성
- `.then()` → `Mono<Void>` 생성

이 차례로 실행되어 **안쪽 파이프라인 설계도가 조립**된다. 람다는 마지막 `sessionDone`(`Mono<Void>`)을 반환하고, **위의 그 콜백 코드(`ReactorNettyWebSocketClient.execute()` 내부, `spring-webflux.jar`)** 가 리턴된 그 Mono를 받아 내부적으로 subscribe한다. **이 `subscribe()`는 사용자 코드 어디에도 적혀있지 않다 — 사용자는 람다에서 Mono를 리턴하기만 하면, 프레임워크가 그 Mono를 알아서 켜준다.** 이게 **Subscribe #2**.

> [!NOTE]
> 💡 **"프레임워크가 알아서 켜준다"의 메커니즘** — `execute()` 내부가 사용자 람다 리턴 Mono를 reactor-netty의 `handle()`에 흘리고, `handle()`이 그것을 outer Mono와 `flatMap`-스타일로 합성하기 때문. outer 한 번의 subscribe(=Subscribe #1)가 inner subscribe(=Subscribe #2)로 자동 전파된다.

**(4) 런타임 (데이터 흐름)** — Binance가 depth 프레임을 보내기 시작하면서, 데이터가 안쪽 파이프(`receive` → `map` → `doOnNext` raw → `doOnNext` parsed → `then`)를 신호로 타고 흐른다.

**정리: 이 네 시점 안에서 subscribe는 두 번 일어났다** — 같은 단어지만 **다른 두 Mono**에 대한 호출.

```javascript
Subscribe #1: 사용자가 호출
  └─ 대상: observablePublisher (바깥 Mono)
  └─ 효과: 바깥 dormant 로직 깨움 → WebSocket 핸드셰이크 시작

Subscribe #2: 프레임워크가 호출 (사용자 코드엔 안 보임)
  └─ 대상: sessionDone (람다가 리턴한 안쪽 Mono)
  └─ 효과: 안쪽 dormant 파이프라인 깨움 → 데이터 흐름 시작

* Reactor 맥락에서의 dormant = 조립은 됐지만 아직 안 깨어난 상태
```

### 핵심 포인트

- "조립"이라는 단어가 **한 번에 끝나는 한 단계가 아니다.** 바깥 Mono는 `connect()` 호출 시점에 조립되지만, **안쪽 파이프라인의 조립은 subscribe → 핸드셰이크 성공 → 람다 바디 실행 시점까지 미뤄진다.**
- 이유는 단순하다: **안쪽 파이프라인은 `session` 객체 없이는 조립조차 시작할 수 없기 때문.** `session.receive()`의 `session`이 람다 파라미터이므로, 람다가 호출되기 전엔 `session` 자체가 세상에 존재하지 않는다.
- 그래서 `connect()`만 호출하고 반환된 Mono를 버리면(=구독 안 하면) **안쪽은 영원히 조립되지 않는다.** Binance 접속도 없고, 로그도 안 찍히고, 람다 바디도 한 줄도 실행되지 않는다.

## 두 파이프라인은 결국 하나로 연결되나

**런타임에선 하나의 비동기 흐름.** 단, 사용자 코드에서 `.map`/`.flatMap` 같은 Reactor 연산자로 직접 잇진 않음 — **프레임워크(`execute()` 내부 콜백 코드)가 다리 역할**.

- 안쪽 파이프라인의 `sessionDone`을 람다가 리턴
- reactor-netty의 `.handle()` 콜백이 그걸 받아 **"내부 Mono 종료 신호 = 바깥 Mono 종료 신호"** 로 묶음
- 결과: Subscribe #1 → 핸드셰이크 → Subscribe #2 → 메시지 흐름 → (연결 종료) → 종료 신호가 안쪽에서 바깥으로 surfacing → Subscribe #1에 onComplete/onError 도달

개념적으로 **보이지 않는 `.flatMap`이 두 Mono 사이에 끼어있는 셈** — `flatMap`이 평소 하는 일("바깥 결과로 안쪽 Publisher 만들어 신호를 이어가기")을 사용자 대신 프레임워크가 한다.

## 구독 신호는 어떻게 흐르는가 (아래 → 위, upstream)

`subscribe()`를 호출하면 구독 요청이 **파이프의 맨 끝(downstream)에서 시작해서 소스(upstream)를 향해 거슬러 올라간다.**

```javascript
subscribe()
  → observablePublisher (doOnError)
    → connectionPublisher (webSocketClient.execute)
      → 여기서 실제로 URI에 WebSocket 접속 시도!
        → 연결 성공 → 세션 생성 → sessionHandler 람다 실행
          → 람다 안에서 session.receive() 구독
            → Binance 프레임 소스가 진짜로 "켜짐"
```

핵심: `webSocketClient.execute(...)`가 반환한 `Mono<Void>`는 **구독될 때까지 접속을 미룬다.** `subscribe()`가 그 구독을 일으키고, 그 신호가 `execute` 내부까지 전파되어서야 비로소 WebSocket 핸드셰이크가 일어난다.

## 데이터는 어떻게 흐르는가 (위 → 아래, downstream)

세션이 살아 있는 동안 새 depth 업데이트가 도착할 때마다, 데이터는 **소스에서 시작해 각 연산자를 거쳐 아래로** 흘러내린다.

```javascript
session.receive()        // Binance 프레임 한 건 도착
  → WebSocketMessage 방출
    → .map()             // → JSON String 으로 변환
      → .doOnNext()      // raw JSON 로그 (값을 통과시키며 "엿보기")
        → .doOnNext()    // 파싱 결과 로그 (역시 통과)
          → .then()      // 값은 버리고 onComplete/onError 신호만 통과
            → subscribe() // 종착점 (값 안 받음, 완료/에러만)
```

`.doOnNext()`는 데이터를 **소비하지 않고 옆에서 들여다보기만** 한다("엿보기"). 그래서 데이터는 변형 없이 다음 단계로 그대로 전달된다.

`.then()`이 중요한데, 흘러온 `String` 값들을 전부 버리고 **스트림이 끝났다는 신호(완료/에러)만** 아래로 보낸다. 그래서 `subscribe()`는 개별 depth 데이터를 받지 않고 "연결이 끝났는가/실패했는가"만 알게 된다. 실제 데이터 처리(로그)는 중간의 `doOnNext` 안에서 이미 다 끝난 것.

## 정리: 핵심 비대칭

두 흐름의 방향이 정반대다.

| 흐름 | 방향 | 방아쇠 / 내용 |
| --- | --- | --- |
| 구독 신호 | 아래 → 위 (upstream) | `subscribe()`가 방아쇠. 신호가 위로 올라가 `execute`를 깨우고 실제 접속 발생. cold → hot 전환의 본질 |
| 데이터 | 위 → 아래 (downstream) | 접속 후 Binance 프레임이 소스에서 출발해 `map → doOnNext → doOnNext → then` 순서로 내려오며 변환·로깅됨 |

## 추가로 알아두면 좋은 것

- **`.then()`의 역할** — `WebSocketHandler.handle(session)`은 사용자가 직접 호출하지 않고 **프레임워크가 핸드셰이크 성공 시점에 자동으로 콜백**하는데, 그 시그니쳐가 `Mono<Void> handle(...)`로 박혀있어 람다도 `Mono<Void>`를 돌려줘야 한다. `.then()` 없이 `Flux<String>`을 그대로 두면 반환 타입이 `WebSocketHandler`가 요구하는 `Mono<Void>`와 안 맞는다. `.then()`은 "값엔 관심 없고 언제 끝나고 에러 나는지만 신경 쓴다"는 의미라 세션 핸들러의 생명주기 관리용으로 적합.
- **구독은 cold·1회성** — 이 프로젝트의 `connect()`는 내부에서 직접 `observablePublisher.subscribe()`를 호출하는 구조(반환 타입 `void`, '내부 subscribe' 스타일). 그래서 **`connect()`를 두 번 호출하면** 내부 `subscribe()`가 두 번 발사되어 **동시에 살아있는 WebSocket 연결이 두 개** 생긴다. 재연결(끊고 새로 잇기)이 아니라 같은 시간대에 나란히 살아있는 거라, Binance가 같은 depth 스트림을 두 번 보내 **트래픽·로그가 두 배**. `execute` Mono가 cold라서 구독마다 새 연결을 맺기 때문. (Binance 스트림 자체는 hot source.)
- **Binance WebSocket이 동시에 여러 개 살아있는 것을 막으려면: upstream 1개 + downstream N개 분리** — `connect()`를 사용자 행동에서 완전히 뗼어낸다 — **앱 시작 시점에 1회만** 호출해 Binance WebSocket 1개를 영원히 유지하고, 사용자 N명에게는 그 결과를 `latestStore`로 fan-out한다. 그러면 사용자가 100명이든 1000명이든 Binance 연결은 1개로 고정.

```plain text
[앱 시작 시점, 1회]
  └─ connect() ────────> Binance WebSocket 1개 (이후 영원히 유지)
                              ↓
                          [latestStore]
                              ↑
[사용자 1000명이 페이지 열 때마다]
  └─ SSE /stream  ──> store에서 읽기  (1000개 SSE 연결, 1개 Binance 연결)
```

`@PostConstruct`로 `connect()`를 부팅 시 1회 자동 호출하도록 분리했고, POST `/start` 엔드포인트는 주석 처리해 비활성화했다 (학습 흔적으로 코드에 남겨둔 상태).

> [!TIP]
> ➡️ **다음에 볼 만한 것**: backpressure(소스가 너무 빨리 보낼 때 흐름 제어), 재연결(`retry`, `retryWhen`).
