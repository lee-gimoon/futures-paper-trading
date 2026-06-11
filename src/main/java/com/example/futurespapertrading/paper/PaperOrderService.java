package com.example.futurespapertrading.paper;

import com.example.futurespapertrading.market.LatestOrderBookSnapshotStore;
import com.example.futurespapertrading.market.OrderBookSnapshot;
import com.example.futurespapertrading.paper.dto.CreateOrderRequest;
import com.example.futurespapertrading.paper.dto.OrderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

// 모의 주문의 '주문 처리(place)·조회(list) 비즈니스 로직' 계층. 컨트롤러(PaperOrderController)가 HTTP를 받아 이 서비스에 위임한다.
//   (C~E단계까진 컨트롤러가 직접 했지만, F의 취소·G의 matcher가 같은 체결·저장 로직을 재사용하게 되어 서비스로 추출했다.)
//
// 처리 흐름:  최신 호가 확보 → PaperTradingEngine.tryFill 로 체결 계산
//            → 상태 판정(FILLED / OPEN[지정가 미체결] / REJECTED[시장가 미체결]) → 주문 저장 → 그 id로 체결(fill) 저장 → 요약 응답.
//   · 엔진은 "fill 목록"만 계산하는 순수 함수. "상태 결정 + DB 저장"이라는 부수효과는 이 서비스가 맡는다.
//
// ⚠️ 트랜잭션: 지금은 주문 저장과 fill 저장이 별도라, 둘 사이에서 실패하면 주문만 남을 수 있다.
//    MVP라 단순하게 두지만, 원자성이 필요해지면 이 서비스 메서드에 @Transactional(리액티브)로 묶는다.
@Service
public class PaperOrderService {

    // ── 의존성 주입: 스프링이 생성자로 채워준다 ──
    private final PaperTradingEngine engine;                 // 순수 체결 계산기 (B단계)
    private final PaperOrderRepository orderRepository;      // 주문 저장/조회
    private final PaperFillRepository fillRepository;        // 체결 저장/조회
    private final LatestOrderBookSnapshotStore latestStore;  // 메모리에 보관된 최신 호가 snapshot

    public PaperOrderService(PaperTradingEngine engine,
                             PaperOrderRepository orderRepository,
                             PaperFillRepository fillRepository,
                             LatestOrderBookSnapshotStore latestStore) {
        this.engine = engine;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.latestStore = latestStore;
    }

    // 검증 통과한(@Valid 통과) 주문 1건을 받아: ① 지금 호가에 대고 '얼마나 체결되나' 평가 → ② 그 결과로 상태(FILLED/OPEN/REJECTED) 판정 → ③ 주문·체결을 DB 저장 → ④ 요약(OrderResponse)을 돌려준다.
    //   이름대로 '주문을 넣는(place an order)' 핵심 로직. 시장가·지정가 둘 다 이 메서드를 거치고, 둘의 차이는 ②의 상태 판정뿐이다.
    //   반환이 Mono<OrderResponse>라 여기선 파이프라인만 엮어 return하고, 실제 실행은 WebFlux가 구독할 때 일어난다.
    //   create()가 입력검증·userId를 걸러 넘기므로, 여기선 '체결 판정 + 저장'에만 집중한다.
    //   상태 판정: 지정가는 '완전 체결'이면 FILLED·아니면(부분·미체결) OPEN(잔량은 limit 가격에 걸려 대기) / 시장가는 1건이라도 체결 FILLED·0건 REJECTED.
    public Mono<OrderResponse> placeOrder(CreateOrderRequest req, Long userId) {
        boolean isLimit = OrderType.LIMIT.name().equals(req.type());  // req.type()이 "LIMIT"이면 true(지정가), 아니면 false(시장가) — 비교 문자열은 typo-safe하게 enum에서

        // 최신 호가 확보. 없을 때(스트림 첫 메시지 전 찰나):
        //   · 시장가 → 체결 기준이 없어 즉시 체결 불가 → 503.
        //   · 지정가 → 실거래소처럼 호가와 무관하게 OPEN으로 걸어둔다(나중에 G단계 matcher가 평가). 그래서 tryFill 없이 바로 OPEN 저장.
        Optional<OrderBookSnapshot> maybeSnapshot = latestStore.latest();
        if (maybeSnapshot.isEmpty()) {
            if (!isLimit) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "호가 수신 전이라 체결할 수 없습니다."));
            }
            // saveOrder = 정해진 status·filledQty·fills로 '주문+체결을 DB 저장하고 요약 응답을 만드는' 공통 꼬리 메서드(아래 정의).
            //   여기선 호가가 없어 tryFill(체결 계산)을 건너뛰므로, 결과가 이미 정해져 있다 → status=OPEN·filledQty=0·fills=빈리스트로 바로 호출.
            //   (호가가 있는 일반 경로도 마지막엔 결국 이 saveOrder로 모인다 — 저장 로직을 한 곳에 두려고 분리한 것.)
            return saveOrder(req, userId, OrderStatus.OPEN.name(), BigDecimal.ZERO, List.of()); // 호가 없는 지정가 → OPEN(미체결)
        }

        // 엔진에 넘길 입력 주문(probe)을 요청 DTO로부터 만든다.
        //   · 왜 CreateOrderRequest를 그대로 안 넘기나: 엔진은 '순수 도메인 계산기'라 도메인 타입(PaperOrder)만 받는다(웹 DTO에 의존 X).
        //     엔진이 웹 경계 DTO에 엮이면 DB·웹 없이 도는 B단계 단위테스트가 깨진다 → 그래서 DTO를 PaperOrder로 '번역'해 넘긴다(계층 분리).
        //   · 왜 이름이 probe('떠보다')인가: '지금 호가에 어떻게 체결되나'를 떠보는 임시 입력이라서 — 저장본이 아니다.
        //     (저장본 toSave는 saveOrder에서 status·filledQty 확정해 따로 만든다.) status=NEW는 자리만(tryFill이 status는 안 읽음), id도 null(저장 시 부여).
        PaperOrder probe = new PaperOrder(
                null, userId, null, req.symbol(),
                req.side(), req.type(), OrderStatus.NEW.name(),
                req.limitPrice(), req.quantity(), BigDecimal.ZERO);


        // 엔진이란? 주문 1건(PaperOrder)과 지금 호가창(OrderBookSnapshot)을 인자로 받아, 실거래소의 매칭 규칙대로
        //   "어떻게 체결되는지"를 계산해 체결 내역(List<PaperFill>)을 return하는 순수 계산기다.
        //   DB·웹 같은 바깥은 일절 안 만지고 입력만 보고 답을 내며, 이 체결 계산이 곧 모의투자의 핵심 도메인 로직이다.
        // maybeSnapshot.get() — .get()은 Optional의 메서드(상자 열어 안의 OrderBookSnapshot 꺼냄). OrderBookSnapshot 자체엔 .get()이 없다.
        //   위 isEmpty() 체크를 통과한 뒤라 '비어있지 않음'이 보장돼 안전하게 꺼낸다.
        List<PaperFill> fills = engine.tryFill(probe, maybeSnapshot.get());
        BigDecimal filledQty = totalQuantity(fills);  // 체결 수량 합 (부분체결이면 주문 수량보다 작을 수 있다)

        // 체결 결과로 status를 정한다 — 시장가 vs 지정가가 '잔량(못 채운 수량)을 어떻게 보느냐'로 갈린다.
        boolean fullyFilled = filledQty.compareTo(req.quantity()) >= 0;  // 체결합 ≥ 주문수량 → 완전 체결(true). 부분·미체결이면 false.
        String status;                                                   // 아래 분기에서 정해질 상태 (FILLED / OPEN / REJECTED 중 하나)
        if (isLimit)  status = fullyFilled ? OrderStatus.FILLED.name() : OrderStatus.OPEN.name();        // 지정가: 다 차면 FILLED, 아니면(부분·미체결) OPEN — 잔량은 limit가에 걸려 대기(G단계 matcher가 채움)
        else          status = fills.isEmpty() ? OrderStatus.REJECTED.name() : OrderStatus.FILLED.name(); // 시장가: 0건이면 REJECTED, 1건이라도 체결되면 FILLED(잔량은 못 쉬어 드롭)

        return saveOrder(req, userId, status, filledQty, fills);         // 정해진 status·filledQty·fills로 주문+체결을 저장하고 요약 응답을 반환
    }

    // 현재 유저의 주문 목록을 최신순으로 조회해 응답 DTO로 바꾼다.
    //   findByUserIdOrderByIdDesc(userId)라 '남의 주문'은 쿼리(WHERE user_id=?)에서 원천 제외 — 이게 user_id 격리.
    //   '가벼운' 목록이라 avgPrice는 생략(null): fill을 조회해야 나오는 값인데, 주문마다 또 조회(N+1)하지 않으려고.
    //   그래서 OrderResponse.from에 빈 fills(List.of())를 넘긴다 → averagePrice([]) = null. (상세가 필요해지면 그때 채움)
    public Flux<OrderResponse> listOrders(Long userId) {
        return orderRepository.findByUserIdOrderByIdDesc(userId)
                // List.of() = List 팩토리 메서드로 만든 '빈 불변 리스트'(원소 0개·수정 불가). 여기선 'fill 없음'을 표현하는 빈 입력 → averagePrice([])가 null.
                .map(order -> OrderResponse.from(order, List.of())); // 각 주문 → OrderResponse (fill 조회 없이 변환, avgPrice=null)
    }

    // 결정된 status·filledQty·fills로 주문+체결을 DB에 저장하고 요약 응답을 만든다. (placeOrder의 공통 꼬리 — 시장가·지정가·OPEN 모두 여기로)
    //   PaperOrder는 record(불변)라, 결정된 status·filledQty를 담아 '저장용' 객체를 새로 만든다(복제+수정).
    private Mono<OrderResponse> saveOrder(CreateOrderRequest req, Long userId,
                                          String status, BigDecimal filledQty, List<PaperFill> fills) {
        PaperOrder toSave = new PaperOrder(
                null, userId, null, req.symbol(),
                req.side(), req.type(), status,
                req.limitPrice(), req.quantity(), filledQty);

        // save()는 ReactiveCrudRepository 내장. INSERT가 주 임무지만 반환은 Mono<PaperOrder> —
        //   DB가 BIGSERIAL로 만든 id가 채워진 '저장본'(saved)을 돌려준다. 그 saved.id()로 fill들을 연결한다.
        //   (OPEN 주문은 fills가 비어 saveFills가 그냥 Mono.empty()로 끝난다 → 체결 없이 주문만 저장)
        return orderRepository.save(toSave).flatMap(saved ->
                saveFills(saved.id(), fills)                          // 그 id로 fill들을 저장하고
                        .thenReturn(OrderResponse.from(saved, fills))); // 끝나면 요약 응답으로 변환해 흘려보냄
    }

    // 주문 1건이 낳은 체결(fill) N개를 paper_fills 테이블에 저장한다. (주문 : 체결 = 1 : N — 한 주문이 여러 가격에 쪼개져 체결되므로)
    //   매개변수 orderId = 방금 save로 저장돼 DB가 매긴 '주문의 고유번호(PK)'. (주문한 사람인 userId가 아님)
    //     → 각 fill의 order_id(외래키)에 이 번호를 박아 "이 체결은 이 주문 소속"임을 남긴다. (안 그러면 주문별 체결 조회 불가)
    //   fill 계산(엔진)은 주문 저장보다 먼저 일어난다 → 그 계산 시점엔 주문 id가 아직 없어서(저장돼야 DB가 id를 매김) fill의 orderId가 null이다.
    //   이제 주문이 저장돼 id가 생겼으니, 그 id를 박은 새 fill로 바꿔 '처음' 저장한다 — '다시 저장'이 아니다(fill은 그전까진 메모리 계산뿐).
    private Mono<Void> saveFills(Long orderId, List<PaperFill> fills) {
        if (fills.isEmpty()) return Mono.empty();   // 체결 0건이면 저장할 것도 없음 → 빈 '완료' Mono
        // orderId만 채운 새 PaperFill로 복제 (record라 기존 객체의 orderId를 못 고쳐서 새로 만든다).
        List<PaperFill> withOrderId = fills.stream()
                .map(f -> new PaperFill(null, orderId, f.accountId(), f.symbol(),
                        f.side(), f.price(), f.quantity(), f.fee()))
                .toList();
        // saveAll = save의 '여러 건' 버전. 둘 다 우리가 안 만든 ReactiveCrudRepository 내장 메서드.
        //   save(1건)→Mono<PaperFill> 이듯, saveAll(N건)→Flux<PaperFill> : 리스트의 각 fill을 INSERT하고
        //   (각자 id=null이라 DB가 id를 매겨) 저장본 0~N개를 흘려보낸다.
        //   여기선 그 저장본이 필요 없어(응답엔 메모리의 fills를 그대로 씀), .then()으로 값은 버리고
        //   "다 끝났다"는 완료 신호만 남긴 Mono<Void>로 바꾼다. (이 저장이 끝나야 다음 단계로 넘어가도록)
        return fillRepository.saveAll(withOrderId).then();
    }

    // fill들의 체결 수량 합 (= filled_quantity). BigDecimal이라 + 대신 add 누적.
    private static BigDecimal totalQuantity(List<PaperFill> fills) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PaperFill f : fills) sum = sum.add(f.quantity());
        return sum;
    }
}
