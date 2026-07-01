package com.example.futurespapertrading.paper.exception;

// 새로 여는(또는 늘리는) 주문의 필요 증거금이 가용잔고를 넘을 때. (레버리지 도입 — 매수가능 검증)
//   PaperExceptionHandler가 400으로 번역한다. (서비스는 HTTP를 모름 — 다른 도메인 예외와 같은 패턴)
public class InsufficientMarginException extends RuntimeException {
    // super(message)는 RuntimeException 부모 생성자에 메시지를 넘겨 예외 객체 안에 저장한다.
    // 그래야 나중에 PaperExceptionHandler가 e.getMessage()로 같은 문구를 꺼내 HTTP 응답 본문에 담을 수 있다.
    // 주의: Java 생성자에서는 super(...) 호출이 반드시 첫 실행문이어야 하므로, 생성자 본문 안에서 이 줄보다 앞에 코드를 둘 수 없다.
    public InsufficientMarginException(String message) {
        super(message);
    }
}

// 왜 RuntimeException을 바로 쓰지 않고 InsufficientMarginException 클래스를 따로 만들었나:
//   메시지는 사람이 읽는 설명이고, 예외 타입은 코드가 상황을 구분하는 기준이다.
//   모든 문제를 RuntimeException으로 던지면 "주문 없음", "남의 주문", "증거금 부족"이 코드상 같은 타입이 된다.
//   그러면 PaperExceptionHandler가 어떤 예외를 404/403/400 중 무엇으로 바꿔야 하는지 명확히 구분하기 어렵다.
//   그래서 증거금 부족에는 InsufficientMarginException이라는 이름을 붙이고,
//   PaperExceptionHandler가 이 타입을 보고 HTTP 400 Bad Request로 번역하게 한다.

// 체크 예외(Checked Exception)와 런타임 예외(Runtime Exception)의 차이:
//   체크 예외는 컴파일러가 처리 강제한다.
//   예를 들어 Files.readString(...)처럼 throws IOException이 선언된 메서드를 호출하면,
//   실제 실행에서 파일 읽기가 성공하더라도 코드에는 반드시 try-catch 또는 throws가 있어야 컴파일된다.
//
//   런타임 예외는 컴파일러가 처리 강제하지 않는다.
//   RuntimeException을 상속한 예외는 메서드 선언에 throws를 적거나 호출부마다 try-catch를 쓰지 않아도 컴파일된다.
//   이 프로젝트의 도메인 예외들은 "요청은 들어왔지만 비즈니스 규칙상 처리할 수 없음"을 표현하고,
//   PaperExceptionHandler가 한 곳에서 HTTP 응답으로 번역하므로 RuntimeException 계열로 둔다.
