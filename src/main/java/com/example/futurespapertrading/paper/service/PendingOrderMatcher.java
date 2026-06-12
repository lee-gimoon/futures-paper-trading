package com.example.futurespapertrading.paper.service;
import com.example.futurespapertrading.market.stream.LatestOrderBookSnapshotStore;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 서버가 Binance에서 새 호가 snapshot을 받을 때마다 아직 OPEN 상태인 지정가 주문들을 자동으로 다시 검사해서,
// 가격이 닿았으면 체결 처리하도록 연결하는 클래스다. 이 작업은 사용자의 HTTP 요청 없이 앱 내부에서 계속 돌아간다.
@Component
public class PendingOrderMatcher {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderMatcher.class);

    private final LatestOrderBookSnapshotStore latestStore;
    private final OpenOrderCounter openOrderCounter;
    private final PaperOrderService orderService;

    public PendingOrderMatcher(LatestOrderBookSnapshotStore latestStore,
                               OpenOrderCounter openOrderCounter,
                               PaperOrderService orderService) {
        this.latestStore = latestStore;
        this.openOrderCounter = openOrderCounter;
        this.orderService = orderService;
    }

    @PostConstruct
    public void start() {
        // latestStore.stream()은 새 호가 snapshot이 들어올 때마다 그 snapshot을 흘려주는 내부 Flux다.
        latestStore.stream()
                // filter는 람다가 false를 반환하면 snapshot을 버리고, true를 반환하면 다음 단계로 통과시킨다.
                .filter(snapshot -> !openOrderCounter.isZero())
                // matchOpenOrders는 비동기 DB 작업(Mono)을 반환한다.
                // concatMap은 이전 snapshot의 DB 작업이 완료된 뒤 다음 snapshot의 DB 작업을 시작한다.
                .concatMap(orderService::matchOpenOrders)
                // 재평가 중 에러가 onError로 번지면 이 구독(부팅 시 1회뿐)이 죽어 matcher가 영구 정지한다
                //   → 에러난 snapshot만 로그 남기고 버린 뒤 스트림은 계속.
                .onErrorContinue((e, item) -> log.warn("OPEN 주문 재평가 실패 — 다음 snapshot에서 재시도", e))
                .subscribe();
    }
}
