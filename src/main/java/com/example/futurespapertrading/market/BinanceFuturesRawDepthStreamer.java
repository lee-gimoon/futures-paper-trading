package com.example.futurespapertrading.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

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
	}

	// Binance WebSocket에 연결하고, 들어오는 메시지를 그대로 로그에 남긴다.
	public void connect() {
		log.info("Connecting to Binance Futures depth stream: {}", BTCUSDT_DEPTH_STREAM_URI);
		// webSocketClient.execute(URI, handler) — Mono<Void>(연결 종료 시 완료되는 약속)를 만들어 반환만 한다.
		// 인자: URI = 어디로 연결할지, handler = 연결 후 세션으로 뭘 할지 + 언제 끝낼지(반환 Mono<Void>로 신호).
		// 실제 TCP/TLS/WebSocket 핸드셰이크는 아래 .subscribe() 시점에야 reactor-netty가 시작.
		webSocketClient.execute(BTCUSDT_DEPTH_STREAM_URI, session ->
						// session.receive()는 서버에서 오는 메시지들의 Flux 스트림이다.
						session.receive()
								// 각 메시지의 payload를 문자열로 꺼낸다.
								.map(WebSocketMessage::getPayloadAsText)
								// 메시지가 들어올 때마다 부수효과로 로그를 찍는다.
								.doOnNext(message -> log.info("Binance Futures raw depth JSON: {}", message))
								// 2단계: 같은 메시지를 파싱해서 OrderBookSnapshot도 별도 로그로 찍는다.
								// 1단계의 raw 로그는 위 doOnNext에 그대로 두고, 여기는 "추가" 로그다.
								.doOnNext(this::logParsedSnapshot)
								// 스트림이 끝났음을 알리는 Mono<Void>로 변환한다.
								.then())
				// 연결 자체가 실패하거나 도중에 에러가 나면 로그로 남긴다.
				.doOnError(error -> log.warn("Binance Futures depth stream failed", error))
				// 이 람다는 .subscribe() 시점에 reactor-netty가 고른 event loop 1개(예: 손 5)의 selector에 채널 등록되어,
				// 그 한 손(event loop(스레드))이 모든 콜백을 평생 실행한다.
				// (참고: execute() 안의 "Binance URI 실제 연결 + 람다 콜백"은 HTTP 요청을 받은 손 3이 아니라 보통
				//  다른 손(예: 손 5)에 배정된다 — reactor-netty가 호출 스레드와 무관하게 EventLoopGroup.next()로 round-robin해서 부하를 분산하기 때문.)
				.subscribe();
	}

	// raw JSON을 파싱해서 OrderBookSnapshot을 로그로 찍는다.
	// 파싱이 실패해도 raw 스트림 전체를 죽이지 않도록 여기서 예외를 잡아 로그만 남긴다.
	private void logParsedSnapshot(String message) {
		try {
			OrderBookSnapshot snapshot = snapshotParser.parse(message);
			// 3단계: snapshot이 메모리에 존재하는 "이 순간"에 store에 박아둔다.
			// 이 try 블록이 끝나면 지역변수 참조가 끊겨 GC 대상이 되므로, 여기서 안 잡아두면 영원히 사라진다.
			latestStore.update(snapshot);
			// {} 자리에 SLF4J가 snapshot.toString()을 자동 호출해 끼워 넣는다.
			// record가 toString을 자동 생성하므로 안의 List/OrderBookLevel까지 재귀적으로 펼쳐져 한 줄로 찍힌다.
			log.info("Parsed OrderBookSnapshot: {}", snapshot);
		} catch (Exception e) {
			log.warn("Failed to parse depth JSON: {}", message, e);
		}
	}
}

// 람다식을 쓰는 이유:
//   목적(왜): 나중에 실행할 동작을 값처럼 넘기기 위해.
//   자바의 우회(어떻게): 함수가 1급 시민이 아니라서, "동작 1개짜리 인터페이스의 1회용 구현체 객체"를
//                      즉석에서 만들어 넘기는 방식으로 실현. 람다(->)는 그 우회를 한 줄로 적는 문법.

// Flux를 쓰는 이유 (간단 정리):
//   ★ 큰 그림: Flux는 "스트림 처리 라이브러리"다. 안 쓰면 그 안에 들어있는 도구들
//             (.map, .filter, .buffer, .retry, .merge 등)을 하나하나 직접 만들어 써야 한다.
//   ★ 비유:   자바에서 String.split(), List.sort()를 직접 안 짜듯이, 스트림 처리도 Reactor가
//             검증된 구현을 제공한다. 그걸 안 쓰면 그 일을 내가 다시 발명하는 셈.
//
//   .map(), .doOnNext(), .doOnError(), .subscribe() — 이 메서드들은 모두 Flux 클래스에 정의돼 있다.
//   그래서 쓰려면 Flux 객체가 있어야 한다. session.receive()가 Flux<WebSocketMessage>를 돌려주는 이유 =
//   이 메서드들을 점(.) 찍어 부르라고.
//
//   IDE에서 flux. 까지 치면 자동완성에 뜨는 게 전부 Flux 클래스의 메서드들이다:
//     map(), flatMap(), filter(),
//     doOnNext(), doOnError(), doOnComplete(),
//     buffer(), window(), retry(),
//     merge(), zip(),
//     subscribe(),
//     ... (수백 개)
//   전부 Reactor가 미리 만들어둔 Flux 클래스의 메서드.
//
// 메서드 체이닝의 비밀:
//   각 연산자 메서드는 "새 Flux"를 반환해서 또 점(.) 찍을 수 있다.
//     flux.map(...)        // Flux 반환
//         .doOnNext(...)   // 또 Flux 반환
//         .doOnError(...)  // 또 Flux 반환
//         .subscribe();    // 종착 (Subscription 반환)
//   이걸 fluent API / 메서드 체이닝이라고 한다. 그래서 한 줄로 길게 이어 쓸 수 있는 것.
//
// 한 줄 요약:
//   사용자 입장에선 "Flux 클래스에 정의된 편리한 메서드들(.map / .doOnNext / .doOnError 등)을
//   점 찍어 쓰기 위해" Flux를 받는 것.
//   (뒤에서 백프레셔, 스레드 안전, 에러 전파 같은 일도 일어나지만, 표면적으로 우리가 쓰는 건
//    Flux 클래스의 메서드들이다.)
//
// Mono<User> 예시:
//   Mono<User> userMono = userRepository.findById(1L);
//
//   이 코드는 User 객체를 바로 꺼낸 것이 아니라,
//   나중에 누군가 구독하면 1번 유저를 조회하고,
//   결과가 있으면 User 하나를 흘려보내고,
//   없으면 값 없이 완료하고,
//   실패하면 에러를 흘려보내는 실행 가능한 작업 설명서를 만든 것이다.
//
//   즉, User 하나가 나올 수 있는 비동기 흐름이다.
//   단, 여기서 "하나"는 0개 또는 1개라는 뜻이다.
//   유저가 없으면 null을 흘려보내는 게 아니라, 값 없이 완료된다.
//
// Flux<WebSocketMessage> 예시:
//   Flux<String> messages = session.receive()
//       .map(WebSocketMessage::getPayloadAsText)
//       .doOnNext(message -> log.info("received: {}", message));
//
//   session.receive()는 WebSocketMessage 목록을 바로 꺼내는 것이 아니라,
//   나중에 누군가 구독하면 WebSocket 서버에서 들어오는 메시지들을 기다리고,
//   메시지가 들어올 때마다 WebSocketMessage를 하나씩 흘려보내는 Flux 흐름을 만든다.
//
//   .map(WebSocketMessage::getPayloadAsText)는
//   흘러오는 WebSocketMessage를 String payload로 바꾸는 새 Flux를 만든다.
//
//   .doOnNext(message -> log.info(...))는
//   String 메시지가 흘러올 때마다 로그를 찍는 부수효과를 끼워 넣은 새 Flux를 만든다.
//
//   즉, 위 코드는 메시지를 지금 당장 읽어서 List처럼 저장하는 코드가 아니라,
//   "나중에 구독되면 WebSocket 메시지를 받고,
//   각 메시지를 String으로 바꾸고,
//   각 String이 지나갈 때 로그를 찍어라"라는 실행 가능한 스트림 설명서를 조립하는 코드다.
//
//   Flux는 여러 값이 시간에 따라 나올 수 있는 비동기 흐름이다.
//   여기서 "여러 값"은 0개부터 N개까지라는 뜻이다.
//   메시지가 하나도 안 올 수도 있고, 1개만 올 수도 있고, 계속 올 수도 있다.
//
//   실제로 값이 흘러가기 시작하는 대표적인 순간은 subscribe()다.
//   WebFlux/WebSocket 코드에서는 보통 이 Flux를 반환하거나 then()으로 Mono<Void>로 바꿔서
//   Spring/Reactor 쪽이 적절한 시점에 구독하게 만든다.



