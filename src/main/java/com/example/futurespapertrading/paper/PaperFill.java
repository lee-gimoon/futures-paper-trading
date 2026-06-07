package com.example.futurespapertrading.paper;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

// paper_fills 테이블 한 줄 ↔ 자바 객체. "체결 한 건"의 기록이다.
// 한 주문(order_id)이 여러 번에 나눠 체결되면 fill이 여러 개 생기는 1:N 관계다.
//   예) 시장가 10개 매수인데 best 매도가 3개뿐이면, 다음 호가까지 긁어 3+2+5처럼
//       가격별로 쪼개져 체결된다(부분 체결). 이때 fill 행이 여러 개 생긴다.
//   왜 쪼개지나? = 가격이 달라서다. PaperFill은 price 필드를 딱 하나만 가진다
//       (한 레코드 = 한 가격). 그래서 체결 가격이 바뀔 때마다 fill을 새로 끊어야 한다.
//       (호가창 유동성이 가격마다 한정돼서, 한 레벨을 다 먹으면 더 비싼 다음 가격으로 넘어감)
//   8단계 MVP(로드맵 8단계의 최소 버전)도 best ask부터 위로 호가를 긁어 체결한다.
//   따라서 큰 주문은 fill N건이 생긴다. 단 완료 기준의 0.01 같은 작은 수량은
//   best 한 줄에서 다 차서 보통 fill 1건. (queue priority·maker/taker·슬리피지·
//   체결 후 호가 수량 차감 같은 정밀 모델은 나중 단계로 미룬다.)
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

// ─────────────────────────────────────────────────────────────────────────────
// record를 안 쓰면? 위 한 줄이 사실은 아래 클래스 전체와 (거의) 같다.
// record는 컴파일러가 이 코드를 대신 만들어 주는 "문법 설탕(syntactic sugar)"일 뿐이다.
//
// @Table("paper_fills")
// public final class PaperFill {                 // record는 항상 final — 상속 불가
//
//     // 1) 모든 필드가 private final — 한 번 정해지면 못 바꾼다(불변, immutable)
//     @Id
//     private final Long id;
//     @Column("order_id")
//     private final Long orderId;
//     @Column("account_id")
//     private final Long accountId;
//     private final String symbol;
//     private final String side;
//     private final BigDecimal price;
//     private final BigDecimal quantity;
//     private final BigDecimal fee;
//
//     // 2) 모든 필드를 받는 생성자(canonical constructor)
//     public PaperFill(Long id, Long orderId, Long accountId, String symbol,
//                      String side, BigDecimal price, BigDecimal quantity, BigDecimal fee) {
//         this.id = id;
//         this.orderId = orderId;
//         this.accountId = accountId;
//         this.symbol = symbol;
//         this.side = side;
//         this.price = price;
//         this.quantity = quantity;
//         this.fee = fee;
//     }
//
//     // 3) 접근자(accessor) — 주의: record는 getId()가 아니라 id() 처럼 필드명과 똑같다
//     public Long id()            { return id; }
//     public Long orderId()       { return orderId; }
//     public Long accountId()     { return accountId; }
//     public String symbol()      { return symbol; }
//     public String side()        { return side; }
//     public BigDecimal price()   { return price; }
//     public BigDecimal quantity(){ return quantity; }
//     public BigDecimal fee()     { return fee; }
//
//     // 4) equals — 모든 필드 값이 같으면 같은 객체로 본다(값 기반 동등성)
//     @Override
//     public boolean equals(Object o) {
//         if (this == o) return true;
//         if (!(o instanceof PaperFill other)) return false;
//         return java.util.Objects.equals(id, other.id)
//                 && java.util.Objects.equals(orderId, other.orderId)
//                 && java.util.Objects.equals(accountId, other.accountId)
//                 && java.util.Objects.equals(symbol, other.symbol)
//                 && java.util.Objects.equals(side, other.side)
//                 && java.util.Objects.equals(price, other.price)
//                 && java.util.Objects.equals(quantity, other.quantity)
//                 && java.util.Objects.equals(fee, other.fee);
//     }
//
//     // 5) hashCode — equals와 짝. 같은 값이면 같은 해시(HashMap/HashSet에서 필요)
//     @Override
//     public int hashCode() {
//         return java.util.Objects.hash(id, orderId, accountId, symbol, side, price, quantity, fee);
//     }
//
//     // 6) toString — 디버깅용 출력. "PaperFill[id=1, orderId=10, ...]" 형태
//     @Override
//     public String toString() {
//         return "PaperFill[id=" + id + ", orderId=" + orderId + ", accountId=" + accountId
//                 + ", symbol=" + symbol + ", side=" + side + ", price=" + price
//                 + ", quantity=" + quantity + ", fee=" + fee + "]";
//     }
// }
// ─────────────────────────────────────────────────────────────────────────────
