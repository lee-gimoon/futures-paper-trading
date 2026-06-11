package com.example.futurespapertrading.paper.exception;

// 도메인 예외(F-2) — 주문이 OPEN이 아니라 취소 불가(이미 FILLED/CANCELED/REJECTED로 끝난 상태).
//   PaperExceptionHandler가 HTTP 409(Conflict — 자원의 현재 상태와 요청이 충돌)로 번역한다.
//   (unchecked인 이유는 OrderNotFoundException 주석 참고)
public class OrderNotOpenException extends RuntimeException {
    public OrderNotOpenException(String message) {
        super(message);
    }
}
