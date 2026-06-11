package com.example.futurespapertrading.paper;

import org.springframework.data.repository.reactive.ReactiveCrudRepository; // 리액티브 기본 CRUD
import reactor.core.publisher.Flux;                                         // 0개 이상을 비동기로 흘려보내는 상자

// paper_orders DB 접근 계층. UserRepository와 같은 방식 —
// 인터페이스만 선언하면 Spring Data가 부팅 시 구현체를 자동 생성해 빈으로 등록한다.
//   <PaperOrder, Long> = "이 리포지토리가 다루는 엔티티는 PaperOrder, PK 타입은 Long"
//   상속만으로 save/findById/findAll/deleteById 등 기본 메서드가 자동 제공된다.
//
//   단, 모든 메서드가 PaperOrder를 반환하는 건 아니다 — findById/findAll은 제네릭 T가
//   PaperOrder로 채워지지만, count/existsById/deleteById는 Long/Boolean/Void를 반환한다.
//   → <PaperOrder, Long>이 정하는 건 반환 타입이 아니라 쿼리의 대상(paper_orders 테이블)이다.
public interface PaperOrderRepository extends ReactiveCrudRepository<PaperOrder, Long> {

    // 메서드 이름이 곧 쿼리(Query Derivation):
    //   findByUserIdOrderByIdDesc → SELECT * FROM paper_orders WHERE user_id = ? ORDER BY id DESC
    //   · OrderByIdDesc = id 내림차순. id는 BIGSERIAL이라 클수록 최신 → 최신 주문이 위로 온다.
    // "내 주문만 보기"(GET /api/paper/orders, D단계)에서 쓴다.
    // Flux<PaperOrder> = 일치하는 주문 0개 이상을 최신순으로 흘려보냄.
    Flux<PaperOrder> findByUserIdOrderByIdDesc(Long userId);

    // findByStatus → SELECT * FROM paper_orders WHERE status = ?
    // 대기 주문 재평가(G단계 matcher)가 status="OPEN"인 주문만 꺼내올 때 쓴다.
    // 파라미터가 String인 이유: 엔티티 status 필드가 String이라서. 호출부에서 OrderStatus.OPEN.name()을 넘긴다.
    Flux<PaperOrder> findByStatus(String status);
}

// ════════════════════════════════════════════════════════════════════════════
// 메서드 이름 한 줄이 어떻게 SQL이 되나 — 쿼리 조각별 출처
//
//   쿼리 조각              | 어디서 왔나
//   ─────────────────────|──────────────────────────────────────────────────
//   FROM paper_orders     | 제네릭 <PaperOrder> → PaperOrder의 @Table("paper_orders")
//   WHERE user_id         | 메서드 이름 findByUserIdOrderByIdDesc 파싱 → userId 속성 → @Column("user_id")
//   ORDER BY id DESC      | 같은 메서드 이름의 OrderByIdDesc 파싱 → id 속성 내림차순
//   SELECT * + 객체 복원   | 제네릭 <PaperOrder>의 필드들 (행 → new PaperOrder(...) 매핑)
//   이 전부를 실행         | 자바(javac)가 아니라 Spring Data — 부팅 때 구현체(프록시)를
//                         |   자동 생성하고, 어노테이션·메서드 이름을 리플렉션으로 읽어 쿼리 생성
//
//   즉: 제네릭 타입이 "어느 테이블/어떤 객체"를 정하고(FROM·SELECT),
//       메서드 이름이 "무엇으로 거를지·정렬할지"를 정한다(WHERE·ORDER BY). 본문은 한 줄도 안 짜도 된다.
//
// ── "객체 복원"이란? (DB는 자바 객체를 모르고 행=값들만 안다) ─────────────────────
//   저장(save) : PaperOrder 객체  ──분해──▶  행(값들)        → DB에 저장
//   조회(find) : 행(값들)         ──조립──▶  PaperOrder 객체  ← 원래 객체 형태로 "복원"
//   저장할 때 객체를 칸칸이 분해해 넣었다가, 읽을 때 new PaperOrder(...)로 다시 조립한다.
//   (가구를 분해해 창고에 넣었다가 꺼낼 때 다시 조립하는 것과 같다.)
//
// ── "자바가 아니라 Spring Data"란? (누가/언제 구현체를 만드나) ─────────────────────
//   자바 컴파일러(javac) | 인터페이스를 보고 "본문 없네, 인터페이스니까 OK" 하고 그냥 넘어감 | 컴파일할 때
//   Spring Data(라이브러리) | 그 인터페이스의 실제 구현체(프록시)를 만들어 빈으로 등록       | 서버 부팅할 때(실행 중)
//   → 우리는 본문 없는 인터페이스(약속)만 적었고, 실제 동작 코드는 Spring Data가
//     부팅 때 대신 채워 넣는다. 자바 언어/컴파일러의 기능이 아니라 라이브러리의 기능.
// ════════════════════════════════════════════════════════════════════════════
