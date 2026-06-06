package com.example.futurespapertrading.paper;

import org.springframework.data.repository.reactive.ReactiveCrudRepository; // 리액티브 기본 CRUD
import reactor.core.publisher.Flux;                                         // 0개 이상을 비동기로 흘려보내는 상자

// paper_orders DB 접근 계층. UserRepository와 같은 방식 —
// 인터페이스만 선언하면 Spring Data가 부팅 시 구현체를 자동 생성해 빈으로 등록한다.
//   <PaperOrder, Long> = "이 리포지토리가 다루는 엔티티는 PaperOrder, PK 타입은 Long"
//   상속만으로 save/findById/findAll/deleteById 등 기본 메서드가 자동 제공된다.
public interface PaperOrderRepository extends ReactiveCrudRepository<PaperOrder, Long> {

    // 메서드 이름이 곧 쿼리(Query Derivation):
    //   findByUserId → SELECT * FROM paper_orders WHERE user_id = ?
    // "내 주문만 보기"(GET /api/paper/orders, D단계)에서 쓴다.
    // Flux<PaperOrder> = 일치하는 주문 0개 이상을 흘려보냄.
    Flux<PaperOrder> findByUserId(Long userId);

    // findByStatus → SELECT * FROM paper_orders WHERE status = ?
    // 대기 주문 재평가(G단계 matcher)가 status="OPEN"인 주문만 꺼내올 때 쓴다.
    // 파라미터가 String인 이유: 엔티티 status 필드가 String이라서. 호출부에서 OrderStatus.OPEN.name()을 넘긴다.
    Flux<PaperOrder> findByStatus(String status);
}
