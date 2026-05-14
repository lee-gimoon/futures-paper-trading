package com.example.futurespapertrading.market;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// HTTP 요청을 받아 Binance Futures raw 호가 스트림 시작 명령을 처리한다.
@RestController
public class BinanceFuturesRawDepthController {

	// 실제 Binance WebSocket 연결과 raw JSON 로그 출력을 담당하는 객체다.
	private final BinanceFuturesRawDepthLogger rawDepthLogger;

	// Spring이 BinanceFuturesRawDepthLogger bean을 생성자에 주입한다.
	public BinanceFuturesRawDepthController(BinanceFuturesRawDepthLogger rawDepthLogger) {
		this.rawDepthLogger = rawDepthLogger;
	}

	// 클라이언트가 이 POST 주소로 요청하면 BTCUSDT raw depth stream 연결을 시작한다.
	@PostMapping("/api/binance-futures/btcusdt/depth/raw/start")
	public String start() {
		// 이미 실행 중이면 false, 새로 연결을 시작했으면 true를 반환한다.
		boolean started = rawDepthLogger.connect();
		if (started) {
			// 이번 요청으로 stream 연결이 새로 시작됐음을 알린다.
			return "Binance Futures BTCUSDT raw depth stream started.";
		}
		// 기존 stream 연결이 이미 살아 있어서 새 연결을 만들지 않았음을 알린다.
		return "Binance Futures BTCUSDT raw depth stream is already running.";
	}
}
