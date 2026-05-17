package com.example.futurespapertrading.market;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

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

	public BinanceFuturesRawDepthStreamer(OrderBookSnapshotParser snapshotParser) {
		this.snapshotParser = snapshotParser;
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