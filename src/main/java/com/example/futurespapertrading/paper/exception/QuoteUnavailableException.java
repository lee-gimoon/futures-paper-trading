package com.example.futurespapertrading.paper.exception;

// 호가(quote)가 아직 준비되지 않았을 때 쓰는 도메인 예외.
//   Quote = 주문 체결이나 증거금 계산에 필요한 현재 호가/가격 정보.
//   Unavailable = 지금 사용할 수 없음. 즉, 최신 호가를 아직 받지 못해 시장가 주문을 처리할 기준 가격이 없는 상태다.
//   이름이 QuoteUnavailableException인 이유도 "호가 정보가 없어서 지금은 처리할 수 없는 예외"라는 뜻을 드러내기 위해서다.
//   서비스는 이 예외만 던지고 HTTP 상태코드는 모르며, PaperExceptionHandler가 503 Service Unavailable로 번역한다.
public class QuoteUnavailableException extends RuntimeException {
    public QuoteUnavailableException(String message) {
        super(message);
    }
}
