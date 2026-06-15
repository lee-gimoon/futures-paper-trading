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

// 9단계 계좌/포지션/PnL + 레버리지·마진 비즈니스 로직.
//   포지션·실현/미실현 PnL은 저장하지 않고 paper_fills에서 매번 계산한다(로드맵 9단계 방향).
//   저장하는 건 시드 현금·계좌 레버리지(paper_accounts)·주문별 레버리지(paper_orders)뿐.
//
// 레버리지 두 종류 구분 (사용자 요청):
//   · 계좌 레버리지(account.leverage) = '신규 주문'에 쓰는 현재 설정값(% 바·매수가능 검증·주문에 박는 값). 버튼이 바꾼다.
//   · 포지션 레버리지(positionLeverage) = '이미 연 포지션'이 진입 시점에 박아 둔 값. 사용증거금·청산가·가용잔고는 이걸로 계산해
//     버튼을 눌러도 안 흔들린다(격리 마진). paper_orders.leverage에서 PositionCalculator.openLeverage로 복원한다.
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
        return getOrCreateAccount(userId).flatMap(account ->
                Mono.zip(
                        fillRepository.findByUserIdOrderByIdAsc(userId).collectList(),
                        orderRepository.findByUserIdOrderByIdDesc(userId)
                                .collectMap(PaperOrder::id, PaperOrder::leverage)
                ).map(t -> toState(account, t.getT1(), t.getT2())));
    }

    // 계좌가 있으면 그대로, 없으면 시드 현금·기본 레버리지로 새로 만들어 저장한다(lazy 생성).
    private Mono<PaperAccount> getOrCreateAccount(Long userId) {
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

    // 계좌 + 체결 + 주문레버리지맵 → 마진 계산 값 묶음. 사용증거금/가용잔고는 '포지션 레버리지'로 계산한다(고정).
    private AccountState toState(PaperAccount account, List<PaperFill> fills, Map<Long, Integer> orderLeverage) {
        Position pos = PositionCalculator.compute(fills);
        int positionLeverage = PositionCalculator.openLeverage(fills, orderLeverage, account.leverage());
        BigDecimal mark = midPrice();
        boolean flat = pos.signedQuantity().signum() == 0;

        BigDecimal unrealized = (flat || mark == null)
                ? BigDecimal.ZERO
                : mark.subtract(pos.averageEntryPrice()).multiply(pos.signedQuantity());

        BigDecimal walletBalance = account.cashBalance().add(pos.realizedPnl());
        BigDecimal usedMargin = MarginCalculator.usedMargin(pos, positionLeverage);
        BigDecimal available = walletBalance.subtract(usedMargin).max(BigDecimal.ZERO);

        return new AccountState(account, pos, positionLeverage, mark, unrealized, walletBalance, usedMargin, available);
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

    // 마진 계산 중간값 묶음. (placeOrder의 매수가능 검증, LiquidationService의 청산 판정이 함께 쓴다)
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
    }
}
