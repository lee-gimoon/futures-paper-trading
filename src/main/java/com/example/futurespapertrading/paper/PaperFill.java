package com.example.futurespapertrading.paper;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

// paper_fills 테이블 한 줄 ↔ 자바 객체. "체결 한 건"의 기록이다.
// 한 주문(order_id)이 여러 번에 나눠 체결되면 fill이 여러 개 생길 수 있어 주문과 1:N 관계다.
//   (8단계 MVP 시장가는 best 한 가격에 통째 체결이라 보통 주문 1건당 fill 1건)
//
// side는 String, price/quantity/fee는 BigDecimal — 이유는 PaperOrder/OrderSide와 동일.
// executed_at은 DB가 DEFAULT now()로 채우므로 record에 두지 않는다.
@Table("paper_fills")
public record PaperFill(
        @Id Long id,
        @Column("order_id") Long orderId,        // 어느 주문의 체결인지 (paper_orders.id)
        @Column("account_id") Long accountId,    // 9단계 전까지 null
        String symbol,
        String side,                             // "BUY" / "SELL"
        BigDecimal price,                        // 실제 체결 가격
        BigDecimal quantity,                     // 실제 체결 수량
        BigDecimal fee                           // 수수료. 8단계는 0, 나중 단계에서 사용
) {
}
