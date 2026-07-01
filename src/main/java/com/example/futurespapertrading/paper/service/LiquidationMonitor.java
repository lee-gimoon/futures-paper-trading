package com.example.futurespapertrading.paper.service;
import com.example.futurespapertrading.market.stream.LatestOrderBookSnapshotStore;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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

    // 도메인 선택: 100ms마다 모든 snapshot을 검사하면 더 정확하지만 부하가 커진다.
    // 이 MVP는 부하를 낮추려고 1초마다 최신 snapshot만 검사하며, 아주 짧은 청산가 터치는 놓칠 수 있다.
    @PostConstruct
    public void start() {
        // 타입 흐름: stream()/sample()까지는 Flux<OrderBookSnapshot>, concatMap은 각 snapshot 트리거를 runOnce()의 Mono<Void>로 바꾼다.
        // Mono<Void>는 값 없이 완료/에러 신호만 내며, runOnce() 실패는 로그 후 Mono.empty()로 바꿔 다음 틱을 계속 받는다.
        latestStore.stream()
                .sample(Duration.ofSeconds(1)) // 1초에 한 번만 청산 검사
                .concatMap(ignored -> liquidationService.runOnce() // snapshot 값은 쓰지 않고, 도착 신호만 청산 검사 트리거로 쓴다
                        .doOnError(e -> log.warn("청산 검사 실패 — 다음 틱에서 재시도", e)) // do-on-error = 에러가 났을 때 로그 남기기(에러 자체는 아직 흐름에 남아 있음)
                        .onErrorResume(e -> Mono.empty()))  // on-error-resume = 에러가 났을 때 빈 완료 흐름으로 재개해 다음 틱을 받게 한다
                .subscribe();
    }

    // 소스 코드의 ignored -> liquidationService.runOnce()... 자체는 람다식 표현식이고,
    // 런타임에서는 함수형 인터페이스 객체/인스턴스로 취급된다.
    // 함수형 객체는 호출할 핵심 메서드가 하나뿐이라, 나중에 실행할 함수 하나를 객체처럼 넘겨둘 수 있다.
    // 이 객체는 start() 메서드가 실행되면서 .concatMap(...) 호출 인자를 평가할 때 준비된다.
    // 즉 서버 시작 시 Spring이 LiquidationMonitor 객체 생성 -> 의존성 주입 -> @PostConstruct 때문에 start() 호출,
    // 이 시점에 Reactor 파이프라인이 조립되고 람다식도 concatMap에 넘길 Function 객체로 준비된다.
    // 실제 liquidationService.runOnce()는 나중에 sample을 통과한 snapshot이 들어와 concatMap이 그 Function을 호출할 때 실행된다.
}
