package com.example.futurespapertrading.paper.service;
import com.example.futurespapertrading.market.stream.LatestOrderBookSnapshotStore;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 자동 강제청산 감시(9단계 레버리지). PendingOrderMatcher와 같은 패턴 — snapshot 스트림을 구독해 청산 대상을 검사한다.
//   다른 점: 청산은 100ms마다 볼 필요가 없어 sample(1초)로 throttle한다(부하↓). concatMap으로 직렬화해 틱이 겹치지 않게 한다.
@Component
public class LiquidationMonitor {

    private static final Logger log = LoggerFactory.getLogger(LiquidationMonitor.class);

    private final LatestOrderBookSnapshotStore latestStore;
    private final LiquidationService liquidationService;

    public LiquidationMonitor(LatestOrderBookSnapshotStore latestStore,
                              LiquidationService liquidationService) {
        this.latestStore = latestStore;
        this.liquidationService = liquidationService;
    }

    @PostConstruct
    public void start() {
        latestStore.stream()
                .sample(Duration.ofSeconds(1))                       // 1초에 한 번만 청산 검사
                .concatMap(snapshot -> liquidationService.runOnce())  // 이전 검사가 끝나야 다음 시작 (틱 겹침 방지)
                .onErrorContinue((e, item) -> log.warn("청산 검사 실패 — 다음 틱에서 재시도", e))
                .subscribe();
    }
}
