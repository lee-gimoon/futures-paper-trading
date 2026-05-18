package com.example.futurespapertrading.market;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// HTTP로 Binance Futures BTCUSDT 호가 스트림을 시작하거나, 메모리에 보관된 최신 snapshot을 조회한다.
// 2단계까지: POST로 raw 스트림 시작 (메시지는 로그로 흘려보내고 사라짐).
// 3단계 추가: GET으로 현재 메모리에 박혀있는 최신 OrderBookSnapshot 조회.
@RestController
public class BinanceFuturesDepthController {

	// 실제 Binance WebSocket 연결과 raw JSON 로그 출력을 담당하는 객체다.
	private final BinanceFuturesRawDepthStreamer rawDepthStreamer;

	// 3단계: 파서가 만든 최신 snapshot이 담겨있는 메모리 양동이.
	private final LatestOrderBookSnapshotStore latestStore;

	public BinanceFuturesDepthController(
			BinanceFuturesRawDepthStreamer rawDepthStreamer,
			LatestOrderBookSnapshotStore latestStore) {
		this.rawDepthStreamer = rawDepthStreamer;
		this.latestStore = latestStore;
	}

	// 클라이언트가 이 POST 주소로 요청하면 BTCUSDT raw depth stream 연결을 시작한다.
	// curl -X POST http://localhost:8080/api/binance-futures/btcusdt/depth/raw/start
	@PostMapping("/api/binance-futures/btcusdt/depth/raw/start")
	public String start() {
		rawDepthStreamer.connect();
		return "Binance Futures BTCUSDT raw depth stream started.";
	}

	// 현재 메모리에 보관된 최신 OrderBookSnapshot을 JSON으로 반환한다.
	// Jackson이 record를 자동 직렬화 — 필드 이름이 그대로 JSON 키가 됨.
	// 스트림이 아직 안 켜졌거나 첫 메시지 전이면 store가 비어있으므로 404를 돌려준다.
	// curl http://localhost:8080/api/binance-futures/btcusdt/depth/latest
	@GetMapping("/api/binance-futures/btcusdt/depth/latest")
	public ResponseEntity<OrderBookSnapshot> latest() {
		return latestStore.latest()
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
