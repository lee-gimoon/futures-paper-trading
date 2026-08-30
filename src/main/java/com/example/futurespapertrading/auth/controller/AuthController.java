package com.example.futurespapertrading.auth.controller; // 이 파일이 속한 패키지(폴더) 경로
import com.example.futurespapertrading.auth.repository.UserRepository;
import com.example.futurespapertrading.auth.service.AuthService;

import com.example.futurespapertrading.auth.dto.LoginRequest;  // 로그인 요청 본문(email, password)을 담는 객체
import com.example.futurespapertrading.auth.dto.SignupRequest; // 회원가입 요청 본문(email, password, displayName)을 담는 객체
import com.example.futurespapertrading.auth.dto.UserResponse;  // 클라이언트에 돌려줄 유저 정보(비밀번호 제외)를 담는 객체
import jakarta.validation.Valid;                                            // @Valid: 요청 본문이 dto의 검증 규칙(@NotBlank 등)을 지키는지 검사 트리거
import java.util.Map;                                                       // 응답 본문을 {"message": "..."} 형태로 만들 때 쓰는 자료구조
import org.springframework.http.HttpStatus;                                 // 200, 201, 401 같은 HTTP 상태 코드 모음(열거형)
import org.springframework.http.ResponseEntity;                            // "상태코드 + 본문 + 헤더"를 함께 담는 HTTP 응답 객체
import org.springframework.security.authentication.ReactiveAuthenticationManager;        // 이메일/비밀번호가 맞는지 실제로 검증하는 인증 매니저
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;  // "이 이메일+비번으로 로그인 시도"를 담는 인증 요청 토큰
import org.springframework.security.core.AuthenticationException;                        // 인증 실패(비번 틀림, 유저 없음 등) 시 던져지는 예외
import org.springframework.security.core.context.ReactiveSecurityContextHolder;          // 현재 로그인한 사용자 정보를 꺼내오는 통로
import org.springframework.security.core.context.SecurityContext;                        // 인증 결과(누가 로그인했나)를 보관하는 그릇(인터페이스)
import org.springframework.security.core.context.SecurityContextImpl;                    // 위 SecurityContext의 실제 구현체
import org.springframework.security.web.server.context.ServerSecurityContextRepository;  // 인증 결과를 서버 WebSession에 저장/복원하는 저장소
import org.springframework.web.bind.annotation.GetMapping;     // HTTP GET 요청을 이 메서드에 매핑
import org.springframework.web.bind.annotation.PostMapping;    // HTTP POST 요청을 이 메서드에 매핑
import org.springframework.web.bind.annotation.RequestBody;    // 요청의 JSON 본문을 자바 객체로 변환해 파라미터로 받음
import org.springframework.web.bind.annotation.RequestMapping; // 이 클래스의 모든 경로 앞에 붙는 공통 경로(prefix) 지정
import org.springframework.web.bind.annotation.ResponseStatus; // 성공 시 돌려줄 HTTP 상태코드를 지정
import org.springframework.web.bind.annotation.RestController; // 이 클래스가 REST API 컨트롤러임을 표시(반환값→JSON)
import org.springframework.web.server.ServerWebExchange;       // 한 번의 요청/응답/세션을 통째로 담은 WebFlux 객체
import org.springframework.web.server.WebSession;              // 로그인 전 CSRF 토큰과 로그인 후 인증정보 등을 담는 서버 저장 공간
import reactor.core.publisher.Mono;                            // 0~1개의 결과를 "나중에" 비동기로 흘려보내는 리액티브 상자

// @RestController = REST API 컨트롤러 표시.
//  - HTTP 요청이 들어오는 "입구" 역할. 메서드 반환값이 자동으로 JSON 본문(HTTP 응답의 바디)으로 변환된다.
// @RequestMapping("/api/auth") = 이 클래스 안 모든 엔드포인트 경로 앞에 "/api/auth"가 붙는다.
//  - 예: 아래 @PostMapping("/signup")의 실제 경로는 → POST /api/auth/signup
@RestController
@RequestMapping("/api/auth")
public class AuthController { // 인증 관련 HTTP 요청을 받는 컨트롤러

    // ── 의존성 주입(DI): 아래 4개는 필드 선언(빈 그릇). 스프링이 생성자를 통해 실제 객체를 채워준다 ──
    private final AuthService authService;                                 // 회원가입 비즈니스 로직(이메일 중복검사 + 비번 해싱 + 저장)
    private final UserRepository userRepository;                           // DB에서 유저 조회/저장
    private final ReactiveAuthenticationManager authenticationManager;     // 로그인 시 이메일/비번 검증을 수행
    private final ServerSecurityContextRepository securityContextRepository; // 로그인 성공 후 인증정보를 세션에 저장

    // 생성자 주입 = 스프링이 미리 만들어둔 빈들을 이 생성자를 호출할 때 인자로 넘겨준다.
    // 개발자: "생성자 작성 + this.xxx = xxx 대입 코드 작성"
    // 스프링: "이미 만들어둔 빈(AuthService 등)을 이 생성자 호출 시 타입에 맞춰 인자로 전달"
    public AuthController(AuthService authService,
                          UserRepository userRepository,
                          ReactiveAuthenticationManager authenticationManager,
                          ServerSecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    // ── ① 회원가입 ──  POST /api/auth/signup
    // @ResponseStatus(HttpStatus.CREATED) = 성공 시 HTTP 201 Created로 응답.
    // @Valid = SignupRequest의 검증 규칙 위반 시 스프링이 자동으로 400 처리(여기 도달 전에 걸러짐).
    // @RequestBody = Spring WebFlux가 요청 JSON 본문을 읽고 Jackson을 통해 SignupRequest 객체로 자동 변환한다.
    //   직접 객체를 만드는 게 아니라 "요청 본문에서 가져와라"는 트리거(표시) 역할.
    //   @RequestBody(트리거) → Spring이 감지·중계 → Jackson 호출(실제 실행) → SignupRequest 객체 생성
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserResponse> signup(@Valid @RequestBody SignupRequest req) {
        // 서비스에 가입 위임 → 저장된 User를 받아 → 비밀번호 뺀 UserResponse로 변환해 응답
        // (이미 가입된 이메일이면 AuthService가 IllegalStateException → AuthExceptionHandler가 409로 변환)
        return authService.signup(req.email(), req.password(), req.displayName())
                .map(u -> new UserResponse(u.id(), u.email(), u.displayName()));
    }

    // ── ② 로그인 ──  POST /api/auth/login
    // 클라이언트가 HTTP 요청을 보내면 Spring WebFlux가 이를 ServerWebExchange로 감싼다.
    // 이후 요청 URL과 HTTP 메서드에 맞는 컨트롤러 메서드를 찾고, 해당 메서드를 호출할 때 현재 요청·응답과 세션 접근 기능을 담은 exchange를 자동 주입한다.
    // ServerWebExchange exchange = Spring WebFlux가 HTTP 요청 1건마다 만들고 컨트롤러에 전달하는 현재 요청 처리 객체.
    //                              요청·응답·WebSession과 서버가 요청에 덧붙인 부가 정보(attributes)를 함께 담는다.
    //   - request: 브라우저가 보낸 요청 전체
    //   - response: 브라우저에 보낼 응답을 준비하는 공간
    //   - session: exchange.getSession()이 구독될 때 전달하는 현재 요청의 WebSession.
    //              유효한 SESSION 쿠키가 있으면 그 ID의 기존 WebSession을 찾고, 없으면 새 WebSession을 준비한다.
    //   - attributes: 현재 HTTP 요청을 처리하는 동안 서버 코드가 exchange에 붙이는 부가 정보(속성) 목록.
    //                 예: CSRF 보호가 활성화되면 CsrfWebFilter가 "CSRF 토큰을 전달할 Mono<CsrfToken>"를 붙이고,
    //                 CsrfController가 같은 요청의 exchange에서 이를 읽어 응답을 만든다.
    //                 이 정보는 현재 ServerWebExchange(= 현재 HTTP 요청)에만 속한다.
    //                 요청 처리가 끝나면 exchange 자체의 수명이 끝나며, 그 안에 있던 attributes도 함께 의미를 잃고 정리 대상이 된다.
    // session = 로그인 전에는 CSRF 토큰을, 로그인 후에는 CSRF 토큰과 SecurityContext를 함께 기억하는 서버 저장 공간.
    // cookie = 브라우저가 다음 요청 때 "내 세션 번호"를 서버에 알려 주는 작은 정보.
    //
    // CSRF 보호를 적용한 세션 로그인 흐름:
    // 1) 로그인 전 GET /api/auth/csrf 응답에서 익명 WebSession과 SESSION 쿠키가 먼저 생긴다.
    // 2) 브라우저가 로그인 요청에 그 SESSION 쿠키와 CSRF 토큰을 함께 보낸다.
    // 3) 로그인 성공 시 아래 save(exchange, context)가 현재 WebSession에 SecurityContext를 추가한다.
    // 4) WebSessionServerSecurityContextRepository는 세션 고정 공격 방지를 위해 세션 ID를 변경하고,
    //    응답의 Set-Cookie로 브라우저의 기존 SESSION 쿠키 값을 새 세션 ID로 갱신한다.
    //
    // 따라서 SESSION 쿠키는 로그인할 때 처음 생길 수도 있지만, CSRF 적용 후 정상 흐름에서는
    // 로그인 전에 이미 존재하고 로그인 성공 응답에서 새 세션 ID로 갱신된다.
    // login()의 핵심 역할은 email/password를 검증하고, 성공하면 현재 WebSession에 로그인 사용자 정보를 저장하는 것이다.
    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login(
            @Valid @RequestBody LoginRequest req, ServerWebExchange exchange) {
        // 1) 인증 매니저(로그인 정보를 검증해 성공/실패를 결정하는 Spring Security 객체)는
        //    email과 password를 각각 받지 않고, 모든 인증 요청을 Authentication 토큰 하나로 받는다.
        //    그래서 로그인 입력(email/password)을 Spring Security 표준 "인증 요청 토큰"(UsernamePasswordAuthenticationToken)으로 포장해 전달한다.
        //    이 토큰은 아직 인증 전이며, 다음 authenticate(token)에서 DB 조회·비밀번호 검증이 수행된다.
        // var = 오른쪽 값으로 지역 변수의 타입을 컴파일 시 자동 추론하는 Java 문법(JavaScript var와 다름).
        var token = new UsernamePasswordAuthenticationToken(req.email(), req.password());
        // 2) authenticationManager = 로그인 인증(사용자 검증)을 담당하는 객체.
        //    아래 authenticate(token) 호출이 token의 email로 DB에서 사용자를 찾고, 입력한 원문 password와 DB의 BCrypt 해시를 비교해 인증 성공/실패를 결정한다.
        //    인증 성공 시에는 인증 완료된 Authentication(현재 사용자의 식별자·권한·인증 완료 여부를 담는 Spring Security 신원증)을 흘려보내는 Mono를 반환하고,
        //    실패하면 AuthenticationException 오류를 발생시킨다.
        return authenticationManager.authenticate(token)
                .flatMap(auth -> { // 3) 인증 성공 시에만 인증 완료된 Authentication 객체가 auth 변수로 들어온다.
                    SecurityContext context = new SecurityContextImpl(auth); // Authentication을 담는 Spring Security 표준 보안 정보 상자 생성
                    return securityContextRepository.save(exchange, context) // 현재 WebSession에 SecurityContext를 저장하고 세션 ID를 변경한다.
                            .thenReturn(ResponseEntity.ok(Map.of("message", "로그인 성공"))); // 세션 저장이 끝난 뒤 200 응답 반환
                })
                // 4) 인증 실패 시 발생한 AuthenticationException을 잡아 401 응답으로 바꾼다.
                //    email과 password 중 무엇이 틀렸는지는 보안을 위해 알려 주지 않는다.
                .onErrorResume(AuthenticationException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of("message", "이메일 또는 비밀번호가 올바르지 않습니다."))));
    }

    // ── ③ 로그아웃 ──  POST /api/auth/logout
    // 이 메서드가 반환한 Mono는 WebFlux가 클라이언트에 HTTP 응답을 보내기 위해 자동으로 구독한다.
    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, String>>> logout(ServerWebExchange exchange) {
        // 현재 WebSession을 무효화 → 그 안에 저장된 SecurityContext도 제거 → 이후 요청은 미인증 상태가 된다.
        return exchange.getSession()                  // 현재 요청의 SESSION 쿠키 ID로 연결된 WebSession을 가져와서
                .flatMap(WebSession::invalidate)      // WebSession을 무효화(폐기) → 저장된 인증정보도 함께 사라짐
                .thenReturn(ResponseEntity.ok(Map.of("message", "로그아웃 되었습니다."))); // 무효화가 끝나면 200 응답
    }

    // ── ④ 내 정보 조회 ──  GET /api/auth/me
    @GetMapping("/me")
    public Mono<UserResponse> me() {
        // 이 경로(/me)는 SecurityConfig의 permitAll 목록(signup·login·binance-futures)에 없어 .anyExchange().authenticated() 규칙에 걸린다.
        //   → 그래서 비로그인 요청은 Security 필터가 '컨트롤러에 닿기 전에' 401로 끊는다.
        //   → 즉 이 me() 코드가 실행된다는 것 자체가 로그인 통과를 뜻한다('여기 도달하면' = 필터를 통과해 이 메서드까지 옴).
        //     (그래서 아래 getContext()의 인증정보가 항상 존재 — null 걱정 없이 바로 꺼내 쓴다.)
        return ReactiveSecurityContextHolder.getContext()  // 현재 로그인된 인증정보(SecurityContext)를 꺼내옴
                .map(ctx -> ctx.getAuthentication().getName()) // 인증정보에서 사용자 식별자(email)를 꺼냄
                .flatMap(userRepository::findByEmail)          // 그 email로 DB에서 유저 조회
                .map(u -> new UserResponse(u.id(), u.email(), u.displayName())); // 비밀번호 뺀 정보만 응답
    }

    // ── 참고: 클라이언트가 받는 응답 모습 ──
    //
    //  ① 회원가입 성공            ② 로그인 성공                 ④ 로그인 실패(잘못된 비번)
    //   HTTP/1.1 201 Created       HTTP/1.1 200 OK             HTTP/1.1 401 Unauthorized
    //   {                         Set-Cookie: SESSION=새ID     {
    //     "id": 1,                {                              "message": "이메일 또는
    //     "email": "a@b.com",       "message": "로그인 성공"                비밀번호가 올바르지 않습니다."
    //     "displayName": "철수"   }                            }
    //   }
    //   ※ 로그인 성공의 Set-Cookie는 보통 /csrf에서 이미 만든 SESSION 쿠키의 값을 새 세션 ID로 갱신한다.
}
