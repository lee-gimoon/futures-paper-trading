package com.example.futurespapertrading.paper.exception;

// 도메인 예외(F-2) — 호가 수신 전이라 체결 기준이 없음(시장가 즉시 체결 불가). PaperExceptionHandler가 HTTP 503으로 번역한다.
//   C단계에선 서비스가 웹 계층 타입인 ResponseStatusException(503)을 직접 던졌는데 F-2에서 이걸로 정리 —
//   서비스는 HTTP를 모르게 하고, 예외→상태코드 번역은 핸들러 한 곳으로 모은다. (컨트롤러=HTTP, 서비스=비즈니스 분리의 마무리)
//   (unchecked인 이유는 OrderNotFoundException 주석 참고)
public class QuoteUnavailableException extends RuntimeException {
    public QuoteUnavailableException(String message) {
        super(message);
    }
}
