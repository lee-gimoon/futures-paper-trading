package com.example.futurespapertrading.market;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

// Binance Futures BTCUSDT partial depth WebSocket에 연결하고 받은 raw JSON을 로그로 출력한다.
@Component
public class BinanceFuturesRawDepthStreamer {

	// 이 클래스 이름으로 로그를 남기기 위한 SLF4J Logger다.
	private static final Logger log = LoggerFactory.getLogger(BinanceFuturesRawDepthStreamer.class);

	// BTCUSDT 상위 20개 bid/ask를 100ms 단위로 받는 Binance USDⓈ-M Futures stream 주소다.
	private static final URI BTCUSDT_DEPTH_STREAM_URI =
			URI.create("wss://fstream.binance.com/ws/btcusdt@depth20@100ms"); // URI 객체 = “이 글자는 주소다”라는 의미와 기능이 붙은 객체

	// WebSocketClient(인터페이스)와 ReactorNettyWebSocketClient(구현체) — 둘 다 spring-webflux.jar 코드.
	// 구현체 내부에서 reactor-netty.jar의 HttpClient.websocket()을 호출해 실제 TCP/WebSocket 연결을 맺는다.
	private final WebSocketClient webSocketClient = new ReactorNettyWebSocketClient();

	// 2단계: raw JSON에서 bids/asks를 뽑아 OrderBookSnapshot으로 만드는 파서.
	private final OrderBookSnapshotParser snapshotParser;

	// 3단계: 파싱한 snapshot을 메모리에 붙잡아두는 양동이. 흐름 직후 여기에 박아둬야
	// HTTP 쪽에서 "지금 호가창 어때?"에 답할 수 있다 (그 외엔 GC가 즉시 회수).
	private final LatestOrderBookSnapshotStore latestStore;

	public BinanceFuturesRawDepthStreamer(
			OrderBookSnapshotParser snapshotParser,
			LatestOrderBookSnapshotStore latestStore) {
		this.snapshotParser = snapshotParser;
		this.latestStore = latestStore;
		log.info("[STEP3-ctor.Streamer] thread={}", Thread.currentThread().getName());
	}

	// Binance WebSocket에 연결하고, 들어오는 메시지를 그대로 로그에 남긴다.
	// @PostConstruct: Spring이 빈 생성 + 의존성 주입 직후 1회만 자동 호출.
	// → Binance WebSocket 1개를 부팅 시점에 확정한다.
	// → 사용자 요청 수와 무관하게 Binance WebSocket을 정확히 1개만 유지하기 위함.
	@PostConstruct
	public void connect() {
		log.info("[STEP4-connect.enter] thread={}", Thread.currentThread().getName());
		log.info("Connecting to Binance Futures depth stream: {}", BTCUSDT_DEPTH_STREAM_URI);

		// [1] 세션 핸들러: session 받으면 어떻게 처리할지 정의 (subscribe 전엔 실행 X, cold)
		WebSocketHandler sessionHandler = session -> {
			log.info("[STEP5-sessionHandler.enter] thread={}", Thread.currentThread().getName());

			// 어댑터: Binance WebSocket 프레임 → Flux<WebSocketMessage>
			//   역할: Netty의 저수준 WebSocket 프레임(바이트)을 Spring의 reactive 타입(WebSocketMessage)으로 감싸는 변환층.
			//   - 이 줄(receive() 호출) 시점: cold Flux 객체 하나만 만들어 리턴. 네트워크에서 아무것도 끌어오지 않음.
			//   - 나중에 subscribe 신호가 이 Flux까지 거슬러 올라오면: 그 신호가 reactor-netty의 inbound 채널로 전파되어
			//     Netty가 WebSocket 프레임을 읽기 시작 → 각 프레임을 WebSocketMessage 객체로 래핑해서 downstream으로 push.
			Flux<WebSocketMessage> incomingMessages = session.receive();

			// 변환: WebSocketMessage → JSON 문자열
			Flux<String> payloadTexts = incomingMessages
					.map(WebSocketMessage::getPayloadAsText);

			// 엿보기: raw JSON 로그
			Flux<String> withRawLog = payloadTexts
					.doOnNext(message -> log.info("[STEP6-rawJson] thread={} len={}",
							Thread.currentThread().getName(), message.length()));

			// 엿보기 + 저장: raw JSON을 파싱해 latestStore에 저장하고, 파싱 결과 로그도 남긴다.
			Flux<String> withParsedLog = withRawLog
					.doOnNext(this::logParsedSnapshot);

			// Flux<String> → Mono<Void>: 값은 버리고 완료/에러 신호만 전달 (handle() 반환 타입 맞춤용).
			// 그 신호가 오기 전까지는 메시지가 도착할 때마다 위쪽 파이프(map → doOnNext → doOnNext)가 한 번씩 흘러간다.
			Mono<Void> sessionDone = withParsedLog.then();

			return sessionDone;
		};

		// [2] 연결 Publisher: Mono<Void> 자체가 Reactive Streams Publisher다.
		// - Publisher = 누군가 subscribe()하면 신호를 흘려보낼 수 있는 비동기 흐름.
		// - 여기서는 Mono<Void>라서 Binance 메시지를 값으로 계속 발행한다는 뜻이 아니다.
		// - 이 Mono<Void>는 WebSocket 연결 시작부터 종료/에러까지의 생명주기를 표현한다.
		// - webSocketClient는 WebSocketClient 인터페이스이고, 실제 구현체는 ReactorNettyWebSocketClient다.
		// - 그래서 이 Publisher가 subscribe되면 실제 socket connect/read/write 같은 네트워크 I/O는
		//   Reactor Netty/Netty event loop를 통해 처리된다.
		// - subscribe 시 URI 접속 → 세션(session) 생성 → sessionHandler 호출.
		Mono<Void> connectionPublisher =
				webSocketClient.execute(BTCUSDT_DEPTH_STREAM_URI, sessionHandler);

		// [3] 에러 훅 추가 (.doOnError = Mono 의 "에러 엿보기")
		Mono<Void> observablePublisher = connectionPublisher
				.doOnError(error -> log.warn("Binance Futures depth stream failed", error));

		// [4] subscribe = 여기서 실제 연결 시작 (cold → hot)
		observablePublisher.subscribe();
		log.info("[STEP4-connect.exit ] thread={} (즉시 리턴)", Thread.currentThread().getName());
	}

	// raw JSON을 파싱해서 latestStore에 저장하고 OrderBookSnapshot을 로그로 찍는다.
	// 파싱이 실패해도 raw 스트림 전체를 죽이지 않도록 여기서 예외를 잡아 로그만 남긴다.
	private void logParsedSnapshot(String message) {
		try {
			OrderBookSnapshot snapshot = snapshotParser.parse(message);
			// 3단계: snapshot이 메모리에 존재하는 "이 순간"에 store에 박아둔다.
			// 이 try 블록이 끝나면 지역변수 참조가 끊겨 GC 대상이 되므로, 여기서 안 잡아두면 영원히 사라진다.
			latestStore.update(snapshot);
			// {} 자리에 SLF4J가 snapshot.toString()을 자동 호출해 끼워 넣는다.
			// record가 toString을 자동 생성하므로 안의 List/OrderBookLevel까지 재귀적으로 펼쳐져 한 줄로 찍힌다.
			log.info("[STEP6-parsedSnapshot] thread={} eventTime={}",
					Thread.currentThread().getName(), snapshot.eventTime());
		} catch (Exception e) {
			log.warn("Failed to parse depth JSON: {}", message, e);
		}
	}
}

// 람다식을 쓰는 이유:
//   목적(왜): 나중에 실행할 동작을 값처럼 넘기기 위해.
//   자바의 우회(어떻게): 함수가 1급 시민이 아니라서, "동작 1개짜리 인터페이스의 1회용 구현체 객체"를
//                      즉석에서 만들어 넘기는 방식으로 실현. 람다(->)는 그 우회를 한 줄로 적는 문법.
//
// 이 파일의 sessionHandler 람다 핵심 두 줄:
// ✅ sessionHandler 변수 = 그 WebSocketHandler 람다 객체에 대한 참조를 들고 있다.
// ✅ 람다 바디 {...} 안의 코드는 session이 매개변수로 들어와야 실행된다.
//
// ── 람다의 두 단계 (자바 객체 동작과 동일) ──
//   ① 람다 표현식 () -> {...} 평가 시점
//      → 함수형 인터페이스 구현체 "객체"만 생성. 바디 {...} 안 코드는 미실행.
//   ② 그 객체의 함수형 메서드가 호출되는 시점
//      → 그제서야 바디 안 코드가 실행됨.
//
//   함수형 인터페이스마다 호출 메서드 이름이 다르다:
//     Runnable          .run()
//     Supplier<T>       .get()
//     Function<T,R>     .apply(t)
//     Consumer<T>       .accept(t)
//     WebSocketHandler  .handle(session)   ← 이 파일이 쓰는 것
//
//   → sessionHandler 람다도 마찬가지. session이 매개변수로 들어와야 = 누군가
//     sessionHandler.handle(session)을 호출해야 바디 코드가 실행된다는 뜻.
//     이 파일에서 호출 주체는 webSocketClient — WebSocket 연결 성공 → 세션 생성 →
//     reactor-netty가 내부에서 sessionHandler.handle(session) 호출 → 그제서야 바디 실행.
//
// ※ 람다는 익명 클래스의 짧은 문법일 뿐.
//   () -> {...} ≡ new WebSocketHandler() { public Mono<Void> handle(WebSocketSession s){...} }
//   객체 생성 + 메서드 호출의 2단계 구조는 동일하다.
//
// ── 결론 ──
//   "session이 생기면 handle(session)을 호출하라" — 이 동작이 reactor-netty 내부에 미리 박혀 있다.
//   우리가 하는 일은 그 호출 시점에 실행될 람다 1개를 인자로 넘겨주는 것뿐.
//   그 다음부턴 라이브러리가 알아서 호출한다.
//   → 이게 자바의 콜백 / 이벤트 핸들러 패턴의 표준 구조다.
