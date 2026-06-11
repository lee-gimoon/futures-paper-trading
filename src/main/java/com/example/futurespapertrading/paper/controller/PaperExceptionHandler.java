package com.example.futurespapertrading.paper.controller;
import com.example.futurespapertrading.paper.exception.OrderForbiddenException;
import com.example.futurespapertrading.paper.exception.OrderNotFoundException;
import com.example.futurespapertrading.paper.exception.OrderNotOpenException;
import com.example.futurespapertrading.paper.exception.QuoteUnavailableException;

import java.util.Map;                                                // 응답 본문 {"message": "..."} 자료구조
import org.springframework.http.HttpStatus;                          // 404/403/409/503 상태코드 enum
import org.springframework.http.ResponseEntity;                      // 상태코드 + 본문을 함께 담는 HTTP 응답 객체
import org.springframework.web.bind.annotation.ExceptionHandler;     // 특정 예외 → 처리 메서드 매핑
import org.springframework.web.bind.annotation.RestControllerAdvice; // 전역 예외 처리기 표시

// paper 모듈의 예외 → HTTP 응답 번역기 (F-2). AuthExceptionHandler와 같은 @RestControllerAdvice 패턴 —
//   서비스가 던진 도메인 예외를 가로채 "상태코드 + {"message": ...}" 응답으로 바꾼다.
//   매핑: 주문 없음 404 / 남의 주문 403 / OPEN 아님 409 / 호가 없음 503.
//   리액티브에서도 동작 방식은 같다 — Mono.error(...)로 흘려보낸 예외도 WebFlux가 구독 중 받아 여기로 가져온다.
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

    // 상태코드만 다르고 본문 모양({"message": ...})은 같아서 공통 꼬리로 모음.
    private static ResponseEntity<Map<String, String>> toResponse(HttpStatus status, RuntimeException e) {
        return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
    }
}
