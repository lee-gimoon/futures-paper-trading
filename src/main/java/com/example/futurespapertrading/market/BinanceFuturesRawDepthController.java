package com.example.futurespapertrading.market;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// HTTP 요청을 받아 Binance Futures raw 호가 스트림 시작 명령을 처리한다.
@RestController
public class BinanceFuturesRawDepthController {

	// 실제 Binance WebSocket 연결과 raw JSON 로그 출력을 담당하는 객체다.
	private final BinanceFuturesRawDepthStreamer rawDepthStreamer;

	// Spring이 BinanceFuturesRawDepthStreamer bean을 생성자에 주입한다.
	public BinanceFuturesRawDepthController(BinanceFuturesRawDepthStreamer rawDepthStreamer) {
		this.rawDepthStreamer = rawDepthStreamer;
	}

	// 클라이언트가 이 POST 주소로 요청하면 BTCUSDT raw depth stream 연결을 시작한다.
	// curl -X POST http://localhost:8080/api/binance-futures/btcusdt/depth/raw/start
	@PostMapping("/api/binance-futures/btcusdt/depth/raw/start")
	public String start() {
		rawDepthStreamer.connect();
		return "Binance Futures BTCUSDT raw depth stream started.";
	}
}
