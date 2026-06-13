package com.example.futurespapertrading.paper.exception;

// 새로 여는(또는 늘리는) 주문의 필요 증거금이 가용잔고를 넘을 때. (레버리지 도입 — 매수가능 검증)
//   PaperExceptionHandler가 400으로 번역한다. (서비스는 HTTP를 모름 — 다른 도메인 예외와 같은 패턴)
public class InsufficientMarginException extends RuntimeException {
    public InsufficientMarginException(String message) {
        super(message);
    }
}
