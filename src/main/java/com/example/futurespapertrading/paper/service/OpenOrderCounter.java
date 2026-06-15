package com.example.futurespapertrading.paper.service;
import com.example.futurespapertrading.paper.domain.OrderStatus;
import com.example.futurespapertrading.paper.repository.PaperOrderRepository;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

// "지금 OPEN 주문이 몇 건인가"를 메모리에 들고 있는 카운터 (G-1).
//
//   배경 — matcher는 왜 100ms마다 DB를 두드리나:
//     지정가 주문이 OPEN이라는 건 "지금 가격엔 안 닿아서, 닿을 때까지 기다리는 중"이라는 뜻이다.
//     그런데 "닿았다"는 사건은 어디에도 저절로 존재하지 않는다 — 가격(snapshot)과 주문(limit)을
//     누군가 비교해야만 생기는 파생 사건이다. Binance는 가격이 바뀌었다고만 push할 뿐 우리 주문의
//     존재를 모르고, DB에 저장된 행도 스스로 변하지 않는다. 그래서 그 비교를 하고 FILLED로 바꿔줄
//     주체가 필요하다 — 그게 matcher. 비교할 새 가격이 생기는 순간이 곧 snapshot 도착(100ms마다,
//     아까 안 닿던 주문이 지금은 닿을지도 모르는 순간)이므로, matcher는 매 snapshot마다 OPEN
//     주문들을 DB에서 꺼내(findByStatus) "이제 닿나?"를 다시 검사한다.
//     (닿는 순간이 어느 snapshot에 실릴지 몰라 건너뛰면 기회를 놓친다)
//
//   존재 이유 — fast-path skip:
//     위 검사는 OPEN이 0건이어도 일단 DB를 조회해야 "0건이라 할 일 없음"을 알 수 있다. OPEN이 0건인
//     시간이 대부분인데 결과가 뻔한 SELECT를 초당 10번씩 던지는 건 낭비 — OPEN 건수를 메모리 정수로
//     들고 있으면 "볼 것도 없음"을 공짜(정수 비교)로 판정하고 DB는 건드리지 않는다.
//   ±1 시점: placeOrder가 OPEN 저장 +1 / 취소 성공 −1 / matcher가 완전 체결(FILLED) −1.
//     부분 체결로 OPEN이 유지되면 그대로 — 아직 호가창에 걸려 있으니까.
//   동시성: Writer가 둘 이상(HTTP 요청 스레드들 + matcher)이라 일반 int의 ++ 대신 AtomicInteger의 원자 연산.
@Component
public class OpenOrderCounter {

    private final AtomicInteger count = new AtomicInteger();

    private final PaperOrderRepository orderRepository;

    public OpenOrderCounter(PaperOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // 재시작 보정: 서버가 죽었다 떠도 DB의 OPEN 주문은 남아 있다 — 0으로 시작하면 matcher가 영원히 skip하므로
    //   앱 준비가 끝난 뒤 DB에서 한 번 세어 채운다.
    //
    // 왜 @PostConstruct가 아니라 ApplicationReadyEvent인가:
    //   @PostConstruct는 이 Bean 하나가 만들어진 직후 실행된다. 그 시점에는 schema.sql이 아직 paper_orders 테이블을
    //   만들기 전일 수 있어, 첫 조회가 "relation paper_orders does not exist"로 실패할 수 있다.
    //   ApplicationReadyEvent는 Spring Boot 앱 전체 준비가 끝난 뒤 발행되므로 DB 초기화 이후에 조회할 수 있다.
    //
    // 왜 set이 아니라 addAndGet인가:
    //   이 조회는 비동기라 결과가 도착하기 전에 새 주문이 +1을 했을 수 있다.
    //   set은 그 +1을 덮어쓰지만, addAndGet은 기존 증가분을 보존한 채 DB 조회값을 더한다.
    //   set(n)은 현재 count를 n으로 교체하고, addAndGet(n)은 현재 count에 n을 원자적으로 더한다.
    //   원자적 = 여러 스레드가 동시에 건드려도 "읽고-더하고-저장"이 중간에 끊기지 않는 한 번의 안전한 연산.
    @EventListener(ApplicationReadyEvent.class)
    void seedFromDb() {
        // countByStatus(...)는 Mono<Long>("아직 안 온 건수 1개"의 약속) — 이 줄은 파이프라인을 만들 뿐 실행은 안 된다(cold).
        //   OrderStatus.OPEN.name() = enum을 DB에 저장된 문자열 "OPEN"으로 바꿔 넘김 (status 컬럼이 문자열이라).
        orderRepository.countByStatus(OrderStatus.OPEN.name())
                // subscribe(콜백) = 실행 방아쇠. 람다 '객체'는 인자라서 subscribe가 호출되기도 전에 이미 조립돼
                //   건네진다(자바 일반 규칙: 메서드를 호출하려면 인자값이 먼저 평가되어야 한다) — 바디는 이때 미실행.
                //   subscribe가 걸리면 흐름은 단순하다:
                //     DB count 쿼리 실행
                //     결과 Long n 도착
                //     람다 실행: n -> count.addAndGet(n.intValue())
                //       n.intValue() = Long 객체 n을 int 기본값으로 변환.
                //       count.addAndGet(...) = AtomicInteger에 값을 원자적으로 더함. 반환값은 여기서 쓰지 않는다.
                .subscribe(n -> count.addAndGet(n.intValue()));
    }

    public void increment() { count.incrementAndGet(); }

    public void decrement() { count.decrementAndGet(); }

    public boolean isZero() { return count.get() == 0; }
}
