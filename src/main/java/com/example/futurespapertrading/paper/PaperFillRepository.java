package com.example.futurespapertrading.paper;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

// paper_fills DB 접근 계층. (PaperOrderRepository와 같은 구조)
public interface PaperFillRepository extends ReactiveCrudRepository<PaperFill, Long> {

    // findByOrderId → SELECT * FROM paper_fills WHERE order_id = ?
    // 한 주문의 체결 내역(여러 건일 수 있음)을 모아볼 때 쓴다.
    Flux<PaperFill> findByOrderId(Long orderId);
}
