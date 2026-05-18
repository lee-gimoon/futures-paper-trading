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

	// Jackson이 record를 자동 직렬화 — 필드 이름이 그대로 JSON 키가 됨.
	// 스트림이 아직 안 켜졌거나 첫 메시지 전이면 store가 비어있으므로 404를 돌려준다.
	// curl http://localhost:8080/api/binance-futures/btcusdt/depth/latest
	//
	// ★ JSON 직렬화는 이 함수 안 어느 줄에서도 일어나지 않는다 — return 이후 Spring 영역에서 자동으로 일어난다.
	//   트리거는 위 @RestController 한 줄: "이 클래스의 모든 메서드 반환값을 HTTP 응답 본문으로 직렬화하라"는 약속.
	//   그 약속 덕분에 함수가 ResponseEntity<OrderBookSnapshot>을 반환만 하면, Spring이 받아서
	//   MappingJackson2HttpMessageConverter를 골라 호출 → 그 컨버터가 내부적으로 ObjectMapper 빈
	//   (2단계 Parser가 readTree에 쓰는 그 객체와 동일한 싱글톤)의 writeValue...을 호출해 JSON 바이트로 변환한다.
	//   record의 자동 생성된 접근자(symbol(), eventTime(), bids(), asks())를 Jackson이 리플렉션으로 찾아내
	//   메서드 이름을 그대로 JSON 키로 쓴다 → {"symbol":"BTCUSDT","eventTime":...,"bids":[...],"asks":[...]}.
	//   우리가 JSON 변환 코드를 한 줄도 안 적은 이유 = @RestController + 객체 반환 = Spring의 컨벤션.
	@GetMapping("/api/binance-futures/btcusdt/depth/latest")
	public ResponseEntity<OrderBookSnapshot> latest() {
		return latestStore.latest()
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
