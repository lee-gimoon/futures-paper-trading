# 공부 노트: Spring MVC vs Spring WebFlux

작성일: 2026-05-15

Spring Boot로 웹 서버를 만들 때 고를 수 있는 길은 사실상 **딱 2개**다.
이 노트는 그 두 길의 차이를 머릿속에 그림으로 박아두기 위한 정리.

---

## 한 줄 요약

```text
Spring MVC    = 동기 / Blocking  / 요청 1개 = 스레드 1개      / 기본 서버 Tomcat
Spring WebFlux = 비동기 / Non-blocking / 적은 스레드로 많은 연결 / 기본 서버 Reactor Netty
```

두 줄을 보면 "이 프로젝트에 어느 쪽이 맞나"가 거의 결정된다.

---

## 용어 한 줄 정리

스레드와 이벤트 루프를 보면 머릿속에 그냥 이 이미지가 떠오르면 된다.

- **스레드** — OS가 만들어주는 "한 줄로 일하는 작업자". 한 시점에 코드 한 줄씩 순서대로 실행한다. 받은 일이 도중에 멈추면(예: DB 응답 대기) 그 작업자는 거기 묶여서 같이 기다린다.
- **이벤트 루프** — 스레드 위에서 도는 무한 반복문. "처리할 이벤트 있나?" → 있으면 짧게 처리하고 즉시 다음 이벤트로 → 없으면 다시 묻기. 기다리는 일은 절대 떠안지 않고 OS에 맡긴다.

비유로는 스레드가 **책상 앞의 작업자 1명**(받은 일이 끝날 때까지 그 자리에서 처리),
이벤트 루프가 **그 작업자가 "다음 일감!" 외치며 큐(queue)를 끝없이 도는 행동 패턴**에 가깝다.

그래서 그림에 나오는 "Event Loop 1"은 자원의 단위가 아니라 행동의 이름이고,
실체는 **그걸 굴리는 OS 스레드 1개**다.

핵심 한 줄: **이벤트 루프 1개 = 스레드 1개 위에서 도는 무한 반복 패턴.**
Reactor Netty는 보통 CPU 코어 수만큼(8코어면 8개) 이벤트 루프 = 스레드를 띄운다.

---

## 같은 요청을 두 프레임워크가 어떻게 처리하나

같은 5개 요청을 두 프레임워크가 어떻게 다루는지 한 그림에 펼쳐 본 것.

![MVC vs WebFlux 요청 처리 방식 비교](images/mvc-vs-webflux-request-handling.svg)

**눈으로 봐야 할 포인트:**

- **MVC 쪽 (위)**: 빨간 줄무늬(BLOCKED)가 **스레드 위에** 깔린다. 스레드는 "여기 앉아서 응답을 기다리는 것" 외에 아무 일도 못 한다. 5 요청 = 5 스레드가 통째로 묶여 있음.
- **WebFlux 쪽 (아래)**: 청록 줄무늬(I/O 대기)가 **스레드 밖, OS 영역에** 깔린다. Event Loop는 받자마자 OS에 던져두고 다음 요청으로 즉시 이동. 응답이 준비되면 그때 다시 잡아서 내려준다.
- **동일한 물리적 대기 시간**(R1의 DB 응답이 준비되는 데 걸리는 절대 시간)은 두 그림이 같다. 차이는 **그 대기를 누가 떠안는가** — MVC는 스레드가, WebFlux는 OS가.
- 그래서 100 요청, 1000 요청으로 늘어나면: MVC는 스레드 풀이 바닥나고, WebFlux는 같은 2 스레드가 그대로 처리한다.

---

## 비유로 먼저

식당 비유가 가장 직관적이다.

- **Spring MVC = 1인 1테이블 웨이터**
  손님이 오면 웨이터 한 명이 그 테이블에 붙는다. 음식이 나올 때까지 옆에 서서 기다린다. 손님이 100명이면 웨이터도 100명 필요.
  → **단순하고 직관적이지만, 대기 시간이 긴 손님이 많으면 인력이 빨리 동난다.**

- **Spring WebFlux = 회전초밥 / 주방 호출벨**
  웨이터 몇 명이 홀 전체를 돌면서 "지금 음식 나온 테이블"만 골라 서빙한다. 음식 기다리는 동안 다른 테이블을 본다.
  → **소수의 웨이터로 많은 손님을 본다. 대신 시스템(주방·호출벨·번호표)을 미리 갖춰야 한다.**

코드 복잡도와 처리량의 트레이드오프가 이 비유에 다 들어있다.

---

## 표로 비교

| 항목 | **Spring MVC** | **Spring WebFlux** |
|---|---|---|
| Spring Boot 스타터 | `spring-boot-starter-web` | `spring-boot-starter-webflux` |
| 처리 모델 | 동기 / Blocking | 비동기 / Non-blocking |
| 스레드 모델 | 요청 1개 = 스레드 1개 점유 | 이벤트 루프 + 적은 스레드 |
| 기본 내장 서버 | Tomcat (Jetty/Undertow 가능) | Reactor Netty (Tomcat/Jetty/Undertow 가능) |
| 메서드 반환 타입 | `User`, `List<User>` 등 일반 객체 | `Mono<User>`, `Flux<User>` |
| API 호출 | `RestTemplate` (deprecated 권고), `WebClient` 도 가능 | `WebClient` (전용) |
| DB 접근 | JPA, JDBC (대부분 blocking) | R2DBC, MongoDB Reactive 등 reactive 드라이버 |
| 컨트롤러 스타일 | `@RestController` + `@GetMapping` | 같은 어노테이션 + 함수형 라우팅도 가능 |
| 등장 시기 | Spring 1.0부터 (전통의 기본) | Spring 5 (2017)부터 |
| 학습 난이도 | 낮음 (자바답게 흘러간다) | 높음 (Reactor 사고방식 필요) |
| 디버깅 | 스택 트레이스가 직관적 | 스택 트레이스가 길고 끊긴 듯 보임 |

---

## 코드 비교

### 같은 일을 두 스타일로

**Spring MVC** — 평범한 자바 메서드:

```java
@RestController
public class PriceController {

    @GetMapping("/price")
    public Price getPrice() {
        Price p = binanceApi.fetchPrice();   // 끝날 때까지 이 스레드는 여기서 대기
        return p;
    }
}
```

**Spring WebFlux** — `Mono` 반환:

```java
@RestController
public class PriceController {

    @GetMapping("/price")
    public Mono<Price> getPrice() {
        return binanceApi.fetchPrice();      // "끝나면 알려줘", 스레드는 다른 일 하러 감
    }
}
```

겉모양은 비슷한데, **반환 타입이 `Price`냐 `Mono<Price>`냐**가 핵심 차이.

### 스트리밍은 차이가 더 도드라진다

WebFlux는 "여러 개가 시간 차로 흘러나오는 데이터"에 강하다.

```java
@GetMapping(value = "/btcusdt/depth/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<Depth> stream() {
    return depthStreamer.flux();   // 100ms마다 한 개씩, 끊지 않고 계속 흘려보냄
}
```

MVC로 같은 걸 하려면 SSE/스레드/큐 직접 다뤄야 해서 코드가 훨씬 무거워진다.

---

## 둘 중 어느 길을 가나

### MVC가 맞는 경우 — 사실 90%의 일반 웹앱

- CRUD 위주, JPA + 관계형 DB
- 요청-응답이 명확하게 끝나는 평범한 REST API
- 팀이 리액티브에 익숙하지 않다
- 외부 의존성도 대부분 blocking 드라이버 (대다수 RDBMS, 대다수 SDK)

→ **고민하지 말고 MVC 쓰자.** 대부분의 사이드 프로젝트와 사내 시스템은 여기에 해당.

### WebFlux가 맞는 경우

- **장시간 연결**이 많음 (WebSocket, SSE, 시세 스트리밍, 채팅)
- 외부 API를 **동시에 여러 개** 호출해서 합치는 게 핵심
- 트래픽은 많은데 각 요청이 짧고 가벼움 (실시간 알림, gateway, 시세 분배)
- 데이터가 "한 번에 다 오는 게 아니라 흘러오는" 도메인

→ **우리 프로젝트가 정확히 이쪽.**

---

## 우리는 왜 WebFlux를 골랐나

이 프로젝트의 본질은 "Binance에서 호가창이 100ms마다 흘러들어오는 걸 받아서, 가공해서, 또 흘려보내는" 일이다. 즉:

- WebSocket 연결은 **계속 열려있다**. MVC + Tomcat이면 연결당 스레드 1개가 메시지 기다리며 점유된다.
- 100개 심볼을 구독하면 → 스레드 100개가 그냥 대기 상태.
- WebFlux + Reactor Netty면 → 이벤트 루프 스레드 몇 개가 모든 연결을 다중 처리.

게다가 [stage-1.md](stage-1.md)의 [BinanceFuturesRawDepthStreamer.java](../src/main/java/com/example/futurespapertrading/market/BinanceFuturesRawDepthStreamer.java)에서 보듯이, 메시지가 자연스럽게 `Flux<WebSocketMessage>`로 들어와서 `.map().filter()` 같은 연산자로 변환·집계가 직관적이다.

호가창 → 체결 → 포지션 관리로 가면서도 **"흐르는 데이터"** 사고방식이 계속 살아있어야 해서 WebFlux가 적합하다.

---

## 자주 헷갈리는 점

### Q1. WebFlux 쓰면 MVC 못 쓰나?

**한 프로젝트에 둘 중 하나만** 쓰는 게 원칙.
`spring-boot-starter-web`과 `spring-boot-starter-webflux`를 동시에 의존성에 넣으면 Spring Boot가 **MVC를 우선 선택**해버려서 WebFlux 의도가 깨진다.

→ 우리 프로젝트는 `spring-boot-starter-webflux` 하나로 간다.

### Q2. WebFlux면 무조건 빠른가?

아니다. 요청당 처리 시간 자체는 비슷하거나 오히려 느릴 수 있다.
WebFlux의 강점은 **"동일한 자원으로 더 많은 동시 연결을 버틴다"**는 것. 1초에 처리하는 요청 수의 절대값이 아니라 **자원 효율**이 다르다.

### Q3. 그럼 작은 앱에 WebFlux 쓰면 손해인가?

손해까진 아니지만 **얻는 게 없을 수 있다.** 동시 연결이 적고 모든 호출이 짧은 blocking이라면, 코드 복잡도만 늘고 처리량 이득은 거의 없다.
"WebFlux를 쓸 이유"가 없으면 MVC가 무조건 정답.

### Q4. WebFlux에서 JPA 쓰면 안 되나?

쓸 수는 있는데 **권하지 않는다.** JPA는 본질적으로 blocking이라 WebFlux의 이벤트 루프 스레드를 막아버린다. 굳이 쓰려면 별도 스레드 풀(`Schedulers.boundedElastic()`)로 격리해야 하는데, 그러면 WebFlux를 쓰는 의미가 절반쯤 사라진다.

→ WebFlux를 쓸 거면 **R2DBC** 같은 reactive 드라이버를 같이 가는 게 정석.

### Q5. 디버깅이 어렵다는 게 무슨 뜻?

MVC는 메서드가 위에서 아래로 실행돼서 스택 트레이스가 그대로 콜 체인을 보여준다.
WebFlux는 `.map().filter().flatMap()`이 **나중에 다른 스레드에서 실행**되기 때문에, 에러가 나면 스택 트레이스에 정작 내가 짠 코드가 잘 안 보이고 Reactor 내부 클래스만 잔뜩 찍힌다.

→ 익숙해지기 전엔 `Hooks.onOperatorDebug()` 같은 도움 장치를 켜둬야 한다.

---

## 그 외 참고 (지금은 신경 안 써도 됨)

- **Spring Cloud Gateway** — WebFlux 기반의 API 게이트웨이. 마이크로서비스 입구로 쓰임.
- **Spring Boot CLI / Batch / 데이터 잡** — 웹 서버가 아예 없는 콘솔/배치 앱도 가능. "MVC vs WebFlux"는 웹 서버를 만들 때만 고민하는 주제.
- **Spring 6 / Boot 3의 Virtual Threads** — Java 21+에서 MVC 코드를 거의 그대로 두면서도 Tomcat이 가상 스레드로 돌아가게 할 수 있다. "MVC는 무조건 자원 비효율"이라는 통념이 흔들리는 중. 단, 본질적으로 stream을 다루는 도메인은 여전히 WebFlux의 사고방식이 더 잘 맞는다.

---

## 머릿속에 남길 그림 한 장

```text
            요청이 짧고 끝이 명확한가?
                    │
        ┌───────────┴───────────┐
       YES                      NO (스트림/장시간 연결/대량 동시성)
        │                       │
   Spring MVC              Spring WebFlux
   (대부분의 웹앱)          (이 프로젝트)
```

이 두 갈래만 기억해두면 충분하다.
