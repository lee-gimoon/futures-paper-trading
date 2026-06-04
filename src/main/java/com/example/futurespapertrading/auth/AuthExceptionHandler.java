package com.example.futurespapertrading.auth; // 이 파일이 속한 패키지(폴더) 경로

import java.util.Map;                                                // 응답 본문을 {"message": "..."} 형태로 만들 때 쓰는 자료구조
import org.springframework.http.HttpStatus;                          // 200, 409 같은 HTTP 상태 코드 모음(열거형)
import org.springframework.http.ResponseEntity;                      // "상태코드 + 본문 + 헤더"를 함께 담는 HTTP 응답 객체
import org.springframework.web.bind.annotation.ExceptionHandler;     // 특정 예외를 처리하는 메서드임을 표시하는 애너테이션
import org.springframework.web.bind.annotation.RestControllerAdvice; // 전역 예외 처리 클래스임을 표시하는 애너테이션

// @RestControllerAdvice = 전역(공통) 예외 처리기 표시.
//  - 모든 @RestController에서 "던져진(throw) 예외"를 한 곳에서 가로채 처리한다.
//    → 컨트롤러마다 try-catch를 쓰지 않고, 예외→HTTP응답 변환을 여기로 모은다.
//  - @ControllerAdvice + @ResponseBody 를 합친 것 → 메서드 반환값이 JSON 본문으로 변환된다.
//  - 여기 목적: 중복 이메일 등(IllegalStateException) → 409로 변환. (검증 실패는 스프링이 기본 400 처리)
@RestControllerAdvice
public class AuthExceptionHandler { // 예외를 응답으로 바꾸는 핸들러 클래스

    // @ExceptionHandler(IllegalStateException.class)
    //  = "IllegalStateException이 발생하면 이 메서드를 호출하라"는 매핑.
    //    (AuthService.signup이 이미 가입된 이메일일 때 이 예외를 던진다)
    //
    // 직접 호출 X — IllegalStateException 발생 시 스프링 WebFlux의 RequestMappingHandlerAdapter가
    // 맞는 @ExceptionHandler를 찾아 InvocableHandlerMethod로 리플렉션 호출한다. (no usage 표시는 정상) 리플렉션 호출=메서드 이름을 코드에 하드코딩하지 않고 런타임에 찾아 부르는 것.
    // (Handler = 처리자: 특정 요청/예외가 왔을 때 "그걸 맡아 처리하는 역할"을 뜻하는 영어 단어)
    @ExceptionHandler(IllegalStateException.class)
    // 반환 타입 ResponseEntity<Map<String,String>> = 상태코드 + JSON 본문({"message": ...})
    // 파라미터 e = 실제로 던져진 예외 객체 (안의 메시지를 꺼내 쓰려고 받는다)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)              // 응답 상태를 HTTP 409 Conflict(자원 충돌=중복)로 지정
                .body(Map.of("message", e.getMessage())); // 본문에 예외 메시지를 {"message": "이미 가입된 이메일입니다."} 형태로 담음
    } // → 최종적으로 "409 + JSON 본문"이 클라이언트로 전송된다

    // ── 실제로 클라이언트가 받는 응답 모습 (예: 이미 있는 이메일로 회원가입 시도 시) ──
    //
    //   HTTP/1.1 409 Conflict
    //   Content-Type: application/json
    //
    //   {
    //     "message": "이미 가입된 이메일입니다."
    //   }
    //
    //   - 첫 줄 409 Conflict  ← ResponseEntity.status(HttpStatus.CONFLICT)
    //   - 본문 JSON          ← Map.of("message", e.getMessage()) 가 변환된 것
    //   - e.getMessage()     ← AuthService에서 throw new IllegalStateException("이미 가입된 이메일입니다.") 의 그 문구
}
