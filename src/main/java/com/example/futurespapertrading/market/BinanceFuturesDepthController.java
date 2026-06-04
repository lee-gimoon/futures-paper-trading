package com.example.futurespapertrading.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

// HTTP로 Binance Futures BTCUSDT 호가 스트림을 시작하거나, 메모리에 보관된 최신 snapshot을 조회한다.
// 2단계까지: POST로 raw 스트림 시작 (메시지는 로그로 흘려보내고 사라짐).
// 3단계 추가: GET으로 현재 메모리에 박혀있는 최신 OrderBookSnapshot 조회.
@RestController
public class BinanceFuturesDepthController {

	private static final Logger log = LoggerFactory.getLogger(BinanceFuturesDepthController.class);

	// 실제 Binance WebSocket 연결과 raw JSON 로그 출력을 담당하는 객체다.
	// 옛 start() 엔드포인트(아래 주석)와 함께만 쓰이던 필드. 주석을 풀면 같이 복구한다.
	// private final BinanceFuturesRawDepthStreamer rawDepthStreamer;

	// 3단계: 파서가 만든 최신 snapshot이 담겨있는 메모리 양동이.
	private final LatestOrderBookSnapshotStore latestStore;

	public BinanceFuturesDepthController(
			// BinanceFuturesRawDepthStreamer rawDepthStreamer,
			LatestOrderBookSnapshotStore latestStore) {
		// this.rawDepthStreamer = rawDepthStreamer;
		this.latestStore = latestStore;
	}

	// ⚠️ 학습용으로 남겨둔 옛 엔드포인트 — 실제로는 호출되지 않도록 주석 처리.
	// BinanceFuturesRawDepthStreamer.connect()에 @PostConstruct를 붙여 부팅 시 1회 자동 호출하도록 옮겼다.
	//
	// 이 코드가 살아있던 시절의 문제점:
	//   1) connect()가 호출될 때마다 내부에서 observablePublisher.subscribe()가 발사됨.
	//      execute Mono가 cold라서 구독마다 새 Binance WebSocket을 맺는다 (재연결이 아니라 같은 시간대에 나란히 살아있음).
	//   2) 이 POST에 가드가 전혀 없음 → 두 번 호출되면 동시에 살아있는 Binance WebSocket이 2개,
	//      100번 호출되면 100개. Binance가 같은 BTCUSDT depth 스트림을 그만큼 N번 보내 트래픽·로그·CPU가 N배.
	//   3) 프론트엔드 "스트림 시작" 버튼에 묶여있어, 사용자가 페이지를 새로고침하거나 새 탭에서 또 누르면
	//      서버 쪽 React state(disabled)는 그 사실을 모르므로 또 발사됨.
	//   4) 근본 원인: upstream(서버↔Binance, 단 1개여야 함)을 downstream(사용자↔서버, N개 OK)과
	//      같은 트리거(사용자 행동)에 묶은 것. → connect()를 사용자 행동에서 떼어내는 게 정답.
	//
	// curl -X POST http://localhost:8080/api/binance-futures/btcusdt/depth/raw/start
	// @PostMapping("/api/binance-futures/btcusdt/depth/raw/start")
	// public String start() {
	// 	rawDepthStreamer.connect();
	// 	return "Binance Futures BTCUSDT raw depth stream started.";
	// }

	// Jackson이 record를 자동 직렬화 — 필드 이름이 그대로 JSON 키가 됨.
	// 스트림이 아직 안 켜졌거나 첫 메시지 전이면 store가 비어있으므로 404를 돌려준다.
	// curl -i http://localhost:8080/api/binance-futures/btcusdt/depth/latest
	//
	// ★ JSON 직렬화는 이 함수 안 어느 줄에서도 일어나지 않는다 — return 이후 Spring 영역에서 자동으로 일어난다.
	//   트리거는 위 @RestController 한 줄: "이 클래스의 모든 메서드 반환값을 HTTP 응답 본문으로 직렬화하라"는 약속.
	//   그 약속 덕분에 함수가 ResponseEntity<OrderBookSnapshot>을 반환만 하면, Spring이 받아서
	//   MappingJackson2HttpMessageConverter를 골라 호출 → 그 컨버터가 내부적으로 ObjectMapper 빈
	//   (2단계 Parser가 readTree에 쓰는 그 객체와 동일한 싱글톤)의 writeValue...을 호출해 JSON 바이트로 변환한다.
	//   record의 자동 생성된 접근자(symbol(), eventTime(), bids(), asks())를 Jackson이 리플렉션으로 찾아내
	//   메서드 이름을 그대로 JSON 키로 쓴다 → {"symbol":"BTCUSDT","eventTime":...,"bids":[...],"asks":[...]}.
	//   우리가 JSON 변환 코드를 한 줄도 안 적은 이유 = @RestController + 객체 반환 = Spring의 컨벤션.
	//
	// 현재 React 홈페이지는 이 엔드포인트를 자동 호출하지 않는다.
	// 화면은 /depth/stream SSE를 구독하고, latest()는 curl/브라우저 직접 호출로 최신 snapshot 1개를 확인하는 디버깅용 단발 조회 API다.
	@GetMapping("/api/binance-futures/btcusdt/depth/latest")
	public ResponseEntity<OrderBookSnapshot> latest() {
		return latestStore.latest()
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build()); // build(): 지금까지 체이닝으로 설정한 값들을 바탕으로 실제 객체를 완성한다.
	}

	// 4단계 — SSE(Server-Sent Events)로 latest snapshot을 브라우저에 push.
	// 구현 방식: 폴링(A). 100ms마다 store를 들여다보고, eventTime이 바뀌었으면 보낸다.
	// 6/7단계에서 Store에 Sinks.Many를 박고 store.stream()으로 갈아끼울 예정 (roadmap.md 참고).
	//
	// produces = TEXT_EVENT_STREAM_VALUE 가 있어야 응답 Content-Type이 "text/event-stream"이 되고,
	// 브라우저의 EventSource가 한 줄(`data: ...\n\n`)씩 받아서 onmessage로 풀어 쓴다.
	//
	// 반환 타입 Flux<ServerSentEvent<OrderBookSnapshot>>:
	//   - Flux = "0개 이상의 값을 시간에 걸쳐 흘려보낼 약속". 한 번 return 후 Spring이 알아서 끝까지 흘려보낸다.
	//   - ServerSentEvent<T> = SSE의 한 이벤트 한 덩어리 (data, id, event 이름 등을 담는 봉투).
	//
	// curl -N http://localhost:8080/api/binance-futures/btcusdt/depth/stream
	// 홈페이지 접속 시 React가 EventSource('/api/.../depth/stream')를 생성해서 이 메서드가 자동 호출된다.
	// 변경 전 ─ Cold Flux + 폴링 (비교용으로 남겨둠)
	// @GetMapping(path = "/api/binance-futures/btcusdt/depth/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	// public Flux<ServerSentEvent<OrderBookSnapshot>> stream() {
	// 	return Flux.interval(Duration.ofMillis(100)) // 100ms마다 째깍 (0,1,2,...)
	// 			.concatMap(tick -> Mono.justOrEmpty(latestStore.latest())) // 양동이가 비었으면 그 tick을 통째로 건너뛴다
	// 			.distinctUntilChanged(OrderBookSnapshot::eventTime) // 직전과 같은 snapshot이면 보내지 않는다 (시계 어긋남 보정)
	// 			.map(snap -> ServerSentEvent.builder(snap).build()); // SSE 봉투로 감싼다 (data: {...json...})
	// }

	// 변경 후 ─ Hot Flux + multicast + replay(1)
	@GetMapping(path = "/api/binance-futures/btcusdt/depth/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<OrderBookSnapshot>> stream() {
		log.info("[STEP10-stream.enter] thread={}", Thread.currentThread().getName());
		return latestStore.stream()
				.map(snap -> {
					log.info("[STEP12-sse.map] thread={}", Thread.currentThread().getName());
					return ServerSentEvent.builder(snap).build();
				});
	}
	// stream() 메서드 메커니즘 정리:
	// - 브라우저가 EventSource로 SSE 연결을 열면 이 stream() 메서드는 연결 1개당 보통 1번 호출된다.
	// - stream()은 Flux 파이프라인을 만들어 return하고, 메서드 콜스택 자체는 바로 끝난다.
	// - 하지만 WebFlux가 반환된 Flux를 구독하므로 Flux subscription과 HTTP SSE connection은 열린 채로 살아 있다.
	// - 여기서 subscription은 WebFlux가 이 Flux를 subscribe해서 만든 "구독 관계"다.
	// - 이후 Binance snapshot이 들어올 때마다 다시 호출되는 것은 stream() 메서드가 아니라 위의 map 람다다.
	// - 즉 [STEP10-stream.enter]는 브라우저 연결 1개당 1번, [STEP12-sse.map]은 snapshot emit 때마다 찍힌다.
	// - 브라우저 탭을 닫거나 EventSource가 끊기면 해당 Flux subscription이 cancel되고 SSE connection도 정리된다.

	// 이 SSE 응답도 Netty/Reactor Netty의 event loop + non-blocking I/O 모델 위에서 처리된다.
	// - Netty는 socket을 non-blocking 모드로 등록하고, selector/epoll/kqueue로 준비된 socket 이벤트만 처리한다.
	// - SSE connection은 오래 열려 있지만, event loop 스레드가 연결마다 하나씩 붙어 기다리는 것은 아니다.
	// - Binance snapshot이 emit되면 WebFlux/Reactor Netty가 브라우저 socket에 non-blocking write를 시도한다.
	// - 당장 쓸 수 있는 만큼만 쓰고, 더 못 쓰면 기다리지 않고 빠진 뒤 나중에 writable 이벤트에서 이어서 처리한다.
	// - 그래서 많은 브라우저 SSE connection을 적은 수의 event loop 스레드로 다룰 수 있다.
	// - 단, 이 흐름 위에서 blocking 작업을 하면 같은 event loop가 맡은 다른 connection 처리도 같이 늦어진다.

	// Tomcat blocking MVC로 SSE를 단순 구현하면:
	// - SSE 요청 1번은 브라우저가 연결을 유지하는 동안 끝나지 않는 응답이 된다.
	// - Controller 안에서 while + queue.take() + response.write() 같은 blocking loop를 돌리면 worker thread가 return되지 않는다.
	// - Binance snapshot이 100ms마다 온다면, 그 100ms 대기 시간에도 worker thread는 queue.take() 등에서 묶여 있다.
	// - CPU를 계속 쓰는 것은 아니지만, 해당 Tomcat worker thread 자원은 다른 요청을 처리하도록 반환되지 않는다.
	// - 브라우저 SSE connection이 100개면 이런 blocking worker thread도 100개 묶일 수 있다.

	// 이와 달리 WebFlux/Reactor Netty는 event loop + non-blocking I/O로 이 요청별 worker thread 대기 문제를 피한다.
	// - 대기 시간에는 SSE connection과 Flux subscription 상태만 유지되고, event loop 스레드가 연결마다 붙어 있지 않다.
	// - Binance snapshot이 emit되어 실제로 브라우저에 쓸 데이터가 생긴 순간에만 event loop가 잠깐 write 흐름을 처리한다.
	// - 즉 연결 수는 많아도 "연결 수만큼 스레드가 기다리는 구조"가 아니라, 준비된 socket 이벤트만 짧게 처리하는 구조다.

}
