package com.example.futurespapertrading.paper.service;
import com.example.futurespapertrading.market.domain.OrderBookQuotes;
import com.example.futurespapertrading.market.domain.OrderBookSnapshot;
import com.example.futurespapertrading.market.stream.LatestOrderBookSnapshotStore;
import com.example.futurespapertrading.paper.domain.MarginCalculator;
import com.example.futurespapertrading.paper.domain.PaperAccount;
import com.example.futurespapertrading.paper.domain.PaperFill;
import com.example.futurespapertrading.paper.domain.PaperOrder;
import com.example.futurespapertrading.paper.domain.Position;
import com.example.futurespapertrading.paper.domain.PositionCalculator;
import com.example.futurespapertrading.paper.exception.InvalidLeverageException;
import com.example.futurespapertrading.paper.dto.FillResponse;
import com.example.futurespapertrading.paper.dto.PortfolioResponse;
import com.example.futurespapertrading.paper.dto.PortfolioResponse.PositionView;
import com.example.futurespapertrading.paper.repository.PaperAccountRepository;
import com.example.futurespapertrading.paper.repository.PaperFillRepository;
import com.example.futurespapertrading.paper.repository.PaperOrderRepository;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

// PortfolioService: 사용자의 계좌/포지션 상태를 계산해 포트폴리오 응답과 주문 가능 증거금 검증 값을 만드는 서비스.
//   계좌 현금, 주문/fill 원장, 최신 호가를 모아 포지션·실현/미실현 PnL·마진·가용잔고를 계산하고 PortfolioResponse를 조립한다.
//   PaperOrderService는 새 주문을 받기 전에 accountState로 availableBalance를 확인해 필요한 증거금이 충분한지 검증한다.
//   포지션·PnL은 DB에 따로 저장하지 않고 paper_fills와 현재 호가로 매번 다시 계산한다. 저장하는 건 시드 현금·계좌 레버리지·주문별 레버리지뿐이다.
//
// 레버리지 두 종류 구분 (사용자 요청):
//   · 계좌 레버리지(account.leverage) = '신규 주문'에 쓰는 현재 설정값(% 바·매수가능 검증·주문에 박는 값). 버튼이 바꾼다.
//   · 포지션 레버리지(positionLeverage) = '이미 연 포지션'이 진입 시점에 박아 둔 값. 사용증거금·청산가·가용잔고는 이걸로 계산해
//     버튼을 눌러도 안 흔들린다(격리 마진). paper_orders.leverage에서 PositionCalculator.openPositionLeverage로 복원한다.
@Service
public class PortfolioService {

    private static final BigDecimal SEED_CASH = new BigDecimal("10000");
    private static final int DEFAULT_LEVERAGE = 10;
    private static final Set<Integer> ALLOWED_LEVERAGES = Set.of(1, 3, 5, 10, 20, 50);
    private static final String SYMBOL = "BTCUSDT";

    private final PaperAccountRepository accountRepository;
    private final PaperOrderRepository orderRepository;   // 주문별 레버리지(포지션 레버리지 복원용)
    private final PaperFillRepository fillRepository;
    private final LatestOrderBookSnapshotStore latestStore;

    public PortfolioService(PaperAccountRepository accountRepository,
                            PaperOrderRepository orderRepository,
                            PaperFillRepository fillRepository,
                            LatestOrderBookSnapshotStore latestStore) {
        this.accountRepository = accountRepository;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.latestStore = latestStore;
    }

    // 계좌 한 화면분(현금·PnL·equity·레버리지·마진·포지션)을 조립해 돌려준다.
    public Mono<PortfolioResponse> getPortfolio(Long userId) {
        return accountState(userId).map(this::buildPortfolio);
    }

    // 체결 내역(화면 표시용). 계산은 오름차순이 필요하지만 표시는 프론트에서 최신순으로 뒤집어 쓴다.
    public Flux<FillResponse> listFills(Long userId) {
        return fillRepository.findByUserIdOrderByIdAsc(userId)
                .map(FillResponse::from);
    }

    // 레버리지 변경 (UI 프리셋 값만 허용). '신규 주문'용 계좌 레버리지만 바꾼다 — 이미 연 포지션의 마진/청산가는 안 바뀐다.
    public Mono<PortfolioResponse> setLeverage(Long userId, int leverage) {
        if (!ALLOWED_LEVERAGES.contains(leverage))
            return Mono.error(new InvalidLeverageException("레버리지는 1, 3, 5, 10, 20, 50 중 하나여야 합니다."));
        return getOrCreateAccount(userId)
                .flatMap(account -> accountRepository.save(
                        new PaperAccount(account.id(), account.userId(), account.cashBalance(), leverage)))
                .flatMap(saved -> getPortfolio(userId));
    }

    // 매수가능 검증·강제청산이 쓰는 공통 계산: 계좌+체결+주문레버리지로 (포지션·포지션레버리지·가용잔고·mark)을 묶어 돌려준다.
    public Mono<AccountState> accountState(Long userId) {
        // 먼저 계좌(account)를 확보한 뒤, 그 계좌 값을 들고 체결 목록과 주문별 레버리지를 추가 조회한다.
        // 두 조회가 끝나면 account + 체결 목록 + 주문 레버리지로 AccountState를 만든다.
        return getOrCreateAccount(userId).flatMap(account ->
                // 이 람다 객체가 만들어지는 시점에는 Mono.zip(...)이 아직 실행/조립되지 않는다.
                // getOrCreateAccount에서 account 값이 실제로 흘러와 람다 본문이 호출될 때 Mono.zip(...) 파이프라인이 조립된다.
                // 조립된 직후 flatMap이 이 안쪽 Mono를 구독하면서 체결 조회와 주문 레버리지 조회가 실제로 진행된다.
                // Mono.zip(a, b): a와 b 두 비동기 조회가 모두 끝날 때까지 기다렸다가 결과를 한 묶음(Tuple)으로 합친다.
                Mono.zip(
                        // collectList(): 여러 개가 흘러오는 Flux<PaperFill>을 전부 모아 Mono<List<PaperFill>>로 바꾼다.
                        fillRepository.findByUserIdOrderByIdAsc(userId).collectList(),
                        // collectMap(key, value): 여러 주문을 읽어서 "주문 id를 key, 주문 레버리지를 value"로 하는 Map으로 모은다.
                        // 예: 주문 id 10번의 레버리지가 5배, 11번의 레버리지가 10배라면 {10=5, 11=10}
                        orderRepository.findByUserIdOrderByIdDesc(userId)
                                .collectMap(PaperOrder::id, PaperOrder::leverage)
                // Mono.zip(a, b)의 반환 타입은 Reactor가 정해둔 Mono<Tuple2<A, B>>라서 getT1()/getT2()로 꺼낸다.
                // map(): zip 결과 묶음에서 t.getT1()은 체결 목록, t.getT2()는 주문 레버리지 Map이다.
                ).map(t -> toState(account, t.getT1(), t.getT2())));
    }

    // 계좌가 있으면 그대로, 없으면 시드 현금·기본 레버리지로 새로 만들어 저장한다.
    private Mono<PaperAccount> getOrCreateAccount(Long userId) {
        // defer = "미루다/연기하다".
        // switchIfEmpty(...)에 accountRepository.save(new PaperAccount(...))를 바로 넘기면,
        // 계좌가 있든 없든 이 메서드가 호출되어 리액티브 흐름을 조립하는 순간 new PaperAccount(...)가 먼저 실행된다.
        // 계좌가 이미 있으면 대체 Mono는 구독되지 않으므로 실제 DB 저장은 되지 않지만,
        // 필요 없는 새 계좌 객체를 만들고 save(...)가 반환한 "저장용 Mono"까지 미리 준비하게 된다.
        // Mono.defer(...)로 감싸면 이 준비 작업을 계좌가 없을 때까지 미룬다.
        // 그래서 findByUserId(userId)가 비어 있을 때만 안쪽 람다가 실행되고,
        // 그때 새 PaperAccount를 만든 뒤 save(...)가 반환한 Mono로 대체 흐름을 시작한다.
        return accountRepository.findByUserId(userId)
                .switchIfEmpty(Mono.defer(() ->
                        accountRepository.save(new PaperAccount(null, userId, SEED_CASH, DEFAULT_LEVERAGE))));
    }

    // AccountState → 응답 DTO. 마진/청산가는 '포지션 레버리지'로, 화면의 신규주문 레버리지는 '계좌 레버리지'로 보여준다.
    private PortfolioResponse buildPortfolio(AccountState s) {
        boolean flat = s.position().signedQuantity().signum() == 0;
        BigDecimal equity = s.walletBalance().add(s.unrealizedPnl());

        PositionView view = flat ? null : new PositionView(
                SYMBOL,
                s.position().signedQuantity().signum() > 0 ? "LONG" : "SHORT",
                s.position().signedQuantity().abs(),
                s.position().averageEntryPrice(),
                s.mark(),
                s.unrealizedPnl(),
                MarginCalculator.notional(s.position()),
                MarginCalculator.liquidationPrice(s.position(), s.positionLeverage()),
                s.positionLeverage());

        return new PortfolioResponse(
                s.account().cashBalance(), s.position().realizedPnl(), s.unrealizedPnl(), equity,
                s.account().leverage(), s.usedMargin(), s.availableBalance(), view);
    }

    // toState(...)의 역할:
    //   계좌 정보(account), 시간순 체결 목록(fills), 주문별 레버리지 맵(orderLeverage)을 한데 모아
    //   현재 포지션·미실현손익·지갑잔고·사용증거금·가용잔고를 계산한 AccountState 스냅샷을 만든다.
    //   화면에 보여줄 포트폴리오 응답과 주문 전 매수가능 검증이 이 AccountState를 공통으로 사용한다.
    //   사용증거금/가용잔고 계산은 현재 계좌 레버리지가 아니라, 포지션 진입 시점에 고정된 '포지션 레버리지'를 기준으로 한다.
    private AccountState toState(PaperAccount account, List<PaperFill> fills, Map<Long, Integer> orderLeverage) {
        Position pos = PositionCalculator.compute(fills); // 체결 목록으로 현재 포지션(롱/숏 수량, 평균 진입가, 실현손익 등)을 계산한다.
        int positionLeverage = PositionCalculator.openPositionLeverage(fills, orderLeverage, account.leverage()); // 체결/주문 이력에서 열린 포지션의 레버리지를 복원한다.
        BigDecimal mark = midPrice(); // 현재 호가의 중간값(mid price)을 평가가격(mark)으로 가져온다. 호가가 없으면 null일 수 있다.
        boolean flat = pos.signedQuantity().signum() == 0; // signedQuantity가 0이면 현재 열린 롱/숏 포지션이 없는 상태(flat)다.

        BigDecimal unrealized = (flat || mark == null) // 포지션이나 mark가 없으면 0, 있으면 (현재가 - 평균 진입가) * 보유 수량으로 미실현손익을 계산한다.
                ? BigDecimal.ZERO
                : mark.subtract(pos.averageEntryPrice()).multiply(pos.signedQuantity());

        BigDecimal walletBalance = account.cashBalance().add(pos.realizedPnl()); // 저장된 현금잔고에 체결로 확정된 실현손익을 더한 지갑잔고다.
        BigDecimal usedMargin = MarginCalculator.usedMargin(pos, positionLeverage); // 현재 포지션 크기와 포지션 레버리지 기준으로 계좌에 묶인 사용증거금이다.
        BigDecimal available = walletBalance.subtract(usedMargin).max(BigDecimal.ZERO); // 지갑잔고에서 사용증거금을 뺀 가용잔고이며, 음수면 0으로 고정한다.

        return new AccountState(account, pos, positionLeverage, mark, unrealized, walletBalance, usedMargin, available); // 계산값들을 AccountState 상태 스냅샷으로 묶어 반환한다.
    }

    // 현재 보관 중인 최신 호가에서 mid = (bestBid + bestAsk) / 2. 호가 없거나 한쪽이 비면 null.
    private BigDecimal midPrice() {
        OrderBookSnapshot snapshot = latestStore.latest().orElse(null);
        if (snapshot == null) return null;
        BigDecimal bestBid = OrderBookQuotes.bestBid(snapshot);
        BigDecimal bestAsk = OrderBookQuotes.bestAsk(snapshot);
        if (bestBid == null || bestAsk == null) return null;
        return bestBid.add(bestAsk).divide(BigDecimal.valueOf(2));
    }

    // 계좌 상태를 담는 record. API 응답 DTO가 아니라 서비스 내부 마진(포지션을 열기 위해 계좌에서 묶어둔 투입 증거금) 계산용 중간 DTO/상태 스냅샷이다.
    // PortfolioService.accountState(...)가 만든 내부 계산 결과라 별도 DTO/domain 파일이 아니라 이 서비스 안에 둔다.
    // placeOrder의 매수가능 검증, LiquidationService의 청산 판정이 함께 쓴다.
    //   positionLeverage = 열린 포지션이 진입 시점에 박아 둔 레버리지 — 사용증거금·청산가·가용잔고가 이 값 기준.
    public record AccountState(
            PaperAccount account,
            Position position,
            int positionLeverage,
            BigDecimal mark,             // 현재 mid (null 가능)
            BigDecimal unrealizedPnl,
            BigDecimal walletBalance,    // 현금 + 실현PnL
            BigDecimal usedMargin,
            BigDecimal availableBalance  // walletBalance − usedMargin (0 미만이면 0)
    ) {
        // 예시 스냅샷:
        // new AccountState(
        //     new PaperAccount(1L, 7L, new BigDecimal("10000"), 10),
        //     new Position(new BigDecimal("0.1"), new BigDecimal("50000"), new BigDecimal("100")),
        //     10,
        //     new BigDecimal("52000"),
        //     new BigDecimal("200.0"),
        //     new BigDecimal("10100"),
        //     new BigDecimal("500.00000000"),
        //     new BigDecimal("9600.00000000")
        // );
        //
        // 의미:
        // - 7번 유저의 계좌 현금은 10000, 계좌 레버리지는 10x
        // - 현재 포지션은 BTC 롱 0.1개, 평균 진입가 50000
        // - 현재 mark가 52000이라 미실현손익은 (52000 - 50000) * 0.1 = 200
        // - 지갑잔고는 cashBalance 10000 + realizedPnl 100 = 10100
        // - 사용증거금은 50000 * 0.1 / 10 = 500
        // - 사용가능잔고는 10100 - 500 = 9600
    }

}
