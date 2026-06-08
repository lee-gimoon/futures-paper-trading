package com.example.futurespapertrading.paper;

import java.math.BigDecimal;     // 가격·수량·체결수량 정밀 계산 (double 금지 — 1원 오차도 손익)
import java.util.List;           // 체결(fill) 목록 타입

import org.springframework.http.HttpStatus;                                       // 201/400/503 등 HTTP 상태코드 모음
import org.springframework.security.core.context.ReactiveSecurityContextHolder;   // 현재 로그인 정보를 꺼내는 통로
import org.springframework.web.bind.annotation.PostMapping;                       // HTTP POST 요청을 이 메서드에 매핑
import org.springframework.web.bind.annotation.RequestBody;                       // 요청 JSON 본문을 자바 객체(DTO)로 변환해 받음
import org.springframework.web.bind.annotation.RequestMapping;                    // 클래스 공통 경로(/api/paper/orders) prefix 지정
import org.springframework.web.bind.annotation.ResponseStatus;                    // 성공 시 돌려줄 상태코드 지정 (여기선 201)
import org.springframework.web.bind.annotation.RestController;                    // REST 컨트롤러 표시 (반환값 → JSON)
import org.springframework.web.server.ResponseStatusException;                    // 예외로 HTTP 상태코드(400/503 등)를 바로 내려보냄

import com.example.futurespapertrading.auth.User;                          // email로 조회한 유저 엔티티 (id를 꺼내려고)
import com.example.futurespapertrading.auth.UserRepository;                // email → User 조회 (내 user_id 얻기)
import com.example.futurespapertrading.market.LatestOrderBookSnapshotStore; // 메모리에 보관된 최신 호가 snapshot 보관소
import com.example.futurespapertrading.market.OrderBookSnapshot;          // 한 시점 호가창 (체결 기준 입력)
import com.example.futurespapertrading.paper.dto.CreateOrderRequest;      // 주문 생성 요청 본문 DTO
import com.example.futurespapertrading.paper.dto.OrderResponse;           // 주문 결과 응답 DTO (요약)

import jakarta.validation.Valid;          // @RequestBody의 DTO 검증 트리거(@Pattern 등 위반 시 자동 400)
import reactor.core.publisher.Mono;       // 0~1개의 결과를 비동기로 흘려보내는 리액티브 상자

// 모의 주문 HTTP 입구. 현재는 POST /api/paper/orders 하나(시장가 즉시 체결, C단계).
//
// 처리 흐름:  현재 유저 확인 → 최신 호가 확보(없으면 503) → PaperTradingEngine.tryFill 로 체결 계산
//            → 상태 판정(FILLED/REJECTED) → 주문 저장 → 그 id로 체결(fill) 저장 → 요약 응답.
//   · 엔진은 "fill 목록"만 계산하는 순수 함수. "상태 결정 + DB 저장"이라는 부수효과는 이 컨트롤러가 맡는다.
//   · 비로그인 차단(401)은 SecurityConfig의 .anyExchange().authenticated()가 자동 처리 → 여기선 신경 안 써도 됨.
//
// ⚠️ 트랜잭션: 지금은 주문 저장과 fill 저장이 별도라, 둘 사이에서 실패하면 주문만 남을 수 있다.
//    MVP라 단순하게 두지만, 원자성이 필요해지면 @Transactional(리액티브) 또는 서비스 계층으로 묶는다.
@RestController
@RequestMapping("/api/paper/orders")
public class PaperOrderController {

    // ── 의존성 주입: 스프링이 생성자로 채워준다 ──
    private final PaperTradingEngine engine;                 // 순수 체결 계산기 (B단계)
    private final PaperOrderRepository orderRepository;      // 주문 저장/조회
    private final PaperFillRepository fillRepository;        // 체결 저장/조회
    private final LatestOrderBookSnapshotStore latestStore;  // 메모리에 보관된 최신 호가 snapshot
    private final UserRepository userRepository;             // email → 유저(=내 user_id) 조회

    public PaperOrderController(PaperTradingEngine engine,
                                PaperOrderRepository orderRepository,
                                PaperFillRepository fillRepository,
                                LatestOrderBookSnapshotStore latestStore,
                                UserRepository userRepository) {
        this.engine = engine;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.latestStore = latestStore;
        this.userRepository = userRepository;
    }

    // POST /api/paper/orders — 주문 생성. 성공 시 201 Created + OrderResponse(JSON).
    //   @Valid = CreateOrderRequest의 검증 규칙 위반 시 스프링이 자동 400 (여기 도달 전 차단).
    //   반환 Mono<OrderResponse> = "나중에 OrderResponse 1개를 흘려보낼 약속". 이 메서드는 파이프라인만 짜서 return하고,
    //     실제 실행은 WebFlux가 이 Mono를 구독(subscribe)할 때 일어난다. (스트림의 '게으름'과 같은 원리)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req) {
        // C단계는 시장가만. 지정가(LIMIT)는 미체결분 OPEN 등록(E단계)이 아직 없어 받지 않는다.
        if (!"MARKET".equals(req.type())) {
            // Mono.error(예외) = "값 대신 에러를 흘려보내는 Mono". WebFlux가 이 에러를 받아 ResponseStatusException의
            //   상태코드(400)로 응답을 만든다. (리액티브 흐름에선 throw 대신 에러도 Mono에 실어 보낸다)
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "현재 시장가(MARKET)만 지원합니다. 지정가는 추후 단계."));
        }
        // currentUserId()는 Mono<Long>(아직 안 온 user_id). flatMap = 그 값이 준비되면 placeMarketOrder로 이어 실행.
        //   placeMarketOrder도 Mono를 돌려주므로 flatMap(평탄화)을 쓴다 — map이면 Mono<Mono<OrderResponse>>가 돼버린다.
        return currentUserId().flatMap(userId -> placeMarketOrder(req, userId));
    }

    // 현재 로그인 유저의 user_id. (SecurityContext의 이름=email → DB에서 유저 조회 → id)
    //   AuthController.me()와 같은 패턴. 인증 필수 경로라 여기 닿으면 항상 로그인 상태다.
    //   map vs flatMap 구분: 변환 결과가 '그냥 값'이면 map, '또 다른 Mono'면 flatMap을 쓴다.
    //     (flatMap을 안 쓰면 Mono 안에 Mono가 겹쳐 Mono<Mono<User>>처럼 돼버린다 → flatMap이 한 겹으로 펴줌)
    private Mono<Long> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName()) // 식별자(email) — 결과가 String(값)이라 map
                .flatMap(userRepository::findByEmail)          // email → Mono<User> — 결과가 Mono라 flatMap
                .map(User::id);                                // User → user_id(Long) — 결과가 값이라 map
    }

    private Mono<OrderResponse> placeMarketOrder(CreateOrderRequest req, Long userId) {
        // 최신 호가가 아직 없으면(스트림 켜기 전/첫 메시지 전) 체결 기준이 없으므로 503.
        OrderBookSnapshot snapshot = latestStore.latest().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "호가 수신 전이라 체결할 수 없습니다."));

        // 주문 1건을 만들어 엔진에 넘긴다. status는 NEW로 두지만 tryFill은 status를 안 읽으므로 자리만 채우는 값.
        //   (id도 아직 null — DB에 저장될 때 부여됨)
        PaperOrder order = new PaperOrder(
                null, userId, null, req.symbol(),
                req.side(), req.type(), OrderStatus.NEW.name(),
                req.limitPrice(), req.quantity(), BigDecimal.ZERO);

        List<PaperFill> fills = engine.tryFill(order, snapshot);

        // 시장가 규칙: 한 건이라도 체결되면 FILLED(부분체결도 그대로 완료), 0건이면 REJECTED.
        BigDecimal filledQty = totalQuantity(fills);
        String status = fills.isEmpty() ? OrderStatus.REJECTED.name() : OrderStatus.FILLED.name();

        // 최종 상태/체결수량을 담은 '저장용' 주문을 만든다. PaperOrder는 record(불변)라 위 order의 status를
        //   못 바꾸므로, 결정된 status·filledQty를 담아 새 객체로 만드는 것. (record의 '복제+수정'이 정석)
        PaperOrder toSave = new PaperOrder(
                null, userId, null, req.symbol(),
                req.side(), req.type(), status,
                req.limitPrice(), req.quantity(), filledQty);

        // save(toSave) → Mono<PaperOrder>. flatMap의 saved = DB가 id를 채워 돌려준 '저장본'.
        //   그 saved.id()로 fill들을 저장하고(saveFills), 다 끝나면 thenReturn으로 요약 응답을 흘려보낸다.
        return orderRepository.save(toSave).flatMap(saved ->
                saveFills(saved.id(), fills)                          // 그 id로 fill들을 저장하고
                        .thenReturn(OrderResponse.from(saved, fills))); // 끝나면 요약 응답으로 변환해 흘려보냄
    }

    // 엔진이 만든 fill들은 orderId=null 상태(주문 저장 전 계산이라). 저장된 주문 id에 맞춰 다시 만들어 저장한다.
    //   (PaperFill은 record라 값을 못 바꿔, orderId만 채운 새 객체로 복제)
    private Mono<Void> saveFills(Long orderId, List<PaperFill> fills) {
        if (fills.isEmpty()) return Mono.empty();   // 체결 0건이면 저장할 것도 없음 → 빈 '완료' Mono
        // orderId만 채운 새 PaperFill로 복제 (record라 기존 객체의 orderId를 못 고쳐서 새로 만든다).
        List<PaperFill> withOrderId = fills.stream()
                .map(f -> new PaperFill(null, orderId, f.accountId(), f.symbol(),
                        f.side(), f.price(), f.quantity(), f.fee()))
                .toList();
        // saveAll(여러건) → Flux<PaperFill>(저장된 것들). .then() = 그 값들은 버리고 "다 끝났다"는 Mono<Void>로 바꿈.
        return fillRepository.saveAll(withOrderId).then();
    }

    // fill들의 체결 수량 합 (= filled_quantity). BigDecimal이라 + 대신 add 누적.
    private static BigDecimal totalQuantity(List<PaperFill> fills) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PaperFill f : fills) sum = sum.add(f.quantity());
        return sum;
    }
}
