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

    // 모든 계좌의 현재 포지션을 한 번씩 평가하고, 청산 조건이면 강제 청산한다.
    // runOnce는 1회 스캔만 수행하며, 반복 호출은 LiquidationMonitor가 담당한다.
    // MVP라 매 호출마다 전 계좌를 본다(계좌 수에 비례). 사용자가 많아지면 '포지션 보유 계좌' 인덱스로 최적화.
    public Mono<Void> runOnce() {
        return accountRepository.findAll()
                .concatMap(account -> portfolioService.accountState(account.userId())
                        .flatMap(state -> {
                            // 계좌 레버리지는 신규 주문용이라 청산 판단에 쓰지 않는다.
                            // 이미 열린 포지션의 청산가는 positionLeverage 기준으로 계산한다.
                            boolean shouldLiquidate = MarginCalculator.isLiquidated(state.position(), state.positionLeverage(), state.mark());
                            if (!shouldLiquidate) {
                                return Mono.empty(); // 값 없이 완료 신호만 보내, 강제 청산 없이 이번 계좌 처리를 끝내고 다음 계좌로 넘어간다.
                            }
                            // 청산 대상이면 반대방향 주문과 체결을 저장해 현재 포지션을 닫는다.
                            return liquidate(account.userId(), state.position(), state.positionLeverage());
                        }))
                .then();
    }

    // 청산 대상이라고 판정된 포지션에 대해 "실제 청산을 실행"하는 메서드다.
    // 현재 포지션과 반대 방향의 청산 주문(FILLED)을 만들고, 청산가 체결을 저장해 포지션을 닫는다.
    // Mono<Void>는 반환할 값은 없지만, DB 저장 작업의 완료/실패 신호를 호출자에게 돌려준다.
    // save()가 만든 Mono를 반환해 리액티브 체인에 연결하지 않으면 구독되지 않아 저장이 실행되지 않고,
    // concatMap도 청산 작업의 완료/실패를 기다릴 수 없다.
    private Mono<Void> liquidate(Long userId, Position pos, int leverage) {
        int dir = pos.signedQuantity().signum(); // 포지션 수량의 부호로 현재 포지션 방향을 판단하기 위해 사용한다.
        String side = dir > 0 ? OrderSide.SELL.name() : OrderSide.BUY.name(); // 포지션을 닫으려면 현재 포지션과 반대 방향 주문을 내야 하므로 주문 방향을 정한다.
        BigDecimal qty = pos.signedQuantity().abs(); // 주문 수량은 음수가 될 수 없으므로 포지션 크기만 양수로 꺼낸다.
        BigDecimal liqPrice = MarginCalculator.liquidationPrice(pos, leverage); // 청산 체결을 기록할 가격이 필요하므로 포지션과 레버리지로 청산가를 계산한다.

        // 일반 진입 주문은 사용자가 버튼을 눌러 주문 요청을 보내지만,
        // 강제 청산은 사용자 요청 없이 시스템이 자동으로 발생시키는 반대매매다.
        // 이 프로젝트는 포지션을 직접 수정하지 않고 체결 이력으로 다시 계산하므로,
        // 청산 주문(FILLED)과 청산 체결(PaperFill)을 함께 저장해 포지션이 flat으로 계산되게 한다.
        PaperOrder closeOrder = new PaperOrder(
                null, userId, SYMBOL,
                side, OrderType.MARKET.name(), OrderStatus.FILLED.name(),
                null, qty, qty, leverage);
        // 주문을 먼저 저장해야 DB가 만든 order id를 받을 수 있다. 
        return orderRepository.save(closeOrder)
                // PaperFill 테이블 행을 가지고 현재 포지션 상태를 계산하므로,
                // 청산 때도 반대 방향 PaperFill을 저장해 포지션 계산에 반영한다.
                // 저장된 주문 id로 체결 1건을 저장해야 주문과 체결이 연결된다.
                .flatMap(saved -> fillRepository.save(new PaperFill(
                        null, saved.id(), SYMBOL, side, liqPrice, qty, BigDecimal.ZERO)))
                // 청산 주문과 체결 저장이 성공하면 운영 로그를 남긴다.
                .doOnSuccess(f -> log.warn("강제 청산 userId={} {} {} @ {}", userId, side, qty, liqPrice))
                // 호출자는 저장된 객체가 아니라, 청산 저장 작업이 끝났다는 완료 신호만 받는다.
                .then();
    }
}
