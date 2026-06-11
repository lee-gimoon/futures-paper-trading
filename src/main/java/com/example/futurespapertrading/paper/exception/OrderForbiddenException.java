package com.example.futurespapertrading.paper.exception;

// 도메인 예외(F-2) — 주문은 있지만 내 것이 아님(소유자 불일치). PaperExceptionHandler가 HTTP 403으로 번역한다.
//
// 왜 내장 예외를 안 쓰고 클래스를 따로 만드나 (근본 이유): @ExceptionHandler는 '예외 타입' 단위로 매핑된다.
//   즉 예외 클래스가 곧 "어떤 실패인가"의 식별자 — 404/403/409/503으로 갈라야 할 실패가 4종류니 구분 가능한 타입도 4개 필요.
//   내장(IllegalStateException 등)으로 던지면:
//   ① 타입이 같아 핸들러가 실패들을 구분 못 한다(남는 수단은 메시지 문자열 파싱 — 한 글자만 바뀌어도 깨지는 최악의 분기).
//   ② 범용이라 도메인끼리 충돌한다 — 실제로 AuthExceptionHandler가 전역으로 IllegalStateException→409를 선점 중이라,
//      여기서 그걸 던지면 403이 아니라 엉뚱한 409로 나간다.
//   ③ 전용 타입은 이름이 곧 문서 — throw new OrderForbiddenException(...)은 주석 없이도 "남의 주문 접근"으로 읽힌다.
//   (unchecked인 이유는 OrderNotFoundException 주석 참고)
public class OrderForbiddenException extends RuntimeException { // RuntimeException 상속 = unchecked 선언. 본문에 더할 게 없다 — '타입(이름)' 자체가 정보라서.
    public OrderForbiddenException(String message) {  // 던지는 쪽(서비스)이 상황 메시지를 넣어 만든다: new OrderForbiddenException("내 주문이 아닙니다.")
        super(message);  // 부모(RuntimeException)에 메시지 보관 → 핸들러가 e.getMessage()로 꺼내 {"message": ...} 응답 본문에 담는다
    }
}
