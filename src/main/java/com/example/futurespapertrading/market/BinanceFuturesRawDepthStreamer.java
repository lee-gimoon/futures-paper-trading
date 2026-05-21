package com.example.futurespapertrading.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

		// ────────────────────────────────────────────────────────────
		// 용어 정리 (이 메서드 전체에 적용):
		//   - Publisher = 시간 축에 따라 값을 흘려보내는 객체. Reactive Streams 의 자바 인터페이스.
		//     .subscribe() 메서드가 여기에 정의돼 있다.
		//     Flux / Mono 는 Publisher 인터페이스를 implements 한 (추상) 클래스이고,
		//     그 하위에 실제로 new 되는 구체 클래스들이 잔뜩 있다 → 그래서 둘 다 .subscribe() 호출 가능.
		//
		//       Publisher<T>   (인터페이스, new 불가, subscribe 정의)
		//          ▲
		//          ├─ Flux<T>    (추상 클래스, new 불가) — 0~N 개의 T 를 흘려보냄, subscribe() 있음
		//          │    ▲
		//          │    ├─ FluxJust       (일반 클래스, new 가능)
		//          │    ├─ FluxMap        (일반 클래스, new 가능)
		//          │    └─ ...
		//          └─ Mono<T>    (추상 클래스, new 불가) — 0~1 개의 T 를 흘려보냄, subscribe() 있음
		//               ▲                                  (Mono<Void> 는 "완료/에러 신호만" 보내는 약속)
		//               ├─ MonoJust       (일반 클래스, new 가능)
		//               ├─ MonoNext       (일반 클래스, new 가능)
		//               ├─ MonoPeek       (일반 클래스, new 가능)
		//               └─ ...
		//     우리가 손에 쥐는 Flux/Mono 변수는 실제로는 그 아래 구체 하위 클래스 인스턴스를 가리킨다.
		//
		//     ※ Publisher 인터페이스만으론 부족한 이유: 공통 메서드(.map, .filter…) 모을 곳 + 체이닝용 반환 타입
		//        + 0~N(Flux) / 0~1(Mono) 의미 구분 + 표준 인터페이스와 라이브러리 고유 기능 분리.
		//
		//        층                               | 누가 정의                  | 역할                               | 생성 가능?
		//        ────────────────────────────────|──────────────────────────|────────────────────────────────────|──────────
		//        Publisher                        | Reactive Streams 표준(외부) | 최소 약속 (subscribe)             | ❌
		//        Flux / Mono                      | Reactor 라이브러리         | 공통 메서드 + 의미 구분 + 체이닝용 타입 | ❌
		//        FluxJust, FluxMap, MonoNext ...  | Reactor 내부              | 실제 동작 구현                      | ✓
		//
		//     Publisher 의 4가지 종류 (전부 Publisher 타입):
		//       1) 소스(source)        — Flux.just(1,2,3), Flux.range(1,10) 등
		//                                무에서 데이터 만듦.                                (새 데이터 생성: ✓)
		//       2) 어댑터(adapter)     — session.receive()
		//                                외부 세계의 신호를 RS 로 번역. 데이터는 외부(Binance)가 만듦. (✗, 변환만)
		//       3) 변환기(transformer) — .map(...) 결과 (FluxMap)
		//                                위에서 받은 값을 변형해서 새 값으로 내려보냄.       (⚠ 새 값 객체 생성)
		//       4) 엿보기(peek)        — .doOnNext(...) 결과 (FluxPeek)
		//                                값을 그대로 통과시키며 부수효과만 (로그, 카운트 등). (✗)
		//
		//     ※ 데이터를 새로 안 만들어도 Publisher "객체" 는 새로 생성된다.
		//        Publisher = "데이터 만드는 자" 가 아니라 "subscribe 받아 신호를 발행할 수 있는 자".
		//        각 단계마다 subscribe 시 동작이 달라야 하므로
		//          (어댑터는 채널에 연결, 엿보기는 upstream 구독 후 콜백 끼워 통과, 변환기는 onNext 마다 변형, 소스는 직접 발행)
		//        각각 별개의 Publisher 객체(고리)가 필요하다.
		//
		//   - 파이프라인 = Publisher 들이 .map / .doOnNext 같은 연산자로 줄줄이 이어진 체인 전체.
		//                각 연산자는 새 Publisher 를 반환하므로 점(.)으로 계속 이어 쓸 수 있다.
		//   - 어댑터(Adapter) = 서로 다른 두 인터페이스 사이를 번역해서 이어주는 중간 변환기.
		//                     (콘센트 어댑터가 한국 플러그 ↔ 미국 콘센트의 모양 차이를 흡수해주는 것처럼,
		//                      소프트웨어 어댑터도 한쪽 API 를 다른 쪽 API 가 알아듣는 형태로 변환해 줌)
		//                     Reactor 세계에서는 = 외부 세계(네트워크/DB/파일/콜백 API 등) 를 감싸서
		//                     Publisher 인터페이스로 노출하는 객체.
		//             이 코드의 session.receive() = Binance WebSocket 채널(Netty 의 콜백 기반 API) 을
		//             Flux<WebSocketMessage> (= Publisher 형태) 로 바꿔서 노출해 주는 어댑터.
		//             즉 Netty 가 channelRead(frame) 콜백으로 던지는 외부 신호를
		//             subscriber.onNext(msg) 같은 RS 신호로 번역해서 내 파이프라인에 흘려보낸다.
		//             어댑터 없으면 외부 이벤트를 내 Reactor 파이프라인이 못 받음.
		//             Reactor 파이프라인은 "Publisher (또는 그 하위 타입인 Flux/Mono)" 객체에만 점(.) 찍어 쓸 수 있기 때문.
		//             아래 사슬을 보면 각 줄이 다 "앞 객체가 Publisher 의 한 종류라서 호출 가능"임:
		//                session.receive()        // → Flux 반환. Flux 는 Publisher 의 하위 타입.
		//                    .map(...)            // .map 은 Flux 클래스의 메서드. 앞이 Flux 니까 점 찍기 가능.
		//                    .doOnNext(...)       // .doOnNext 도 Flux 클래스의 메서드. 앞이 Flux 니까 가능.
		//                    .subscribe();        // .subscribe 는 Publisher 인터페이스의 메서드. 앞이 Publisher 니까 가능.
		//             → 만약 session.receive() 가 Publisher 가 아닌 raw 콜백/리스너 객체를 돌려준다면
		//               그 객체엔 .map / .subscribe 같은 메서드가 아예 없어서 이 사슬이 성립 불가능.
		//               그래서 어댑터(session.receive() 의 Flux)가 "외부 콜백 ↔ Publisher 인터페이스" 다리 역할을 함.
		//                  └ "외부" = Reactor/Reactive Streams 의 세계 바깥에 있음 (Netty 라이브러리의 API)
		//                  └ "콜백" = "데이터 오면 호출해줘" 라고 등록하는 메서드들
		//                            (예: channelRead(frame), channelInactive(), exceptionCaught(e))
		//             → 결론: 어댑터 = "외부 비동기 API (이 코드에선 Netty 콜백)" 와 "Reactor 파이프라인" 의 통역사.
		//   - Cold = .subscribe() 가 호출되기 전에는 아무 일도 안 일어남. 아래 [1]~[3] 은 전부 cold.
		// ────────────────────────────────────────────────────────────

		// ────────────────────────────────────────────────────────────
		// [1] 세션 핸들러 (Publisher 빌더)
		//     - WebSocketSession = Binance 와 맺어진 연결 1개를 표현하는 객체.
		//       이걸로 메시지를 받고(session.receive()), 보내고(session.send()), 끊을(session.close()) 수 있다.
		//     - 세션 핸들러 = "그 session 이 주어지면 그걸로 뭘 할지" 를 적어둔 콜백 람다.
		//       여기선 "받은 메시지를 String 으로 바꾸고 → 로그 찍고 → 파싱해서 또 로그 찍기" 라는 파이프라인을 만든다.
		//     - 반환 타입 Mono<Void> = "스트림이 끝났다" 는 완료 신호 (값은 없음). 이것도 Publisher.
		//     - 지금은 람다일 뿐 실행 안 됨. session 객체는 .subscribe() 후 핸드셰이크가 끝나야 비로소 주어진다.
		// ────────────────────────────────────────────────────────────
		WebSocketHandler sessionHandler = session -> {

			// (1-a) 원천 Publisher = 파이프라인의 시작점 (어댑터 Flux):
			//
			//       session.receive() :
			//         WebSocketSession 의 메서드. 호출하면 Flux<WebSocketMessage> 를 돌려준다.
			//         이 메서드 자체는 "메시지를 지금 가져와라" 가 아니라,
			//         "구독하면 Binance WebSocket 서버에서 오는 메시지를 흘려보낼 Flux 를 만들어라" 라는 뜻.
			//         그래서 이 줄을 실행한 시점에는 네트워크에서 데이터를 읽지 않는다 (cold).
			//         .subscribe() 가 일어나는 순간 reactor-netty 가 채널에서 프레임을 읽기 시작하고,
			//         프레임이 도착할 때마다 이 Flux 의 onNext 로 WebSocketMessage 한 건씩 흘려보낸다.
			//
			//       그래서 이 Flux 가 무엇이냐 :
			//         진짜 데이터 생산자는 Binance 서버. 하지만 우리가 .subscribe() 할 수 있는
			//         Reactive Streams 의 Publisher 객체는 이 Flux 다.
			//         이 Flux 는 Binance 와 내 코드 사이의 어댑터 역할:
			//           외부 세계의 WebSocket 프레임 (= Binance 가 만든 데이터)
			//             → JVM 안의 Reactive Streams 신호 (= 내가 다루기 편한 형식)
			//               ─ onNext(msg)    : 메시지 1건 도착
			//               ─ onError(err)   : 에러 발생
			//               ─ onComplete()   : 스트림 종료
			//         이 변환을 reactor-netty 가 내부에서 수행해 준다.
			//
			//         ※ 위 onNext / onError / onComplete 는 Subscriber 가 정의한 콜백 메서드다.
			//           Publisher (이 Flux) 가 그 메서드를 호출해서 데이터/신호를 전달함.
			//           즉 "Subscriber 가 데이터를 받는다" = "Subscriber 의 콜백 메서드가 Publisher 에 의해 호출됨".
			//           Subscriber 는 "메서드를 가진 쪽", Publisher 는 "그 메서드를 부르는 쪽".
			//
			//           코드로 보면:
			//             // 1) Subscriber 가 자기 콜백 메서드를 정의해서 등록
			//             Subscriber<String> sub = new Subscriber<>() {
			//                 public void onNext(String msg)      { System.out.println("받음: " + msg); }
			//                 public void onError(Throwable e)    { e.printStackTrace(); }
			//                 public void onComplete()            { System.out.println("끝"); }
			//                 public void onSubscribe(Subscription s) { s.request(Long.MAX_VALUE); }
			//             };
			//             publisher.subscribe(sub);   // ← Publisher 한테 "내 콜백 여기 있어요" 등록
			//
			//             // 2) 나중에 Publisher 내부에서 Subscriber 의 메서드를 호출 (=신호 발행):
			//             sub.onNext("hello");        // → "받음: hello"  출력
			//             sub.onNext("world");        // → "받음: world"  출력
			//             sub.onComplete();           // → "끝"          출력
			//
			//           ★ 호출 주체는 Publisher, 호출되는 메서드의 소유자는 Subscriber.
			Flux<WebSocketMessage> incomingMessages = session.receive();

			// (1-b) 변환 Publisher = 파이프라인의 두 번째 토막:
			//       .map() 은 "들어온 값을 함수에 통과시켜 새 값으로 바꾸는 새 Flux 를 반환".
			//       원본 incomingMessages 는 안 바뀌고, payloadTexts 는 별개의 Publisher 객체로 새로 만들어진다.
			//       다만 payloadTexts 내부에 incomingMessages 가 source 필드로 참조되어 사슬의 안쪽 고리로 살아있다.
			Flux<String> payloadTexts = incomingMessages
					.map(WebSocketMessage::getPayloadAsText);

			// (1-c) 부수효과 Publisher = 파이프라인 중간에 끼운 "엿보기" 토막:
			//       .doOnNext() 는 값을 바꾸지 않고 "지나갈 때 뭔가 추가로 하는" 새 Flux 를 반환.
			//       여기선 로그를 찍는다. 데이터 흐름은 그대로 유지됨.
			Flux<String> withRawLog = payloadTexts
					.doOnNext(message -> log.info("Binance Futures raw depth JSON: {}", message));

			// 같은 파이프라인에 한 번 더 엿보기 토막을 끼운다 — 이번엔 파싱해서 로그.
			Flux<String> withParsedLog = withRawLog
					.doOnNext(this::logParsedSnapshot);

			// (1-d) 파이프라인의 종료 어댑터: Flux<String> → Mono<Void>
			//       .then() 은 upstream(withParsedLog) 이 보내는 신호 중
			//         - onNext (값들) → 전부 버림 (downstream 에 전달 X)
			//         - onComplete (정상 종료) → 그대로 통과시켜 downstream 에 전달
			//         - onError (에러) → 그대로 통과시켜 downstream 에 전달
			//         - cancel (취소) → upstream 쪽으로 그대로 전파
			//       이런 식으로 동작하는 새 Mono<Void> 객체를 만든다.
			//       즉 .then() = "값에는 관심 없고, '끝났는지 / 실패했는지' 만 알고 싶다" 라는 의미.
			//
			//       Mono<Void> = "결과값 없이 알림만 주는 약속" = 자바 void 메서드의 비동기 버전.
			//       Void 타입은 인스턴스를 만들 수 없어서 onNext 를 부를 수가 없고,
			//       그래서 onComplete (끝남) 또는 onError (실패) 두 신호만 발생 가능.
			//
			//       WebSocketHandler.handle() 의 반환 타입이 Mono<Void> 라서 이 형태로 맞춤.
			//       reactor-netty 는 메시지 값엔 관심 없고 "이 WebSocket 작업이 언제 끝나는지" 만 알면 되기 때문.
			Mono<Void> sessionDone = withParsedLog.then();

			return sessionDone;
		};

		// ────────────────────────────────────────────────────────────
		// [2] 최상위 Publisher: "연결 + 처리 + 종료" 전체를 한 덩어리로 표현한 Mono<Void>
		//     반환 타입은 Mono<Void> 지만, Mono 가 Publisher 의 구현체이므로 본질은 Publisher 객체.(Mono가 Publisher의 구현체인 이유는 위에 용어 정리 주석 확인)
		//     → 그래서 아래 [4] 에서 .subscribe() 호출이 가능한 것 (subscribe 는 Publisher 의 메서드).
		//
		//     아래 execute(...) 한 줄의 의미 (subscribe 시 일어날 일):
		//       1) BTCUSDT_DEPTH_STREAM_URI 로 접속한다.
		//       2) 접속 성공하면 WebSocketSession 을 만든다.
		//       3) 그 session 을 위에서 정의한 sessionHandler 에 넘긴다.
		//       4) sessionHandler 가 반환한 Mono<Void> 가 끝날 때까지 연결을 유지한다.
		//
		//     실행 시점 정확히 정리:
		//       execute() 는 그 줄에서 즉시 실행되어 Mono 객체를 만들고 반환했음.
		//       단지 그 Mono 안에 저장된 "실행 람다"가 subscribe() 시에 비로소 실행된다.
		//       (즉 위 1)~4) 의 실제 작업은 아래 [4] 의 subscribe() 시점에 일어남)
		//     이 Mono 가 완료될 때 = WebSocket 연결/처리가 정상 종료될 때.
		// ────────────────────────────────────────────────────────────
		Mono<Void> connectionPublisher =
				webSocketClient.execute(BTCUSDT_DEPTH_STREAM_URI, sessionHandler);
		// 반환 객체 = Publisher 사슬의 가장 바깥 고리(Mono<Void>, 구체 클래스 MonoNext)
		// — subscribe() 가 안쪽으로 전파되면 reactor-netty 가 비로소 URI 접속 → 세션 생성
		//   → sessionHandler 람다에 session 을 넘겨 호출한다.

		// ────────────────────────────────────────────────────────────
		// [3] 에러 훅이 붙은 새 Publisher
		//     - .doOnError() 는 원본을 바꾸지 않고, "에러 발생 시 로그 찍는 동작을 끼워넣은" 새 Mono 를 만듦.
		//     - connectionPublisher 자체는 그대로. observablePublisher 는 별개 객체.
		//     - Flux 의 .doOnNext 와 정확히 같은 패턴 — Mono 에서는 "에러 엿보기" 가 .doOnError.
		// ────────────────────────────────────────────────────────────
		Mono<Void> observablePublisher = connectionPublisher
				.doOnError(error -> log.warn("Binance Futures depth stream failed", error));

		// ────────────────────────────────────────────────────────────
		// [4] Subscriber 등장 — 여기서 비로소 실제 연결 시작 (cold → hot 전환)
		//     - .subscribe() 가 내부적으로 Subscriber 객체를 만들어 Publisher 에 붙임.
		//     - 이 순간 reactor-netty 가 event loop 하나(아래 메모의 '손 5')를 골라 채널 등록
		//       → TCP + WebSocket 핸드셰이크 시작.
		//     - 핸드셰이크 성공 → WebSocketSession 객체 생성 → 위 [1] 의 람다가 그제서야 호출됨.
		//     - .subscribe() 의 반환값(Disposable, "구독을 끊는 손잡이")은 여기선 안 쓰므로 받지 않음.
		//
		// 스레드 메모:
		//   .subscribe() 자체는 호출한 스레드(컨트롤러에서 부르면 event loop A, 손 3)가 실행하지만,
		//   reactor-netty 가 EventLoopGroup.next() 로 round-robin 해서 채널을 다른 event loop B(예: 손 5)에 배정.
		//   그 뒤로 모든 콜백 (map, doOnNext, ...) 은 평생 손 5 하나가 실행한다.
		//   → 부하 분산 + 채널 affinity (단일 스레드 = 락 불필요) 를 위한 설계.
		//
		// 데이터 도착 감지 메모:
		//   사실 손 5 는 앱 부팅 때부터 selector.select() 무한 루프를 이미 돌고 있었다.
		//   selector.select() 는 OS 에 "내가 등록한 채널 중 읽을 거 있어?" 묻고
		//   없으면 OS 가 깨워줄 때까지 잠 (CPU 0%, busy waiting 아님).
		//   subscribe() 가 한 일 = "우리 Binance 채널을 손 5 의 selector 에 등록" 시킨 것.
		//   이때부터 손 5 의 감지 범위에 우리 채널이 포함됨.
		//   Binance 가 패킷을 보내면 → OS 가 손 5 깨움 → 채널에서 프레임 읽음
		//     → reactor-netty 가 Reactor 의 onNext 신호로 변환
		//     → 위 파이프라인 (map → doOnNext → ...) 이 차례로 실행.
		//   즉 "데이터가 왔는지" 는 우리가 폴링하는 게 아니라 OS 가 알려준다 (이벤트 기반/push).
		// ────────────────────────────────────────────────────────────
		observablePublisher.subscribe();
		// 정확한 표현:
		//   execute() 는 진작에 실행됐고 Mono 만 만들어뒀음.
		//   subscribe() 시 그 Mono 안에 저장돼 있던 람다가 실행되며,
		//   그 안에서 핸드셰이크 → 세션 생성 → handler.handle(session) 호출이 일어남.
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

// connect() 전체 라이프사이클 흐름 (subscribe 부터 메시지 처리, 종료까지):
//
// [우리] observablePublisher.subscribe()         ← subscribe #1 (우리가 호출)
//    │
//    │ subscribe #1 이 외부 사슬 안에서 downstream → upstream 으로 거꾸로 전파:
//    │ (각 단계는 자기 wrapper subscriber 만들고 source 필드의 .subscribe() 호출)
//    │   observablePublisher (MonoPeek, doOnError 결과)
//    │       │  .source = connectionPublisher
//    │       ▼
//    │   connectionPublisher (MonoNext, webSocketClient.execute(...) 가 반환한 것)
//    │       │  .source = reactor-netty 내부 객체
//    │       ▼
//    │   reactor-netty 내부 사슬 (HttpClient + WebSocketUpgrade + ...)
//    │       │  .source 필드 따라 몇 단계 더
//    │       ▼
//    │   가장 안쪽 (HttpClientConnect 같은 곳)
//    │       └ ※ subscribe #1 의 종착역.
//    │         더 이상 .source 필드가 없는 source-level Publisher 라서 더 거슬러 갈 곳이 없음.
//    │         여기서 진짜 네트워크 작업 시작:
//    │           - Netty 채널 생성 → 이벤트 루프(손 5)에 등록
//    │           - TCP connect 시도 → WebSocket upgrade 요청
//    │         이 작업은 비동기로 시작만 시키고 .subscribe() 메서드는 곧장 return.
//    │         return 이 한 단계씩 위로 올라가 우리의 observablePublisher.subscribe() 줄이 return.
//    │         (이 시점에 핸드셰이크는 아직 진행 중. 우리 코드는 다음 줄로 넘어감)
//    │
//    │ ※ subscribe #1 은 외부 사슬만 깨운다. 내부 사슬은 아직 존재조차 안 함 (람다 미실행).
//    │
//    ▼
// [reactor-netty] TCP/WebSocket 핸드셰이크 (비동기, 손 5 가 처리)
//    │
//    │ 핸드셰이크 성공 → session 생성
//    │
//    ▼
// [reactor-netty] sessionHandler.handle(session) 호출
//    │
//    │ 우리 람다 실행 → 람다 안에서 내부 사슬을 즉석에서 만든다:
//    │   session.receive() → .map(...) → .doOnNext(rawLog) → .doOnNext(parsed) → .then()
//    │ 마지막의 .then() 결과(= sessionDone) 를 return
//    │
//    ▼
// [reactor-netty] sessionDone 받음
//    │
//    │ ※ 받은 sessionDone 은 cold. 누군가 .subscribe() 안 해주면 영원히 멈춰 있음
//    │   (= 내부 사슬이 자고 있는 상태, 메시지 한 건도 안 흐름).
//    │   reactor-netty 는 이 Mono 가 onComplete/onError 되는 시점을 알아야 채널을 닫을 수 있음
//    │   → 그래서 자기가 직접 subscribe.
//    │   우리는 sessionDone 참조가 없고 (람다 안 지역변수) 끼어들 시점도 없어서 못 한다.
//    │
//    │ 내부에서 sessionDone.subscribe(internalSub)   ← subscribe #2 (reactor-netty 가 자동 호출)
//    │   - WebSocketHandler 계약상 reactor-netty 가 자기 책임으로 수행
//    │   - internalSub = 외부 사슬과 내부 사슬을 잇는 다리 subscriber
//    │     (내부 사슬에서 올라온 onComplete/onError 를 외부 사슬로 전달 + 채널 close)
//    │
//    ▼
// [sessionDone 사슬 활성화]
//    │
//    │ ※ 사실 사슬이 두 층이다:
//    │     ┌── 외부 사슬 (우리가 [2]~[3] 에서 만든 것 + reactor-netty 내부) ──┐
//    │     │   observablePublisher → connectionPublisher → reactor-netty 내부 │
//    │     └──────────────────────────────────────────────────────────────┘
//    │                                  ↕ reactor-netty 가 둘 사이를 이어줌
//    │     ┌── 내부 사슬 (우리 람다 안에서 만든 것) ────────────────────────┐
//    │     │   sessionDone(=MonoIgnoreThen) → FluxPeek → FluxPeek →        │
//    │     │   FluxMap → session.receive() Flux                            │
//    │     └──────────────────────────────────────────────────────────────┘
//    │     ※ sessionDone 변수 = .then() 이 반환한 MonoIgnoreThen 객체 (같은 거).
//    │
//    │ subscribe #2 가 "내부 사슬" 안에서 downstream(sessionDone) → upstream(session.receive()) 으로 거꾸로 전파:
//    │   sessionDone(=MonoIgnoreThen) → FluxPeek → FluxPeek → FluxMap → session.receive() Flux
//    │   (= 바깥 wrapper 에서 시작해 안쪽 source 까지 거꾸로 거슬러 올라감)
//    │   ※ 여기서 "바깥/안쪽" 은 내부 사슬 기준. 전체 구조로 보면 sessionDone 위에 외부 사슬이 더 얹혀 있다.
//    │
//    │ ※ 만든 직후엔 모든 고리가 cold(잠) 상태. subscribe 가 그 고리까지 도달해야 비로소 깨어남.
//    │   왜 전파해야 하나 = 각 고리가 자기 일을 시작해야 해서. (어댑터는 Netty 채널 등록,
//    │   FluxMap 은 변환 Subscriber 끼우기, FluxPeek 은 콜백 끼우기 등) 한 군데라도 안 도달하면
//    │   사슬이 끊겨 데이터가 못 흐른다 — 전등 직렬 연결처럼.
//    │   ※ 이 subscribe 전파는 평생 단 1번만 일어남 (메시지마다 다시 호출되는 게 아님).
//    │     한 번 사슬을 깨워두면 그 위로 수많은 메시지 (msg1, msg2, ...) 가 흐르고,
//    │     onComplete / onError 가 1번 도착할 때까지 사슬이 그대로 유지됨.
//    │
//    ▼
// [session.receive() 의 어댑터 Flux 도 subscribe 됨]
//    │
//    │ 내부적으로 Netty 채널에 read 핸들러 등록
//    │   └ Netty 채널(Channel) = Netty 가 네트워크 연결 1개(TCP 소켓 + WebSocket) 를 추상화한 객체.
//    │                          read/write/close 같은 메서드를 가짐. 핸들러를 여기에 붙여서 이벤트를 받음.
//    │   └ read 핸들러 = "채널에 데이터가 도착하면 호출될 콜백 객체" (Netty 의 ChannelHandler).
//    │                  channelRead(ctx, frame) 같은 메서드를 가짐. 데이터 도착마다 Netty 가 자동 호출.
//    │ → "프레임 오면 onNext 발행해줘" 라는 read 핸들러를 채널에 붙여두면,
//    │   Netty 가 Binance 프레임 받을 때마다 그 핸들러를 호출 → 우리 Flux 의 onNext 가 발행됨.
//    │   └ "프레임(frame)" = Binance 데이터를 담은 WebSocket 프로토콜의 표준 전송 단위 (헤더 + 페이로드).
//    │                      페이로드 안의 JSON 문자열이 진짜 Binance 데이터. 우리 코드의 WebSocketMessage
//    │                      객체가 이 프레임을 자바로 감싼 것이고, .map(...::getPayloadAsText) 가 그 안에서
//    │                      JSON 문자열만 꺼내는 단계.
//    │
//    ▼
// [채널 read 대기 상태 (event loop 가 selector 에서 잠)]
//    │
//    │ ... Binance 가 첫 프레임 보낼 때까지 침묵 ...
//    │
//    ▼
// [Binance 가 msg1 보냄] → OS 가 손 5 깨움
//    │
//    ▼
// [msg1 흘러감]
//    ※ 방향 주의: 여기부터는 데이터(onNext) 흐름이라 위에서 했던 subscribe 전파와 반대 방향이다.
//      subscribe 는 downstream(내부 사슬의 바깥 = sessionDone) → upstream(내부 사슬의 안쪽 = session.receive()) 으로 거꾸로 갔지만,
//      데이터(onNext / onError / onComplete) 는 upstream(session.receive()) → downstream(sessionDone) 으로 정방향으로 흐른다.
//      한 사슬에 "구독 신청" 과 "데이터 전달" 의 두 방향이 같이 흐른다고 보면 된다.
//      그리고 sessionDone 까지 도착한 onComplete/onError 는 다시 외부 사슬(reactor-netty 내부 → connectionPublisher → observablePublisher) 까지 더 전파됨.
//
//    session.receive() : onNext(msg1)
//         ↓
//    .map(...)         : onNext("msg1 텍스트")
//         ↓
//    .doOnNext(rawLog) : 로그 + onNext 그대로 전파
//         ↓
//    .doOnNext(parsed) : 파싱 + 저장 + 로그 + onNext 전파
//         ↓
//    .then() = sessionDone : onNext 무시 (downstream 전달 X)
//
//    ※ msg1 처리 끝. 사슬은 그대로 살아있음 (subscribe 다시 안 함, 람다 다시 안 만듦).
//    │
//    ▼
// [채널 read 대기 상태로 복귀 (event loop 가 selector 에서 다시 잠)]
//    │
//    │ ... Binance 가 다음 프레임 보낼 때까지 침묵 ...
//    │
//    ▼
// [Binance 가 msg2 보냄] → OS 가 손 5 깨움
//    │
//    ▼
// [msg2 흘러감 — msg1 과 완전히 같은 사슬, 같은 람다, 다시 통과]
//    session.receive() : onNext(msg2)
//         ↓
//    .map(...)         : onNext("msg2 텍스트")
//         ↓
//    .doOnNext(rawLog) : 로그 + onNext 그대로 전파
//         ↓
//    .doOnNext(parsed) : 파싱 + 저장 + 로그 + onNext 전파
//         ↓
//    .then() = sessionDone : onNext 무시 (downstream 전달 X)
//
//    ※ msg2 처리 끝. 사슬은 여전히 살아있음.
//    │
//    ▼
// [채널 read 대기 → msg3 도착 → 또 같은 사슬 통과 → 대기 → msg4 → ...]
//    (Binance 가 close frame 보내거나 에러 날 때까지 평생 반복)
//    → 핵심: 사슬/람다는 처음에 한 번 만들어진 후 그대로, 메시지만 시간 따라 흘러옴.
//    │
//    ▼
// [onComplete 또는 onError 도달]
//    내부 사슬에서 외부 사슬로 신호 전파 (downstream 방향, 데이터 흐름과 같은 방향):
//      session.receive() (or 도중) → .map → .doOnNext → .doOnNext → .then() = sessionDone
//        → reactor-netty 내부 사슬 (.handle / .doOnRequest / .next) → connectionPublisher → observablePublisher
//      → 마지막 observablePublisher 까지 도달하면 우리 .subscribe() 호출이 끝남
//      → reactor-netty 가 WebSocket 채널 정리 (TCP close, 리소스 회수 등)




