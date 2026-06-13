package com.example.futurespapertrading.paper.service;
import com.example.futurespapertrading.paper.domain.PaperOrder;
import com.example.futurespapertrading.paper.domain.OrderSide;
import com.example.futurespapertrading.paper.domain.OrderStatus;
import com.example.futurespapertrading.paper.repository.PaperOrderRepository;
import com.example.futurespapertrading.paper.exception.InsufficientMarginException;
import com.example.futurespapertrading.paper.exception.OrderForbiddenException;
import com.example.futurespapertrading.paper.exception.OrderNotFoundException;
import com.example.futurespapertrading.paper.exception.OrderNotOpenException;
import com.example.futurespapertrading.paper.domain.OrderType;
import com.example.futurespapertrading.paper.domain.PaperTradingEngine;
import com.example.futurespapertrading.paper.domain.PaperFill;
import com.example.futurespapertrading.paper.repository.PaperFillRepository;
import com.example.futurespapertrading.paper.exception.QuoteUnavailableException;

import com.example.futurespapertrading.market.stream.LatestOrderBookSnapshotStore;
import com.example.futurespapertrading.market.domain.OrderBookSnapshot;
import com.example.futurespapertrading.paper.dto.CreateOrderRequest;
import com.example.futurespapertrading.paper.dto.OrderResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

// 모의 주문의 '주문 처리(place)·조회(list)·취소(cancel) 비즈니스 로직' 계층. 컨트롤러(PaperOrderController)가 HTTP를 받아 이 서비스에 위임한다.
//   (C~E단계까진 컨트롤러가 직접 했지만, F의 취소·G의 matcher가 같은 체결·저장 로직을 재사용하게 되어 서비스로 추출했다.)
//
// 처리 흐름:  최신 호가 확보 → PaperTradingEngine.tryFill 로 체결 계산
//            → 상태 판정(FILLED / OPEN[지정가 미체결] / REJECTED[시장가 미체결]) → 주문 저장 → 그 id로 체결(fill) 저장 → 요약 응답.
//   · 엔진은 "fill 목록"만 계산하는 순수 함수. "상태 결정 + DB 저장"이라는 부수효과는 이 서비스가 맡는다.
//
// public API vs private 헬퍼: 이 클래스를 처음 여는 사람의 질문은 "이 서비스가 뭘 해주지?"다.
//   그 답이 public API 4개(placeOrder·listOrders·cancel·matchOpenOrders) — 이것만 읽으면 파악이 끝난다.
//   private 헬퍼(fillOpenOrder·saveOrder·saveFills·totalQuantity)는 그 일을 "어떻게 하는지"의 내부 부품 — 궁금할 때만 내려가 읽는다.
//   변경의 자유도가 다르다: 본문(구현)은 둘 다 자유지만, 시그니처(이름·매개변수·반환타입)와 약속한 동작을 바꿀 때
//   public은 호출자(컨트롤러·G matcher)와 함께 바꿔야 하고(변경 비용이 파일 밖으로 번짐), private은 그마저 자유(밖에선 존재도 안 보임).
//   그래서 정렬도 public API가 위, private 헬퍼가 아래.
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
    private final OpenOrderCounter openOrderCounter;         // 지금 OPEN이 몇 건인지 — matcher의 skip 판정용 (G-1)
    private final PortfolioService portfolioService;         // 매수가능 검증용 계좌/포지션/가용잔고 계산 (9단계 마진)

    public PaperOrderService(PaperTradingEngine engine,
                             PaperOrderRepository orderRepository,
                             PaperFillRepository fillRepository,
                             LatestOrderBookSnapshotStore latestStore,
                             OpenOrderCounter openOrderCounter,
                             PortfolioService portfolioService) {
        this.engine = engine;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.latestStore = latestStore;
        this.openOrderCounter = openOrderCounter;
        this.portfolioService = portfolioService;
    }

    // 검증 통과한(@Valid 통과) 주문 1건을 받아: ① 지금 호가에 대고 '얼마나 체결되나' 평가 → ② 그 결과로 상태(FILLED/OPEN/REJECTED) 판정 → ③ 주문·체결을 DB 저장 → ④ 요약(OrderResponse)을 돌려준다.
    //   이름대로 '주문을 넣는(place an order)' 핵심 로직. 시장가·지정가 둘 다 이 메서드를 거치고, 둘의 차이는 ②의 상태 판정뿐이다.
    //   반환이 Mono<OrderResponse>라 여기선 파이프라인만 엮어 return하고, 실제 실행은 WebFlux가 구독할 때 일어난다.
    //   create()가 입력검증·userId를 걸러 넘기므로, 여기선 '체결 판정 + 저장'에만 집중한다.
    //   상태 판정: 지정가는 '완전 체결'이면 FILLED·아니면(부분·미체결) OPEN(잔량은 limit 가격에 걸려 대기) / 시장가는 1건이라도 체결 FILLED·0건 REJECTED.
    public Mono<OrderResponse> placeOrder(CreateOrderRequest req, Long userId) {
        // 레버리지 매수가능 검증(9단계 마진): 새로 여는/늘리는 주문의 필요 증거금이 가용잔고를 넘으면 거부(400).
        //   순수 축소/청산 주문은 필요 증거금 0이라 항상 통과. 통과하면 원래 처리(placeOrderChecked)로 넘어간다.
        return portfolioService.accountState(userId).flatMap(state -> {
            BigDecimal required = requiredOpeningMargin(req, state);
            if (required.compareTo(state.availableBalance()) > 0)
                return Mono.<OrderResponse>error(new InsufficientMarginException(
                        "가용 증거금 부족: 필요 " + required + " > 가용 " + state.availableBalance()
                                + " (레버리지 " + state.account().leverage() + "x)"));
            return placeOrderChecked(req, userId, state.account().leverage());
        });
    }

    // 새로 여는(늘리는) 수량에 필요한 증거금 = 여는수량 × 기준가 / 레버리지.
    //   기준가: 지정가는 limitPrice, 시장가는 현재 mid. 시장가인데 호가 없으면 0(검증 스킵 — 뒤에서 503).
    //   여는수량: 같은 방향/신규면 전량, 반대 방향이면 축소분을 뺀 '뒤집혀 새로 열리는 초과분'만(순수 축소는 0).
    private static BigDecimal requiredOpeningMargin(CreateOrderRequest req, PortfolioService.AccountState state) {
        boolean isLimit = OrderType.LIMIT.name().equals(req.type());
        BigDecimal refPrice = isLimit ? req.limitPrice() : state.mark();
        if (refPrice == null) return BigDecimal.ZERO;

        int orderDir = OrderSide.BUY.name().equals(req.side()) ? 1 : -1;
        BigDecimal currentQty = state.position().signedQuantity();
        BigDecimal openingQty = (currentQty.signum() == 0 || currentQty.signum() == orderDir)
                ? req.quantity()                                             // 신규/같은 방향 증가 → 전량
                : req.quantity().subtract(currentQty.abs()).max(BigDecimal.ZERO); // 반대 방향 → 축소분 제외한 초과분만

        if (openingQty.signum() <= 0) return BigDecimal.ZERO;                // 순수 축소/청산 → 증거금 불필요
        return openingQty.multiply(refPrice)
                .divide(BigDecimal.valueOf(state.account().leverage()), 8, RoundingMode.HALF_UP);
    }

    // 매수가능 검증을 통과한 뒤의 원래 주문 처리: 체결 평가 → 상태 판정(FILLED/OPEN/REJECTED) → 주문·체결 저장.
    //   leverage = 주문 시점 계좌 레버리지. 주문에 박아 저장해, 포지션이 진입 시점 레버리지를 들고 가게 한다(이후 버튼으로 바꿔도 이 포지션은 불변).
    private Mono<OrderResponse> placeOrderChecked(CreateOrderRequest req, Long userId, int leverage) {
        boolean isLimit = OrderType.LIMIT.name().equals(req.type());  // req.type()이 "LIMIT"이면 true(지정가), 아니면 false(시장가) — 비교 문자열은 typo-safe하게 enum에서

        // 최신 호가 확보. 없을 때(스트림 첫 메시지 전 찰나):
        //   · 시장가 → 체결 기준이 없어 즉시 체결 불가 → 503.
        //   · 지정가 → 실거래소처럼 호가와 무관하게 OPEN으로 걸어둔다(나중에 G단계 matcher가 평가). 그래서 tryFill 없이 바로 OPEN 저장.
        //   · 여기서 받아간 snapshot은 그 시점에 박제된 불변 객체 — 아래 tryFill 계산 도중 store가
        //     100ms마다 새 snapshot으로 교체돼도, 이 주문은 끝까지 같은 시점 호가 기준으로 계산된다.
        Optional<OrderBookSnapshot> maybeSnapshot = latestStore.latest();
        if (maybeSnapshot.isEmpty()) {
            if (!isLimit) {
                // 도메인 예외 → PaperExceptionHandler가 503으로 번역. (F-2 전엔 웹 타입 ResponseStatusException을 직접 던졌다)
                return Mono.error(new QuoteUnavailableException("호가 수신 전이라 체결할 수 없습니다."));
            }
            // saveOrder = 정해진 status·filledQty·fills로 '주문+체결을 DB 저장하고 요약 응답을 만드는' 공통 꼬리 메서드(아래 정의).
            //   여기선 호가가 없어 tryFill(체결 계산)을 건너뛰므로, 결과가 이미 정해져 있다 → status=OPEN·filledQty=0·fills=빈리스트로 바로 호출.
            //   (호가가 있는 일반 경로도 마지막엔 결국 이 saveOrder로 모인다 — 저장 로직을 한 곳에 두려고 분리한 것.)
            return saveOrder(req, userId, OrderStatus.OPEN.name(), BigDecimal.ZERO, List.of(), leverage); // 호가 없는 지정가 → OPEN(미체결)
        }

        // 엔진에 넘길 입력 주문(probe)을 요청 DTO로부터 만든다.
        //   · 왜 CreateOrderRequest를 그대로 안 넘기나: 엔진은 '순수 도메인 계산기'라 도메인 타입(PaperOrder)만 받는다(웹 DTO에 의존 X).
        //     엔진이 웹 경계 DTO에 엮이면 DB·웹 없이 도는 B단계 단위테스트가 깨진다 → 그래서 DTO를 PaperOrder로 '번역'해 넘긴다(계층 분리).
        //   · 왜 이름이 probe('떠보다')인가: '지금 호가에 어떻게 체결되나'를 떠보는 임시 입력이라서 — 저장본이 아니다.
        //     (저장본 toSave는 saveOrder에서 status·filledQty 확정해 따로 만든다.) status=NEW는 자리만(tryFill이 status는 안 읽음), id도 null(저장 시 부여).
        PaperOrder probe = new PaperOrder(
                null, userId, req.symbol(),
                req.side(), req.type(), OrderStatus.NEW.name(),
                req.limitPrice(), req.quantity(), BigDecimal.ZERO, leverage);


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

        return saveOrder(req, userId, status, filledQty, fills, leverage); // 정해진 status·filledQty·fills로 주문+체결을 저장하고 요약 응답을 반환
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

    // 주문 1건 취소 (F단계). 검증 순서가 곧 상태코드 순서 —
    //   ① 그 id(orderId)의 주문이 DB에 없으면 404(Not Found — 요청한 자원이 존재하지 않음)
    //   ② 주문은 있지만 주문의 user_id ≠ 요청한 유저(userId), 즉 남의 주문이면 403(Forbidden — 누군지는 알지만 이 자원을 건드릴 권한이 없음)
    //   ③ 내 주문이지만 status가 OPEN이 아니면(이미 FILLED/CANCELED/REJECTED로 끝난 주문) 409(Conflict — 자원의 현재 상태와 요청이 충돌)
    //   셋 다 통과하면 CANCELED로 저장.
    //   "주문이 존재하나 → 내 것인가 → 취소 가능한 상태인가" — 주문을 찾아야 소유자도 상태도 볼 수 있으니 이 순서가 논리적으로 강제된다.
    //   여기서 던진 도메인 예외는 PaperExceptionHandler가 받아 상태코드로 번역한다(서비스는 HTTP를 모름).
    public Mono<OrderResponse> cancel(Long orderId, Long userId) {
        return orderRepository.findById(orderId)
                // switchIfEmpty = 업스트림이 '빈 채로 완료'(해당 행 없음)면 대체 Mono로 갈아탐 — placeOrder의 Optional.isEmpty() 체크의 리액티브판
                .switchIfEmpty(Mono.error(new OrderNotFoundException("주문이 없습니다: id=" + orderId)))
                .flatMap(order -> {
                    if (!order.userId().equals(userId))
                        return Mono.error(new OrderForbiddenException("내 주문이 아닙니다."));
                    // 취소는 OPEN(대기 중)일 때만 가능 — 취소란 '아직 남아있는 체결 가능성'을 없애는 행위인데,
                    //   그 가능성이 살아있는 상태가 OPEN뿐이라서(matcher가 WHERE status='OPEN'만 재평가).
                    //   FILLED(이미 체결)·CANCELED(이미 취소)·REJECTED(이미 거부)는 종료 상태 — 없앨 가능성 자체가 없어 409.
                    if (!OrderStatus.OPEN.name().equals(order.status()))
                        return Mono.error(new OrderNotOpenException("OPEN 상태가 아니라 취소할 수 없습니다: " + order.status()));
                    // 위 OPEN 검사를 통과해도, 이 줄에 닿기 전 matcher가 막 체결시켰을 수 있다(race) — 그래서 마지막
                    //   쓰기는 save(무조건 덮어쓰기)가 아니라 조건부 UPDATE(G-3). 0행 = 진 것: FILLED를 덮어쓰지 않고 409.
                    //   status만 바꾸는 cancelIfOpen인 이유(왜 filled_quantity를 안 넘기나)는 repository 주석 참고.
                    return orderRepository.cancelIfOpen(order.id())
                            .flatMap(rows -> {
                                if (rows == 0)
                                    return Mono.error(new OrderNotOpenException("취소하는 사이 체결이 완료됐습니다."));
                                openOrderCounter.decrement(); // OPEN 1건이 CANCELED로 종료 (G-1)
                                // UPDATE는 갱신된 행을 돌려주지 않으므로, 응답용으로 같은 내용의 복제본을 만든다(record라 복제+수정).
                                return Mono.just(new PaperOrder(
                                        order.id(), order.userId(), order.symbol(),
                                        order.side(), order.type(), OrderStatus.CANCELED.name(),
                                        order.limitPrice(), order.quantity(), order.filledQuantity(), order.leverage()));
                            });
                })
                .map(updated -> OrderResponse.from(updated, List.of())); // 취소엔 새 체결이 없으니 빈 fills → avgPrice=null
        // ── 위에서 Mono.error로 흘린 에러는 어떻게 핸들러까지 가나 ──
        //   Mono.error는 throw가 아니라 스트림에 '에러 신호(onError)'를 흘리는 것. 두 방식의 이동 경로가 다르다:
        //   · throw 방식        : 발생 지점 → 콜스택을 거슬러 올라 catch로 점프 (중간 코드 건너뜀)
        //   · 리액티브 방식(이것) : 에러 신호 → 다운스트림을 따라 흘러내려 → 체인 끝의 구독자가 받음
        //   위 flatMap·map은 정상 값(onNext)일 때만 람다를 실행하고, 에러 신호는 람다를 건너뛰고 그대로 통과시킨다
        //   → 주문이 없으면(404) 소유자·상태 검사도, 저장도, 응답 변환도 실행되지 않음이 구조적으로 보장된다.
        //   체인의 최종 구독자는 WebFlux — onError를 수신하는 순간 "이 예외 타입에 맞는 @ExceptionHandler가 있나?"를 찾아
        //   PaperExceptionHandler의 매핑으로 상태코드+본문 응답을 만든다. (정상 경로면 onNext의 OrderResponse가 200 본문이 된다)
        //   ※ 중간에 onErrorResume 같은 복구 연산자를 끼우면 끝에 닿기 전에 가로챌 수도 있다 — 여기선 복구할 게 없어 끝까지 흘려보낸다.
    }

    // 대기(OPEN) 주문 전부를 새 snapshot에 대고 재평가한다 (G단계 — matcher가 snapshot마다 1회 호출하는 진입점).
    //   concatMap = 주문도 한 건씩 차례로(앞 주문의 갱신·저장이 끝나야 다음 주문). then() = 값은 버리고
    //   "이 snapshot 처리 끝"이라는 완료 신호만 남김 — matcher의 concatMap이 이 신호를 받아야 다음 snapshot으로 넘어간다.
    public Mono<Void> matchOpenOrders(OrderBookSnapshot snapshot) {
        return orderRepository.findByStatus(OrderStatus.OPEN.name())
                .concatMap(order -> fillOpenOrder(order, snapshot))
                .then();
    }

    // OPEN 주문 1건을 snapshot에 대고 재평가한다.
    // 가격이 닿으면 placeOrder처럼 tryFill→판정→저장 흐름을 타되,
    // 새 주문을 INSERT하지 않고 기존 OPEN 주문을 조건부 UPDATE한 뒤 fill을 INSERT한다.
    // 이름 의미: fillOpenOrder = 아직 체결 대기 중인 OPEN 주문 하나를 현재 호가 snapshot 기준으로 체결 가능한 만큼 처리한다.
    private Mono<Void> fillOpenOrder(PaperOrder order, OrderBookSnapshot snapshot) {
        // probe 수량은 주문 전량이 아니라 '잔량' — placeOrder에서 부분 체결된 채 OPEN으로 남은 주문을
        //   전량으로 재평가하면 이미 체결된 몫까지 또 체결돼 과체결되기 때문.
        BigDecimal remaining = order.quantity().subtract(order.filledQuantity());
        PaperOrder probe = new PaperOrder(
                order.id(), order.userId(), order.symbol(),
                order.side(), order.type(), order.status(),
                order.limitPrice(), remaining, BigDecimal.ZERO, order.leverage());

        // 현재 snapshot 기준으로 이 OPEN 주문이 체결될 수 있는지 계산한다. 결과는 실제 저장 전의 fill 후보 목록이다.
        List<PaperFill> fills = engine.tryFill(probe, snapshot);

        // 체결 후보가 없으면 아직 가격이 닿지 않은 상태다. 주문은 OPEN 그대로 두고 DB에는 아무것도 쓰지 않는다.
        if (fills.isEmpty()) return Mono.empty();

        // 기존 누적 체결 수량에 이번 snapshot에서 새로 체결 가능한 수량을 더해 최신 누적 체결 수량을 만든다.
        BigDecimal newFilledQty = order.filledQuantity().add(totalQuantity(fills));

        // 최신 누적 체결 수량이 원 주문 수량 이상이면 전량 체결, 작으면 부분 체결이다.
        boolean fullyFilled = newFilledQty.compareTo(order.quantity()) >= 0;

        // 전량 체결이면 FILLED로 닫고, 잔량이 남은 부분 체결이면 OPEN으로 유지해 다음 snapshot에서 계속 재평가한다.
        String status = fullyFilled ? OrderStatus.FILLED.name() : OrderStatus.OPEN.name();

        // 아직 OPEN인 주문이면 체결 수량과 상태를 DB에 반영한다.
        // 전량 체결이면 OPEN 카운터를 줄이고, 마지막으로 실제 체결 내역(fill)을 저장한다.
        // 조건부 갱신(G-3): tryFill 계산 사이 사용자가 취소했으면(status≠OPEN) 0행 — 체결을 포기한다.
        // fill 저장은 UPDATE 성공 뒤에만 한다. 졌는데 fill부터 저장하면 주문은 CANCELED인데 fill만 남는 모순이 생긴다.
        return orderRepository.updateIfOpen(order.id(), status, newFilledQty)
                .flatMap(rows -> {
                    if (rows == 0) return Mono.empty();
                    if (fullyFilled) openOrderCounter.decrement(); // OPEN 1건이 FILLED로 종료 (G-1)
                    return saveFills(order.id(), fills);
                });
    }

    // 결정된 status·filledQty·fills로 주문+체결을 DB에 저장하고 요약 응답을 만든다. (placeOrder의 공통 꼬리 — 시장가·지정가·OPEN 모두 여기로)
    //   PaperOrder는 record(불변)라, 결정된 status·filledQty를 담아 '저장용' 객체를 새로 만든다(복제+수정).
    private Mono<OrderResponse> saveOrder(CreateOrderRequest req, Long userId,
                                          String status, BigDecimal filledQty, List<PaperFill> fills, int leverage) {
        PaperOrder toSave = new PaperOrder(
                null, userId, req.symbol(),
                req.side(), req.type(), status,
                req.limitPrice(), req.quantity(), filledQty, leverage);

        // save()는 ReactiveCrudRepository 내장 — 주문을 INSERT하고, DB가 id를 매긴 '저장본'(saved)을 돌려준다.
        //   (OPEN 주문은 fills가 비어 saveFills가 Mono.empty()로 즉시 끝남 → 주문만 저장)
        return orderRepository.save(toSave)
                // OPEN으로 저장됐으면 카운터 +1 (G-1). INSERT가 '성공한 뒤'에 세야 실패한 주문을 세지 않는다.
                .doOnNext(saved -> { if (OrderStatus.OPEN.name().equals(status)) openOrderCounter.increment(); })
                .flatMap(saved ->
                saveFills(saved.id(), fills) // 그 id를 박은 fills를 '처음' 저장하고
                        .thenReturn(OrderResponse.from(saved, fills)));
                        // thenReturn: 업스트림(Mono<Void>) 값은 안 받고 완료 신호만 기다렸다가, 미리 만들어 둔 이 값을 대신 흘려보냄.
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
                .map(f -> new PaperFill(null, orderId, f.symbol(),
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
