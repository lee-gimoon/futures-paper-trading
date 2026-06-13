package com.example.futurespapertrading.paper.repository;
import com.example.futurespapertrading.paper.domain.PaperFill;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

// paper_fills DB 접근 계층. (PaperOrderRepository와 같은 구조)
public interface PaperFillRepository extends ReactiveCrudRepository<PaperFill, Long> {

    // findByOrderId → SELECT * FROM paper_fills WHERE order_id = ?
    // 한 주문의 체결 내역(여러 건일 수 있음)을 모아볼 때 쓴다.
    Flux<PaperFill> findByOrderId(Long orderId);

    // 한 사용자의 모든 체결을 오래된 순(id 오름차순)으로 가져온다 (9단계 계좌/포지션 계산용).
    //   paper_fills엔 user_id가 없어 paper_orders와 JOIN해 주문의 user_id로 거른다(메서드 이름 파생이 안 돼 @Query).
    //   오름차순인 이유: PositionCalculator가 체결을 시간 순서대로 누적해 평균 진입가·실현 PnL을 내야 하기 때문.
    //   (체결 내역 화면은 최신순이 자연스러우니 프론트에서 뒤집어 보여준다.)
    @Query("SELECT f.* FROM paper_fills f JOIN paper_orders o ON f.order_id = o.id WHERE o.user_id = :userId ORDER BY f.id")
    Flux<PaperFill> findByUserIdOrderByIdAsc(Long userId);
}
