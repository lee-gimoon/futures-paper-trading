package com.example.futurespapertrading.paper.service;
import com.example.futurespapertrading.market.stream.LatestOrderBookSnapshotStore;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

// 자동 강제청산 검사를 "언제 실행할지" 관리하는 컴포넌트다.
// 실시간 호가 스트림을 1초마다 확인해 LiquidationService.runOnce()를 호출한다.
// 청산 대상 판정과 주문·체결 저장은 LiquidationService가 담당한다.
@Component
public class LiquidationMonitor {

    private static final Logger log = LoggerFactory.getLogger(LiquidationMonitor.class);

    // Duration.ofSeconds(1)은 "1초"를 나타내는 시간값을 만든다.
    private static final Duration CHECK_INTERVAL = Duration.ofSeconds(1);


    private final LatestOrderBookSnapshotStore latestStore; // 최신 호가 snapshot 스트림을 제공하며, monitoringFlow()에서 stream()을 구독한다.
    private final LiquidationService liquidationService; // 한 회차의 청산 판정과 실행을 위임할 서비스다.
    // Spring 종료 시 해제할 백그라운드 감시 구독이다.
    private Disposable subscription;

    public LiquidationMonitor(LatestOrderBookSnapshotStore latestStore,
                              LiquidationService liquidationService) {
        this.latestStore = latestStore;
        this.liquidationService = liquidationService;
    }

    // 서버 시작 시 Spring이 이 객체를 생성한 뒤 start()를 한 번 호출해 청산 감시를 시작한다.
    // monitoringFlow()가 만든 감시 흐름을 구독하면 내부에서 실시간 호가를 계속 받고, 서버 종료 시 stop()에서 구독을 해제한다.
    @PostConstruct
    public void start() {
        // Schedulers.parallel()은 Reactor가 공용으로 관리하는 병렬 Scheduler를 반환한다.
        // monitoringFlow()는 전달받은 Scheduler를 사용해 CHECK_INTERVAL마다 청산 검사 신호를 만든다.
        subscription = monitoringFlow(CHECK_INTERVAL, Schedulers.parallel())
                // monitoringFlow()는 Flux<Void>라 onNext 값을 내보내지 않으므로 첫 번째 람다는 호출되지 않는다.
                // 두 번째 람다는 전체 감시 스트림이 오류로 종료될 때 Throwable e를 받아 ERROR 로그로 기록한다.
                .subscribe(ignored -> { }, e -> log.error("자동 청산 감시가 종료되었습니다", e));
    }

    // 호가 스트림을 반복적인 청산 검사 호출로 변환한다. 호가 값 자체는 Service에 전달하지 않고 실행 신호로만 사용한다.
    // Scheduler를 인자로 받아 테스트에서는 실제 대기 없이 가상 시간으로 동작을 검증할 수 있다.
    Flux<Void> monitoringFlow(Duration checkInterval, Scheduler scheduler) {
        return latestStore.stream()
                // interval의 타이머 신호는 WebSocket 스레드가 아니라 전달받은 Scheduler의 공용 작업 스레드에서 발생한다.
                // 전체 파이프라인이 구독되면 sample은 원본 호가 스트림과 interval 타이머 스트림을 함께 구독한다.
                // sample은 원본 호가를 내부에 최신 1건만 보관하고, interval 신호가 올 때 그 값을 전달한다.
                .sample(Flux.interval(checkInterval, scheduler))
                // concatMap이 청산 검사를 처리하는 동안 새 신호가 계속 들어오면,
                // 오래된 대기 신호를 버리고 다음 검사에 사용할 최신 신호 하나만 보관한다.
                .onBackpressureLatest()
                // ignored는 sample이 전달한 OrderBookSnapshot이지만, 값 자체는 쓰지 않고 청산 검사 시작 신호로만 사용한다.
                // 한 번 시작한 청산 검사가 끝날 때까지 다음 청산 검사를 기다리게 해, 여러 검사가 동시에 실행되지 않게 한다.
                .concatMap(ignored -> liquidationService.runOnce()
                        // runOnce()에서 발생한 오류를 WARN 로그로 기록한다. doOnError는 오류 신호를 그대로 전달한다.
                        .doOnError(e -> log.warn("청산 검사 실패 — 다음 틱에서 재시도", e))
                        // 오류를 빈 완료 신호로 바꿔 감시 스트림이 종료되지 않고 다음 호가 신호를 처리하게 한다.
                        .onErrorResume(e -> Mono.empty()));
    }

    // 애플리케이션 종료 시 타이머와 호가 스트림 구독을 해제한다.
    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }
}
