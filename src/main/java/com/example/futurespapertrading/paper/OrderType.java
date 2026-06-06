package com.example.futurespapertrading.paper;

// 주문 종류.
//   MARKET(시장가) = "지금 호가창의 best 가격에 당장 체결해줘". 가격을 따로 안 정한다 → limit_price 없음.
//   LIMIT(지정가)  = "이 가격(limit_price) 이하로 사겠다 / 이상으로 팔겠다". 닿을 때까지 OPEN으로 기다릴 수 있다.
//
// (DB에는 String으로 저장한다 — 이유는 OrderSide 참고.)
public enum OrderType {
    MARKET,
    LIMIT
}
