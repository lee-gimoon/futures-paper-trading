package com.example.futurespapertrading.paper.dto; // dto = 계층 간 데이터 운반 전용 객체 모음

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue; // boolean 메서드가 true여야 통과 — 교차(여러 필드) 검증에 쓴다
import jakarta.validation.constraints.NotBlank; // null·빈문자열·공백만 금지
import jakarta.validation.constraints.NotNull;  // null 금지(숫자라 NotBlank 대신 NotNull)
import jakarta.validation.constraints.Pattern;  // 문자열이 정해진 형식(정규식)과 맞는지 검사
import jakarta.validation.constraints.Positive; // 0과 음수 금지 (양수만)

// 주문 생성 요청 본문(JSON) DTO. POST /api/paper/orders 의 @RequestBody로 받는다.
//  - 왜 record인가: 컨트롤러가 req.symbol()·req.quantity()를 '꺼내 읽기만' 하고 값을 바꾸지 않아서,
//    요청 본문은 "한 번 만들어지면 안 바뀌는 데이터 묶음"이다 → 불변(immutable)인 record가 딱 맞다.
//  - record·DTO·@Valid 검증 흐름의 자세한 설명은 auth/dto/SignupRequest.java 참고. (여기선 주문 고유 규칙만)
//  - 검증 규칙을 어기면 컨트롤러 로직에 닿기 전에 스프링이 자동으로 HTTP 400(요청 형식이나 값이 잘못돼서 서버가 처리할 수 없음)으로 막는다.
public record CreateOrderRequest(
        // 종목. 현재는 BTCUSDT 하나만 지원 — 다른 symbol을 받으면 전부 BTCUSDT 호가로 체결해버리므로 여기서 400으로 막는다.
        @NotBlank @Pattern(regexp = "BTCUSDT", message = "지원하는 symbol은 BTCUSDT 뿐입니다") String symbol,

        // 주문 방향. @Pattern = 이 side 문자열이 주어진 정규식(regexp)과 맞는지 검사하는 제약.
        //   regexp "BUY|SELL" (| = 정규식의 OR) → 문자열 전체가 "BUY" 또는 "SELL"이어야 통과.
        //   오타("buy")는 여기서 400으로 걸러, 엔진의 OrderSide.valueOf가 IllegalArgumentException을 던지기 전에 막는다.
        @Pattern(regexp = "BUY|SELL", message = "side는 BUY 또는 SELL 이어야 합니다") String side,

        // 주문 종류. "MARKET" / "LIMIT" 형식 검사.
        @Pattern(regexp = "MARKET|LIMIT", message = "type은 MARKET 또는 LIMIT 이어야 합니다") String type,

        // 주문 수량. null 금지 + 양수만(@Positive: 0·음수 거부). 가격/수량은 double 아닌 BigDecimal.
        @NotNull @Positive BigDecimal quantity,

        // 지정가 한도. 시장가면 null(안 씀). 지정가(LIMIT)일 때만 필수+양수 — 아래 @AssertTrue(isLimitPriceValid)가 검사.
        BigDecimal limitPrice
) {
    // record의 괄호(...)는 이 DTO 객체가 실제로 담는 값 목록이고,
    // 본문({})은 그 값들을 이용한 추가 규칙/동작을 넣는 곳이다.
    // 이 메서드는 새 값을 담는 칸이 아니라, type·limitPrice 조합이 유효한지 계산하는 검증용 메서드다.
    // @AssertTrue가 붙은 메서드는 @Valid 검증 때 Validator가 자동 호출한다.
    // 이 메서드가 true를 돌려줘야 검증 통과, false면 @Valid 검증 실패 → 400 Bad Request.
    @AssertTrue(message = "지정가(LIMIT)는 limitPrice가 양수여야 합니다")
    private boolean isLimitPriceValid() {
        if (!"LIMIT".equals(type)) return true;                 // 시장가면 limitPrice를 안 쓰므로 통과(null이어도 OK)
        return limitPrice != null && limitPrice.signum() > 0;   // 지정가면 반드시 있고 양수여야 (signum>0 = 양수)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// record를 안 쓰면? 위 record 한 묶음이 사실은 아래 클래스 전체와 (거의) 같다.
// record는 컴파일러가 이 코드를 대신 만들어 주는 "문법 설탕(syntactic sugar)"일 뿐이다.
//   ※ 검증 애너테이션(@NotBlank 등)은 record에선 컴포넌트에 붙이지만, 일반 클래스에선 필드에 붙인다.
//
// public final class CreateOrderRequest {                 // record는 항상 final — 상속 불가
//
//     // 1) 모든 필드가 private final — 한 번 정해지면 못 바꾼다(불변, immutable)
//     @NotBlank
//     private final String symbol;
//     @Pattern(regexp = "BUY|SELL", message = "side는 BUY 또는 SELL 이어야 합니다")
//     private final String side;
//     @Pattern(regexp = "MARKET|LIMIT", message = "type은 MARKET 또는 LIMIT 이어야 합니다")
//     private final String type;
//     @NotNull @Positive
//     private final BigDecimal quantity;
//     private final BigDecimal limitPrice;
//
//     // 2) 모든 필드를 받는 생성자(canonical constructor)
//     public CreateOrderRequest(String symbol, String side, String type,
//                               BigDecimal quantity, BigDecimal limitPrice) {
//         this.symbol = symbol;
//         this.side = side;
//         this.type = type;
//         this.quantity = quantity;
//         this.limitPrice = limitPrice;
//     }
//
//     // 3) 접근자(accessor) — 주의: record는 getSymbol()이 아니라 symbol() 처럼 필드명과 똑같다
//     public String symbol()         { return symbol; }
//     public String side()           { return side; }
//     public String type()           { return type; }
//     public BigDecimal quantity()   { return quantity; }
//     public BigDecimal limitPrice() { return limitPrice; }
//
//     // 4) equals — 모든 필드 값이 같으면 같은 객체로 본다(값 기반 동등성)
//     @Override
//     public boolean equals(Object o) {
//         if (this == o) return true;
//         if (!(o instanceof CreateOrderRequest other)) return false;
//         return java.util.Objects.equals(symbol, other.symbol)
//                 && java.util.Objects.equals(side, other.side)
//                 && java.util.Objects.equals(type, other.type)
//                 && java.util.Objects.equals(quantity, other.quantity)
//                 && java.util.Objects.equals(limitPrice, other.limitPrice);
//     }
//
//     // 5) hashCode — equals와 짝. 같은 값이면 같은 해시(HashMap/HashSet에서 필요)
//     @Override
//     public int hashCode() {
//         return java.util.Objects.hash(symbol, side, type, quantity, limitPrice);
//     }
//
//     // 6) toString — 디버깅용 출력. "CreateOrderRequest[symbol=BTCUSDT, side=BUY, ...]" 형태
//     @Override
//     public String toString() {
//         return "CreateOrderRequest[symbol=" + symbol + ", side=" + side
//                 + ", type=" + type + ", quantity=" + quantity
//                 + ", limitPrice=" + limitPrice + "]";
//     }
// }
// ─────────────────────────────────────────────────────────────────────────────
