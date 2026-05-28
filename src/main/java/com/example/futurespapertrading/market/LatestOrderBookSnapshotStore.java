package com.example.futurespapertrading.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

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

	private static final Logger log = LoggerFactory.getLogger(LatestOrderBookSnapshotStore.class);

	// 메모리상 단 1개로 존재하는 근거:
	//   위 @Component로 Spring이 Store 객체를 부팅 시 1개만 만들기 때문에, 그 객체에 속한 이 필드도 자연히 1개.
	//   Streamer와 Controller가 같은 Store 빈을 주입받아 같은 latest를
	//   가리키게 된다 (Writer/Reader가 같은 양동이를 보는 핵심).
	//   현재 SSE stream() 컨트롤러는 아래 sink/asFlux()를 쓰므로 이 값을 직접 읽지 않는다.
	//   이 latest는 /depth/latest 단발 조회 API에서 "지금 최신 snapshot 1개"를 꺼내기 위한 보관함이다.
	private final AtomicReference<OrderBookSnapshot> latest = new AtomicReference<>();

	// ✨ 추가: 변화 알리미 (Hot Flux + multicast + replay(1))
	// replay().limit(1) = 멀티캐스트 + 최근 1건 캐싱
	private final Sinks.Many<OrderBookSnapshot> sink =
			Sinks.many().replay().limit(1);

	// 새 메시지가 파싱되어 도착할 때마다 호출된다. 통째로 교체 — 6단계에서 diff로 발전.
	public void update(OrderBookSnapshot snapshot) {
		log.info("[STEP7-store.update] thread={}", Thread.currentThread().getName());
		latest.set(snapshot); // /depth/latest 단발 조회 API가 꺼내볼 최신 snapshot 1개를 저장
		sink.tryEmitNext(snapshot); // snapshot을 Sink 허브에 발행한다. Sink는 이를 onNext로 현재 구독자들에게 전달한다
	}

	// 현재 보관 중인 최신 snapshot. SSE stream()용이 아니라 /depth/latest 단발 조회용이다.
	// 스트림이 안 켜졌거나 첫 메시지 전이면 Optional.empty().
	public Optional<OrderBookSnapshot> latest() {
		return Optional.ofNullable(latest.get());
	}

	// ✨ 추가: 변화 흐름 구독 진입점. 컨트롤러가 이걸 받아 SSE로 흘려보낸다.
	public Flux<OrderBookSnapshot> stream() {
		return sink.asFlux();
	}
}
//		List<String>       = [ "a", "b", "c", "d", ... ]   ← 대괄호 [] = 진짜 List, 슬롯 N개
//		Map<K,V>           = { k1→v1, k2→v2, ... }         ← 중괄호 {} = 진짜 Map
//		AtomicReference<T> = ┃ ➡ ┃                          ← 통 모양, 슬롯 1개 안에 참조 1개. List 아님 (별개 클래스)
//
//		※ "참조(➡)"의 정체 = 객체가 메모리의 어디에 있는지를 가리키는 주소 숫자(64비트).
//		   예: 0x00007F8A2B3C4D5E 같은 16진수. set/get은 이 주소를 통째로 갈아끼우고/읽는 연산.

//		AtomicReference<OrderBookSnapshot> latest
//		┌─────────────────────────────────────────────────┐
//		│  ➡ 메모리 주소 (예: 0x00007F8A2B3C4D5E, 64비트)    │
//		└─────────────────────────────────────────────────┘
//		                       │
//		                       ▼ (그 주소가 가리키는 메모리 위치)
//		┌──────────────────────────────────┐
//		│ OrderBookSnapshot (record, 불변)  │
//		│   symbol: "BTCUSDT"               │
//		│   eventTime: 1715900000000        │
//		│   bids: [Level, Level, ...]       │
//		│   asks: [Level, Level, ...]       │
//		└──────────────────────────────────┘
//
//		메서드 매핑:
//		  update(snapshot) → latest.set(snapshot)  : 슬롯에 든 메모리 주소 1개를 새 주소로 통째 교체
//		  latest()         → latest.get()          : 슬롯에 든 현재 메모리 주소 1개를 통째로 읽음
//
//		참고로 "통째 교체/읽기"가 가능한 이유는 AtomicReference 안쪽 값이 volatile 필드이기 때문이다.
//		volatile(여러 스레드가 공유하는 값에 대해, 쓰기는 다른 스레드가 볼 수 있게 공개하고
//		읽기는 최신 공개 값을 다시 읽도록 강제하는 JVM 메모리 규칙)이 붙어 있어서
//		visibility(다른 스레드가 최신 값을 볼 수 있음) + ordering(앞뒤 명령 재정렬 제한)이 묶여있다.
//		일반 필드의 `=` 대입은 참조 자체는 원자적이지만 이 두 보장이 빠져, Reader가 오래된 주소를
//		계속 보거나 아직 안전하게 공개되지 않은 객체를 볼 수 있다. 그래서 일반 필드 대신
//		AtomicReference(또는 volatile)를 쓴다.
//
//		만약 일반 필드로 쓰면 모양이 이렇게 바뀐다 (껍데기 AtomicReference<T>가 빠지면 타입은 그냥 T):
//		  private volatile OrderBookSnapshot latest;   // ← final 빠지고, 안전 위해 volatile 권장
//		  public void update(OrderBookSnapshot snapshot) { this.latest = snapshot; }
//		  public Optional<OrderBookSnapshot> latest()   { return Optional.ofNullable(this.latest); }
//
//		final이 빠지는 이유: 일반 필드는 필드 자체가 새 snapshot을 가리키도록 매번 재할당돼야 하기 때문.
//		(AtomicReference 버전은 박스 객체 자체는 평생 같은 박스라 final이 살아있고, 박스 안의
//		메모리 주소만 set으로 바뀌는 구조 — 불변성과 가변성을 한 단계 간접으로 분리한 셈이다.)
