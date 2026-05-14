package com.example.futurespapertrading.market;

import java.net.URI;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;

// Binance Futures BTCUSDT partial depth WebSocket에 연결하고 받은 raw JSON을 로그로 출력한다.
@Component
public class BinanceFuturesRawDepthLogger {

	// 이 클래스 이름으로 로그를 남기기 위한 SLF4J Logger다.
	private static final Logger log = LoggerFactory.getLogger(BinanceFuturesRawDepthLogger.class);

	// BTCUSDT 상위 20개 bid/ask를 100ms 단위로 받는 Binance USDⓈ-M Futures stream 주소다.
	private static final URI BTCUSDT_DEPTH_STREAM_URI =
			URI.create("wss://fstream.binance.com/ws/btcusdt@depth20@100ms");

	// Spring WebFlux가 제공하는 WebSocket 클라이언트 추상화다.
	private final WebSocketClient webSocketClient;

	// 현재 실행 중인 WebSocket 구독이다. 연결 중복 실행을 막고 종료 시 dispose하기 위해 보관한다.
	private volatile Disposable subscription;

	// 운영 코드에서는 Reactor Netty 기반 WebSocket 클라이언트를 사용한다.
	public BinanceFuturesRawDepthLogger() {
		this(new ReactorNettyWebSocketClient());
	}

	// WebSocketClient를 외부에서 넣을 수 있게 분리한 생성자다.
	BinanceFuturesRawDepthLogger(WebSocketClient webSocketClient) {
		this.webSocketClient = webSocketClient;
	}

	// Binance WebSocket 연결을 시작한다. 이미 연결 중이면 새 연결을 만들지 않는다.
	public synchronized boolean connect() {
		if (subscription != null && !subscription.isDisposed()) {
			return false;
		}

		log.info("Connecting to Binance Futures depth stream: {}", BTCUSDT_DEPTH_STREAM_URI);
		subscription = webSocketClient.execute(BTCUSDT_DEPTH_STREAM_URI, session ->
						// 서버에서 들어오는 WebSocket 메시지 본문을 문자열로 꺼낸 뒤 그대로 로그에 남긴다.
						session.receive()
								.map(WebSocketMessage::getPayloadAsText)
								.doOnNext(message -> log.info("Binance Futures raw depth JSON: {}", message))
								.then())
				.doOnError(error -> log.warn("Binance Futures depth stream failed", error))
				.doFinally(signalType -> {
					// 연결이 끝나면 다음 요청에서 다시 시작할 수 있도록 구독 상태를 비운다.
					log.info("Binance Futures depth stream finished: {}", signalType);
					subscription = null;
				})
				.subscribe();
		return true;
	}

	// 애플리케이션이 종료될 때 열려 있는 WebSocket 구독을 정리한다.
	@PreDestroy
	void disconnect() {
		if (subscription != null) {
			subscription.dispose();
		}
	}
}
