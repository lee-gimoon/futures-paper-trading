package com.example.futurespapertrading.paper;

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
