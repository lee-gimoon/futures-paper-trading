package com.example.futurespapertrading.auth.controller;

import com.example.futurespapertrading.auth.dto.CsrfTokenResponse;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;



// 브라우저가 React 정적 파일을 실행하면 사용자 컴퓨터의 RAM에 JavaScript 실행 메모리가 생긴다.
// React는 이 API에서 받은 CSRF 토큰을 그 메모리의 csrfState 변수에 저장하며, 새로고침하면 사라지므로 다시 받아야 한다.
//
// GET /api/auth/csrf 요청이 오면 현재 WebSession의 CSRF 토큰 정보를 JSON으로 응답하는 컨트롤러다.
// 응답 예시: {"headerName":"X-CSRF-TOKEN","token":"abc123"}
@RestController
@RequestMapping("/api/auth")
public class CsrfController {

    // 로그인 전에도 호출해야 하므로 SecurityConfig에서 이 경로는 permitAll로 공개한다.
    // 이 GET 요청은 CSRF 토큰을 제출하지 않고, 현재 세션에서 사용할 토큰을 응답으로 받는다.
    @GetMapping("/csrf")
    public Mono<CsrfTokenResponse> csrf(ServerWebExchange exchange) {
        // CsrfWebFilter가 현재 요청의 속성에 넣어 둔 CSRF 토큰 Mono를 꺼낸다.
        // 이 Mono에는 현재 WebSession에서 조회하거나 새로 준비할 토큰이 들어온다.
        Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());

        if (csrfToken == null) {
            // 요청 속성 자체가 없으면 CSRF 필터가 꺼져 있거나 이 요청에 적용되지 않은 설정 오류다.
            return Mono.error(new IllegalStateException("CSRF 토큰을 생성하지 못했습니다."));
        }

        // 준비된 CsrfToken에서 헤더 이름과 토큰 값을 꺼내 클라이언트용 응답 DTO로 바꾼다.
        // 이 Mono가 구독될 때 필요한 토큰 조회·생성과 세션 저장도 실행된다.
        return csrfToken.map(token -> new CsrfTokenResponse(
                token.getHeaderName(),
                token.getToken()
        ));
    }
}


// ── 홈페이지 진입부터 CSRF 토큰 응답까지의 전체 흐름 ──
//
// 1) 사용자가 브라우저에서 홈페이지를 열면 먼저 GET / 요청으로 React 앱을 받는다.
//    단순히 GET / 요청을 했다는 이유만으로 WebSession이 반드시 시작되는 것은 아니다.
//
// 2) React 앱이 실행되면 POST·PUT·PATCH·DELETE 같은 상태 변경 요청에 사용할 토큰을 준비하기 위해
//    GET /api/auth/csrf를 호출한다. GET은 조회 전용이므로 토큰 제출 대상에서 제외한다.
//
// 3) SESSION 쿠키가 있으면 기존 WebSession을 사용한다. 쿠키가 없으면 새 WebSession을 준비하고,
//    CSRF 저장소가 토큰을 WebSession 속성에 넣을 때 실제 세션을 시작한다.
//
// 4) 새 세션을 시작한 경우 응답에 세션 ID와 CSRF 토큰 정보가 함께 담긴다.
//    Set-Cookie: SESSION=S-1234
//    {"headerName":"X-CSRF-TOKEN","token":"T-9876"}
//
// 5) 이 WebSession에는 CSRF 토큰만 있고 SecurityContext는 아직 없으므로 사용자는 비로그인 상태다.
//    즉, 세션이 있다는 것과 로그인했다는 것은 서로 다른 의미다.
//
// 브라우저는 우리 서버로 요청할 때 SESSION 쿠키를 조건에 따라 자동으로 붙인다.
// 그래서 악성 사이트가 우리 서버로 요청을 유도해도 로그인 세션 쿠키가 함께 전송될 수 있다.
// 반면 CSRF 토큰은 브라우저가 자동으로 붙이지 않고, 우리 React 코드가 요청 헤더에 직접 넣는다.
// 서버는 SESSION 쿠키와 CSRF 토큰이 모두 맞아야 변경 요청을 허용하므로, 세션 쿠키만 있고 CSRF 토큰이 없는 악성 요청은 403으로 거부된다.
//
//
// 브라우저는 아래 쿠키 전체 정보로 전송 여부를 판단하고, 조건이 맞으면 요청 헤더에 이름과 값만 자동으로 붙인다.
// 실제 요청 예시: Cookie: SESSION=S-1234
//
// 브라우저 쿠키 저장소에 들어 있는 쿠키 한 개의 정보:
// 이름:        SESSION
// 값:          S-1234
// 대상 호스트: trading.example.com
// Path:        /
// SameSite:    Lax
// HttpOnly:    true
// Secure:      true 또는 false
// 만료시간:     브라우저 종료 시 또는 지정된 시간
