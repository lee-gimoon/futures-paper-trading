package com.example.futurespapertrading.paper.exception;

// 도메인 예외(F-2) — 해당 id의 주문이 DB에 없음. PaperExceptionHandler가 HTTP 404로 번역한다.
//   서비스는 "주문이 없다"는 비즈니스 사실만 던지고, 어떤 상태코드로 보일지는 핸들러(웹 계층)가 정한다.
//   RuntimeException(unchecked) 상속인 이유: checked면 리액티브 체인의 람다 안에서 던질 수 없고(시그니처 강제),
//   어차피 호출부가 복구할 예외가 아니라 핸들러까지 그대로 흘려보낼 것이라서. (4개 도메인 예외 공통)
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String message) {
        super(message); // 부모에 메시지 보관 → 핸들러가 e.getMessage()로 꺼내 {"message": ...} 본문에 담는다
    }
}
