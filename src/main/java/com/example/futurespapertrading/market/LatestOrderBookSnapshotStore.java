package com.example.futurespapertrading.market;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

// 3단계의 핵심 — 흘러가는 push 스트림을 "지금 상태"로 붙잡아두는 양동이.
// 메시지가 도착할 때마다 통째로 교체되고(매번 새 양동이), HTTP 요청은 양동이를 그 순간 들여다본다.
//
// 동시성: Writer = WebSocket event loop 1개, Reader = HTTP 요청 N개.
// AtomicReference.set/get은 단일 참조 교체/조회를 락 없이 원자적으로 처리하므로
// 락(synchronized) 없이 안전하다. 우리는 한 snapshot 안의 필드를 부분 갱신하지 않고
// 매번 새 OrderBookSnapshot으로 통째 교체하기 때문에 이걸로 충분하다.
//   (6단계에서 diff 적용으로 갈 때는 별도 자료구조가 필요해질 수 있다.)
@Component
public class LatestOrderBookSnapshotStore {

	// 메모리상 단 1개로 존재하는 근거:
	//   위 @Component로 Spring이 Store 객체를 부팅 시 1개만 만들기 때문에, 그 객체에 속한 이 필드도 자연히 1개.
	//   Streamer와 Controller가 같은 Store 빈을 주입받아 같은 latest를
	//   가리키게 된다 (Writer/Reader가 같은 양동이를 보는 핵심).
	private final AtomicReference<OrderBookSnapshot> latest = new AtomicReference<>();

	// 새 메시지가 파싱되어 도착할 때마다 호출된다. 통째로 교체 — 6단계에서 diff로 발전.
	public void update(OrderBookSnapshot snapshot) {
		latest.set(snapshot);
	}

	// 현재 보관 중인 최신 snapshot. 스트림이 안 켜졌거나 첫 메시지 전이면 Optional.empty().
	public Optional<OrderBookSnapshot> latest() {
		return Optional.ofNullable(latest.get());
	}
}
