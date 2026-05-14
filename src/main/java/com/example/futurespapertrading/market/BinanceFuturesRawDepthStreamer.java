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
			URI.create("wss://fstream.binance.com/ws/btcusdt@depth20@100ms");

	// Spring WebFlux가 제공하는 Reactor Netty 기반 WebSocket 클라이언트다.
	private final WebSocketClient webSocketClient = new ReactorNettyWebSocketClient();

	// Binance WebSocket에 연결하고, 들어오는 메시지를 그대로 로그에 남긴다.
	public void connect() {
		log.info("Connecting to Binance Futures depth stream: {}", BTCUSDT_DEPTH_STREAM_URI);
		webSocketClient.execute(BTCUSDT_DEPTH_STREAM_URI, session ->
						// session.receive()는 서버에서 오는 메시지들의 Flux 스트림이다.
						session.receive()
								// 각 메시지의 payload를 문자열로 꺼낸다.
								.map(WebSocketMessage::getPayloadAsText)
								// 메시지가 들어올 때마다 부수효과로 로그를 찍는다.
								.doOnNext(message -> log.info("Binance Futures raw depth JSON: {}", message))
								// 스트림이 끝났음을 알리는 Mono<Void>로 변환한다.
								.then())
				// 연결 자체가 실패하거나 도중에 에러가 나면 로그로 남긴다.
				.doOnError(error -> log.warn("Binance Futures depth stream failed", error))
				// 실제로 구독을 시작해야 위 체인이 동작한다. 호출하지 않으면 아무 일도 일어나지 않는다.
				.subscribe();
	}
}
