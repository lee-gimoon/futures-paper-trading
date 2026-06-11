package com.example.futurespapertrading.paper.domain;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;                  // PK 필드 표시
import org.springframework.data.relational.core.mapping.Column; // 필드 ↔ 컬럼명 매핑 (이름이 다를 때)
import org.springframework.data.relational.core.mapping.Table;  // 클래스 ↔ 테이블 매핑

// paper_orders 테이블 한 줄 ↔ 자바 객체 1:1 매핑. (User 엔티티와 완전히 같은 구조: record + @Table + @Id + @Column)
// "사용자가 낸 주문 한 건"을 DB에 저장/조회할 때 쓰는 영속성 전용 그릇이다. (외부 응답엔 DTO를 따로 쓴다)
//
// ── 타입 선택 근거 ──
//   - price/quantity 계열은 double이 아닌 BigDecimal (DB는 NUMERIC). 1원 오차도 손익이 되므로 정밀도 필수.
//   - side/type/status는 enum이 아니라 String. (이유는 OrderSide 주석 참고 — A단계는 enum 변환기를 안 둔다)
//   - created_at/updated_at은 User처럼 record에 넣지 않는다 → DB가 DEFAULT now()로 채운다.
//     (참고: updated_at은 INSERT 때만 now()로 채워지고, 나중에 status가 바뀌는 UPDATE 때는
//      이 필드를 엔티티에 안 들고 있어 자동으로 갱신되지 않는다. 필요해지면 트리거/명시 갱신으로 보강.)
@Table("paper_orders")
public record PaperOrder(
        @Id Long id,                                          // null로 저장 → INSERT(새 행), 값 있음 → 그 행 UPDATE
        @Column("user_id") Long userId,                       // 주문을 낸 사용자(users.id). "내 주문만" 격리의 기준
        @Column("account_id") Long accountId,                 // 9단계 계좌 도입 전까지는 null
        String symbol,                                        // 우선 "BTCUSDT" 하나
        String side,                                          // "BUY" / "SELL"        → OrderSide
        String type,                                          // "MARKET" / "LIMIT"    → OrderType
        String status,                                        // OrderStatus
        @Column("limit_price") BigDecimal limitPrice,         // 지정가에만 값, 시장가면 null
        BigDecimal quantity,                                  // 주문 수량
        @Column("filled_quantity") BigDecimal filledQuantity  // 체결된 누적 수량 (처음엔 0)
) {
}

// ════════════════════════════════════════════════════════════════════════════
// 엔티티(Entity) vs DTO — "어느 쪽과 대화하는 그릇인가"로 갈린다
//
// 헷갈림 포인트: paper_orders(=이 PaperOrder)는 "내가 입력하는 주문서"가 아니다.
//   내가 입력하는 주문서는 따로 있다 → dto/CreateOrderRequest (필드가 symbol·side·type·
//   quantity·limitPrice 5개뿐, status·id·userId 없음 — 그건 손님이 적을 칸이 아니라서다).
//   PaperOrder는 서버가 처리한 결과까지 적어 DB에 남기는 '기록부(원장)'다.
//
// 한 번의 주문이 흐르며 세 그릇을 거친다:
//
//   ①  내가 보냄      CreateOrderRequest (입력 DTO)  ─ symbol·side·type·quantity·limitPrice 뿐
//          │
//          ▼  서버가 id·userId·status·filledQuantity 를 채워 넣어
//   ②  서버가 저장    PaperOrder         (엔티티)    ── INSERT ──▶  paper_orders 테이블 한 줄
//          │
//          ▼  저장 결과에서 바깥에 보일 것만 골라
//   ③  서버가 돌려줌  OrderResponse      (출력 DTO)  ─ avgPrice 로 요약, fill 개별내역은 뺌
//
// ── 두 부류의 본질 ──
//   · 엔티티(PaperOrder)  = "DB와 대화하는 그릇". @Table 로 테이블 한 줄과 1:1 매핑된다.
//        → 서버가 저장/조회하는 영속성 전용. 바깥(클라이언트)엔 이걸 직접 안 내보낸다.
//   · DTO(CreateOrderRequest / OrderResponse) = "바깥(클라이언트)과 대화하는 그릇".
//        → HTTP 경계를 넘나드는 운반 전용. DB와는 무관하다(@Table 없음).
//
// ── "모든 엔티티는 서버가 저장하는 것인가?" → 그렇다 ──
//   엔티티 = 테이블 한 줄의 자바 표현이라, 본질이 '서버의 영속 데이터'다.
//   (이 프로젝트의 엔티티: User=users, PaperOrder=paper_orders, PaperFill=paper_fills)
//
// ── 단, 그 역(逆)은 성립하지 않는다: "DTO = 내가 보내는 것"이 아니다 ──
//   DTO는 방향이 두 가지다:
//     · 입력 DTO (클라이언트 → 서버) : CreateOrderRequest  ← 내가 '보내는' 것
//     · 출력 DTO (서버 → 클라이언트) : OrderResponse       ← 서버가 내게 '돌려주는' 것
//   OrderResponse 도 DTO지만 내가 보내는 게 아니라 받는 것이다.
//   정확한 구분선은 "입력/출력"이 아니라 "DB냐(엔티티) / 바깥이냐(DTO)"이다.
//
// ── 왜 굳이 셋으로 나누나 (엔티티 하나로 다 쓰면 안 되나) ──
//   1) 입력엔 status·id·userId 가 없다 — 그건 서버가 정할 값이지 손님이 적을 칸이 아니다.
//   2) 출력엔 보일 것만 골라 담는다 — fill 개별내역은 빼고 avgPrice 로 요약.
//      (UserResponse 가 passwordHash 를 빼고 내보내는 것과 같은 이치)
//   3) DB 컬럼이 바뀌어도 API 응답 모양이 안 흔들린다 (엔티티와 응답이 분리돼 있어서).
// ════════════════════════════════════════════════════════════════════════════
