package com.example.futurespapertrading.market;

import java.math.BigDecimal;

// 호가창의 한 가격레벨. price와 quantity는 double이 아닌 BigDecimal로 다룬다.
// record = "한 번 만들면 못 바꾸는 데이터 명함" (불변 값 묶음).
//   └ "한 번 만들면"의 주체는 new OrderBookLevel(...)로 찍어낸 개별 객체.
//     타입(설계도)이 못 바뀐다는 뜻이 아니라, 그 설계도로 만든 객체 한 개의 price/quantity가 굳는다는 뜻.
//     값을 바꾸려면 기존 객체를 수정하는 게 아니라, new로 새 객체를 만들어 교체한다.
// record를 쓰는 이유: 값 객체(불변 데이터 묶음)는 record 한 줄이면 생성자/접근자/equals/hashCode/toString이 자동 생성된다.
public record OrderBookLevel(BigDecimal price, BigDecimal quantity) {
}

// record를 쓰지 않으면 아래처럼 일반 클래스로 같은 역할을 직접 구현해야 한다.
// (final 필드 + 생성자 + 접근자 + equals/hashCode/toString을 전부 손으로 작성)
//
// public final class OrderBookLevel {
//     private final BigDecimal price;
//     private final BigDecimal quantity;
//
//     public OrderBookLevel(BigDecimal price, BigDecimal quantity) {
//         this.price = price;
//         this.quantity = quantity;
//     }
//
//     public BigDecimal price() { return price; }
//     public BigDecimal quantity() { return quantity; }
//
//     @Override
//     public boolean equals(Object o) {
//         if (this == o) return true;
//         if (!(o instanceof OrderBookLevel other)) return false;
//         return java.util.Objects.equals(price, other.price)
//             && java.util.Objects.equals(quantity, other.quantity);
//     }
//
//     @Override
//     public int hashCode() {
//         return java.util.Objects.hash(price, quantity);
//     }
//
//     @Override
//     public String toString() {
//         return "OrderBookLevel[price=" + price + ", quantity=" + quantity + "]";
//     }
// }
