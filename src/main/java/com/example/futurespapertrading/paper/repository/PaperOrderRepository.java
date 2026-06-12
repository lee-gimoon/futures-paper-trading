package com.example.futurespapertrading.paper.repository;
import com.example.futurespapertrading.paper.domain.PaperOrder;

import org.springframework.data.r2dbc.repository.Modifying;                 // "이 @Query는 SELECT가 아니라 갱신" 표시
import org.springframework.data.r2dbc.repository.Query;                     // 파생 못 하는 쿼리의 SQL 직접 지정
import org.springframework.data.repository.reactive.ReactiveCrudRepository; // 리액티브 기본 CRUD
import reactor.core.publisher.Flux;                                         // 0개 이상을 비동기로 흘려보내는 상자
import reactor.core.publisher.Mono;                                         // 0~1개를 비동기로 흘려보내는 상자

import java.math.BigDecimal;

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

    // countByStatus → SELECT count(*) FROM paper_orders WHERE status = ?
    // OpenOrderCounter가 부팅 시 "DB에 남아 있는 OPEN 주문 수"로 카운터를 채울 때 쓴다(재시작 보정, G-1).
    Mono<Long> countByStatus(String status);

    // ── 아래 updateIfOpen·cancelIfOpen 2개의 공통 설명: 조건부 갱신(G-3) ──
    //   둘 다 그 주문이 '아직 OPEN일 때만' 갱신하고, 갱신된 행 수(0 또는 1)를 돌려준다.
    //   matcher의 체결과 사용자의 취소가 같은 주문을 동시에 잡는 경합(race)은 같은 WHERE status='OPEN'이 정리한다.
    //   정리하는 로직은 우리 코드에 없다 — Postgres가 UPDATE 한 문장마다 항상 밟는 절차가 한다:
    //     행 찾기 → 잠겨 있으면 대기 → (풀리면) 최신 행으로 WHERE 재검사 → 맞으면 고침, 아니면 건너뜀 → "n건 고쳤다" 보고
    //   먼저 온 쪽은 행을 고치고 1을 받고, 늦은 쪽은 재검사에서 status≠OPEN이라 건너뜀 → 0 — 진 것이라 자기 쓰기를 포기한다.
    //
    //   @Query: 메서드 이름 파생으로는 UPDATE를 못 만들어서 쓴다 — 이름 파싱을 건너뛰고, 부팅 때 프록시에
    //     "이 메서드가 호출되면 이 SQL을 실행하라"고 그대로 등록된다. 메서드 이름은 사람 읽으라고 짓는
    //     이름이 되고, :이름 자리에는 같은 이름의 매개변수 값이 바인딩된다.
    //
    //   @Modifying: 실행 결과(Result)는 출구가 2개인 봉투인데, 어느 출구로 열지의 선언이다.
    //     Result (봉투)
    //      ├─ 출구 A: 행들을 꺼낸다           ← @Modifying 없을 때 (기본)
    //      └─ 출구 B: '몇 행 고쳤나'를 꺼낸다  ← @Modifying 있을 때
    //     봉투는 스스로 열리지 않아 여는 쪽이 골라야 하는데, SQL만 봐서는 못 고른다 — UPDATE … RETURNING처럼
    //     행과 행 수를 둘 다 돌려주는 문장도 있어서(psql에서 쳐보면 행 표와 "UPDATE 1"이 같이 찍힌다),
    //     첫 단어로 추측하면 조용히 틀린다. 그래서 Spring Data는 @Query 문자열을 해석하지 않고 통째로
    //     드라이버에 넘기며, 출구 선택만 작성자에게 받는다. 아래 두 쿼리는 RETURNING 없는 순수 UPDATE라
    //     내용물이 B에만 있다 → @Modifying으로 B를 선언.

    // 체결 반영(matcher 전용): status(완전 체결 FILLED·부분이면 OPEN 유지)와 filled_quantity를 함께 갱신.
    @Modifying
    @Query("UPDATE paper_orders SET status = :status, filled_quantity = :filledQuantity WHERE id = :id AND status = 'OPEN'")
    Mono<Long> updateIfOpen(Long id, String status, BigDecimal filledQuantity);

    // 취소(cancel 전용): status만 CANCELED로 — filled_quantity는 일부러 안 건드린다.
    //   cancel이 주문을 읽은 '뒤' matcher가 부분 체결(status는 OPEN 유지 + filled_quantity 증가)을 끼워 넣으면,
    //   읽어둔 옛 filled_quantity를 위 updateIfOpen으로 넘길 경우 그 부분 체결분이 옛값으로 되돌아가
    //   paper_fills 합계와 어긋난다 — 안 만지면 마지막으로 체결된 값이 그대로 남아 어긋날 수가 없다.
    @Modifying
    @Query("UPDATE paper_orders SET status = 'CANCELED' WHERE id = :id AND status = 'OPEN'")
    Mono<Long> cancelIfOpen(Long id);
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
