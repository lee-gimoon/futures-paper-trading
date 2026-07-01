package com.example.futurespapertrading.paper.service;
import com.example.futurespapertrading.paper.domain.OrderStatus;
import com.example.futurespapertrading.paper.repository.PaperOrderRepository;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

// 현재 OPEN 지정가 주문 수를 메모리에 들고 있는 fast-path 카운터.
// PendingOrderMatcher는 새 호가 snapshot마다 OPEN 주문이 체결 가능한지 검사해야 한다.
// 그런데 OPEN 주문이 0건이면 DB에서 주문을 조회해도 결과가 뻔하므로, 이 카운터가 0이면 matcher가 DB 조회를 건너뛴다.
//
// 값이 바뀌는 시점:
//   OPEN 주문 생성 +1 / 취소 성공 -1 / matcher가 완전 체결(FILLED) -1.
//   부분 체결은 아직 OPEN 주문이 남아 있으므로 count를 바꾸지 않는다.
//
// 일반 int의 count++는 "메모리에서 현재 값 읽기 -> +1 계산 -> 메모리에 다시 쓰기" 세 단계라, 동시에 실행되면 증가분이 사라질 수 있다.
// AtomicInteger는 내부 숫자를 바꾸는 incrementAndGet()/decrementAndGet() 메서드를 제공한다.
// 원자적(atomic)의 원래 뜻은 원자처럼 더 쪼갤 수 없는 한 덩어리라는 뜻이다.
// 프로그래밍에서는 "읽고-계산하고-메모리에 다시 쓰기"가 하나의 연산처럼 끝나, 다른 스레드가 중간에 끼어들 수 없다는 뜻이다.
@Component
public class OpenOrderCounter {

    // AtomicInteger는 int 값 하나를 안에 담는 객체다.
    // List가 여러 원소를 add/get으로 다루듯, AtomicInteger는 내부 숫자 하나를 get(), incrementAndGet(), addAndGet(...)으로 다룬다.
    // 값 변경 메서드들은 원자적이라 여러 스레드가 동시에 호출해도 증가/감소가 꼬이지 않는다.
    private final AtomicInteger count = new AtomicInteger();

    private final PaperOrderRepository orderRepository;

    public OpenOrderCounter(PaperOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // 서버 재시작 후 DB에 남아 있는 OPEN 주문 수를 읽어 메모리 카운터를 보정한다.
    // 메모리 count는 0으로 다시 시작하지만 DB의 OPEN 주문은 남아 있으므로,
    // 보정하지 않으면 PendingOrderMatcher가 "OPEN 주문 없음"으로 오판해 재평가를 skip할 수 있다.
    //
    // ApplicationReadyEvent.class는 "앱 준비 완료" 사건을 표현하는 이벤트 클래스 타입이다.
    // @EventListener(...)는 그 이벤트가 발생했을 때 이 메서드를 자동 호출하게 한다.
    // 이 시점은 Bean 생성과 DB 초기화가 끝나 요청을 받을 준비가 된 뒤라,
    // @PostConstruct처럼 paper_orders 테이블 생성보다 먼저 실행될 위험을 피할 수 있다.
    //
    // addAndGet은 현재 AtomicInteger 값에 countByStatus("OPEN")가 DB에서 세어 온 OPEN 주문 개수를 더한다.
    // 예를 들어 이 비동기 조회가 끝나기 전에 새 지정가 주문이 OPEN으로 저장되면 saveOrder()가 increment()로 count를 +1 한다.
    // addAndGet은 그 +1 위에 DB 조회 결과를 더하므로, 새 주문 증가분을 덮어쓰지 않는다.
    @EventListener(ApplicationReadyEvent.class)
    void seedOpenOrderCountFromDb() {
        orderRepository.countByStatus(OrderStatus.OPEN.name())
                // R2DBC 쿼리는 subscribe해야 실행된다.  
                .subscribe(n -> count.addAndGet(n.intValue()));
    }

    public void increment() { count.incrementAndGet(); }

    public void decrement() { count.decrementAndGet(); }

    public boolean isZero() { return count.get() == 0; }
}
