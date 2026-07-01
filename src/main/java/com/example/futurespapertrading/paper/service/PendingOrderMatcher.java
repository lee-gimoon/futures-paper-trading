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

    // @PostConstruct = 생성자(constructor) 실행 후(post)에 Spring이 1번 자동 호출하는 초기화 메서드 표시.
    // 객체 생성 -> 의존성 주입 완료(latestStore/openOrderCounter/orderService) -> start() 자동 실행 -> 앱 계속 동작.
    @PostConstruct
    public void start() {
        // latestStore.stream()은 새 호가 snapshot이 들어올 때마다 그 snapshot을 흘려주는 내부 Flux다.
        latestStore.stream()
                // filter는 람다가 false를 반환하면 snapshot을 버리고, true를 반환하면 다음 단계로 통과시킨다.
                // 여기서는 OPEN 주문이 0건이면 현재 snapshot 하나만 버리고, 스트림 자체는 계속 살아 있다.
                // OPEN 주문이 없는 경우가 흔하므로, 이 흔한 경우를 메모리 카운터 조회만으로 먼저 걸러낸다.
                // 이런 식으로 비싼 경로(DB 조회 + matcher 로직)에 들어가기 전에 빠르게 빠져나가는 길을 fast-path라고 부른다.
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
