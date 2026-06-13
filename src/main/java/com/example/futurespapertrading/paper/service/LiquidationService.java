package com.example.futurespapertrading.paper.service;
import com.example.futurespapertrading.paper.domain.MarginCalculator;
import com.example.futurespapertrading.paper.domain.OrderSide;
import com.example.futurespapertrading.paper.domain.OrderStatus;
import com.example.futurespapertrading.paper.domain.OrderType;
import com.example.futurespapertrading.paper.domain.PaperFill;
import com.example.futurespapertrading.paper.domain.PaperOrder;
import com.example.futurespapertrading.paper.domain.Position;
import com.example.futurespapertrading.paper.repository.PaperAccountRepository;
import com.example.futurespapertrading.paper.repository.PaperFillRepository;
import com.example.futurespapertrading.paper.repository.PaperOrderRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

// 자동 강제청산(9단계 레버리지). 현재 mark가 청산가를 넘긴 포지션을 찾아 반대방향 시장가로 강제 청산한다.
//   청산은 "주문 1건 + 체결 1건"을 청산가에 만들어 포지션을 닫는다 → 실현 PnL이 −usedMargin으로 확정된다.
//   LiquidationMonitor가 snapshot마다(샘플링) runOnce()를 호출한다.
@Service
public class LiquidationService {

    private static final Logger log = LoggerFactory.getLogger(LiquidationService.class);
    private static final String SYMBOL = "BTCUSDT";

    private final PortfolioService portfolioService;     // 계좌별 포지션·레버리지·mark 계산 재사용
    private final PaperAccountRepository accountRepository;
    private final PaperOrderRepository orderRepository;
    private final PaperFillRepository fillRepository;

    public LiquidationService(PortfolioService portfolioService,
                              PaperAccountRepository accountRepository,
                              PaperOrderRepository orderRepository,
                              PaperFillRepository fillRepository) {
        this.portfolioService = portfolioService;
        this.accountRepository = accountRepository;
        this.fillRepository = fillRepository;
        this.orderRepository = orderRepository;
    }

    // 모든 계좌를 한 번 훑어 청산 대상이면 강제 청산한다. (concatMap = 한 계좌씩 직렬 처리 — 동시 쓰기 회피)
    //   MVP라 매 호출마다 전 계좌를 본다(계좌 수에 비례). 사용자가 많아지면 '포지션 보유 계좌' 인덱스로 최적화.
    public Mono<Void> runOnce() {
        return accountRepository.findAll()
                .concatMap(account -> portfolioService.accountState(account.userId())
                        .flatMap(state -> {
                            // 청산 판정·청산가는 '포지션 레버리지'(진입 시점 고정) 기준 — 계좌 레버리지 변경과 무관.
                            if (!MarginCalculator.isLiquidated(state.position(), state.positionLeverage(), state.mark()))
                                return Mono.empty();
                            return liquidate(account.userId(), state.position(), state.positionLeverage());
                        }))
                .then();
    }

    // 포지션을 청산가에 반대방향 시장가로 강제 청산: 주문(FILLED) + 체결을 만들어 닫는다.
    private Mono<Void> liquidate(Long userId, Position pos, int leverage) {
        int dir = pos.signedQuantity().signum();                                  // +롱 / -숏
        String side = dir > 0 ? OrderSide.SELL.name() : OrderSide.BUY.name();     // 롱은 매도로, 숏은 매수로 청산
        BigDecimal qty = pos.signedQuantity().abs();
        BigDecimal liqPrice = MarginCalculator.liquidationPrice(pos, leverage);

        // 청산 closing order(이미 체결된 상태로 기록) → 그 id로 체결 1건 저장. 포지션은 이 체결로 flat이 된다.
        PaperOrder closeOrder = new PaperOrder(
                null, userId, SYMBOL,
                side, OrderType.MARKET.name(), OrderStatus.FILLED.name(),
                null, qty, qty, leverage);
        return orderRepository.save(closeOrder)
                .flatMap(saved -> fillRepository.save(new PaperFill(
                        null, saved.id(), SYMBOL, side, liqPrice, qty, BigDecimal.ZERO)))
                .doOnSuccess(f -> log.warn("강제 청산 userId={} {} {} @ {}", userId, side, qty, liqPrice))
                .then();
    }
}
