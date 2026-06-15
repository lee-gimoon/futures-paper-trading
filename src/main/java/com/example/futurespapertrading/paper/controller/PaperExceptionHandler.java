package com.example.futurespapertrading.paper.controller;
import com.example.futurespapertrading.paper.exception.InsufficientMarginException;
import com.example.futurespapertrading.paper.exception.InvalidLeverageException;
import com.example.futurespapertrading.paper.exception.OrderForbiddenException;
import com.example.futurespapertrading.paper.exception.OrderNotFoundException;
import com.example.futurespapertrading.paper.exception.OrderNotOpenException;
import com.example.futurespapertrading.paper.exception.QuoteUnavailableException;

import java.util.Map;                                                // 응답 본문 {"message": "..."} 자료구조
import org.springframework.http.HttpStatus;                          // 404/403/409/503 상태코드 enum
import org.springframework.http.ResponseEntity;                      // 상태코드 + 본문을 함께 담는 HTTP 응답 객체
import org.springframework.web.bind.annotation.ExceptionHandler;     // 특정 예외 → 처리 메서드 매핑
import org.springframework.web.bind.annotation.RestControllerAdvice; // 전역 예외 처리기 표시

// paper 모듈의 예외를 HTTP 응답으로 바꾸는 공통 처리기.
//
// 왜 필요한가:
//   서비스는 "주문이 없음", "남의 주문임", "호가가 아직 없음" 같은 문제를 도메인 예외로 표현한다.
//   하지만 예외 자체는 프론트가 이해할 수 있는 HTTP 응답이 아니므로, 여기서 상태코드와 JSON 본문으로 번역한다.
//
// 예:
//   PaperOrderService.cancel()
//   → OrderNotFoundException("주문이 없습니다: id=999")
//   → handleNotFound(...)
//   → HTTP 404 + {"message": "주문이 없습니다: id=999"}
//
// 각 컨트롤러에서 try-catch로 예외를 잡지 않아도 된다.
// 컨트롤러나 서비스에서 예외가 발생하면, Spring WebFlux가 요청 처리 중 그 예외를 감지한다.
// 그 후 @RestControllerAdvice 클래스들 중에서 해당 예외 타입을 처리하는 @ExceptionHandler 메서드를 찾아 호출한다.
// 리액티브에서는 예외가 throw로 튀어나오지 않고 Mono.error(...)라는 에러 신호로 흘러도,
// WebFlux가 Mono를 subscribe하고 있기 때문에 그 에러 신호를 받아 같은 방식으로 처리한다.
//
// 매핑:
//   주문 없음 404 / 남의 주문 403 / OPEN 아님 409 / 호가 없음 503 / 증거금·레버리지 입력 문제 400
@RestControllerAdvice
public class PaperExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(OrderNotFoundException e) {
        return toResponse(HttpStatus.NOT_FOUND, e);           // 404 — 그 id의 주문이 없음
    }

    @ExceptionHandler(OrderForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(OrderForbiddenException e) {
        return toResponse(HttpStatus.FORBIDDEN, e);           // 403 — 주문은 있지만 내 것이 아님
    }

    @ExceptionHandler(OrderNotOpenException.class)
    public ResponseEntity<Map<String, String>> handleNotOpen(OrderNotOpenException e) {
        return toResponse(HttpStatus.CONFLICT, e);            // 409 — 이미 끝난(FILLED 등) 주문이라 취소와 충돌
    }

    @ExceptionHandler(QuoteUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleQuoteUnavailable(QuoteUnavailableException e) {
        return toResponse(HttpStatus.SERVICE_UNAVAILABLE, e); // 503 — 호가 수신 전이라 일시적으로 처리 불가
    }

    @ExceptionHandler(InsufficientMarginException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientMargin(InsufficientMarginException e) {
        return toResponse(HttpStatus.BAD_REQUEST, e);         // 400 — 가용 증거금 부족 (레버리지 매수가능 검증)
    }

    @ExceptionHandler(InvalidLeverageException.class)
    public ResponseEntity<Map<String, String>> handleInvalidLeverage(InvalidLeverageException e) {
        return toResponse(HttpStatus.BAD_REQUEST, e);         // 400 — 허용하지 않는 레버리지 프리셋
    }

    // 상태코드만 다르고 본문 모양({"message": ...})은 같아서 공통 꼬리로 모음.
    private static ResponseEntity<Map<String, String>> toResponse(HttpStatus status, RuntimeException e) {
        return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
    }
}
