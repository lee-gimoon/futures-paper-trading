package com.example.futurespapertrading.paper.service;

import com.example.futurespapertrading.market.domain.OrderBookSnapshot;
import com.example.futurespapertrading.market.stream.LatestOrderBookSnapshotStore;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiquidationMonitorTest {

    @Test
    void keepsLatestTriggerAndContinuesAfterSlowLiquidationCheck() {
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        LatestOrderBookSnapshotStore latestStore = mock(LatestOrderBookSnapshotStore.class);
        LiquidationService liquidationService = mock(LiquidationService.class);
        OrderBookSnapshot snapshot = mock(OrderBookSnapshot.class);
        AtomicInteger runCount = new AtomicInteger();

        // 100ms마다 호가가 오지만 청산 검사 한 번은 3초가 걸리는 과부하 상황을 만든다.
        when(latestStore.stream()).thenReturn(
                Flux.interval(Duration.ofMillis(100), scheduler).map(ignored -> snapshot));
        when(liquidationService.runOnce()).thenAnswer(ignored -> {
            runCount.incrementAndGet();
            return Mono.delay(Duration.ofSeconds(3), scheduler).then();
        });

        LiquidationMonitor monitor = new LiquidationMonitor(latestStore, liquidationService);

        // 이전 구현은 두 번째 sample 시점에 OverflowException으로 종료됐다.
        // 8초 동안 에러 없이 유지되고, 느린 검사가 끝난 뒤 최신 신호로 다음 검사가 시작되는지 확인한다.
        StepVerifier.withVirtualTime(
                        () -> monitor.monitoringFlow(Duration.ofSeconds(1), scheduler),
                        () -> scheduler,
                        Long.MAX_VALUE)
                .thenAwait(Duration.ofSeconds(8))
                .thenCancel()
                .verify();

        assertTrue(runCount.get() >= 3, "느린 검사 뒤에도 최신 신호로 청산 검사가 계속되어야 한다");
    }
}
