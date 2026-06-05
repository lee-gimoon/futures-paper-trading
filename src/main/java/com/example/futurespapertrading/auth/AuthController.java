package com.example.futurespapertrading.auth; // 이 파일이 속한 패키지(폴더) 경로

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
import org.springframework.security.web.server.context.ServerSecurityContextRepository;  // 인증 결과를 세션(쿠키)에 저장/복원하는 저장소
import org.springframework.web.bind.annotation.GetMapping;     // HTTP GET 요청을 이 메서드에 매핑
import org.springframework.web.bind.annotation.PostMapping;    // HTTP POST 요청을 이 메서드에 매핑
import org.springframework.web.bind.annotation.RequestBody;    // 요청의 JSON 본문을 자바 객체로 변환해 파라미터로 받음
import org.springframework.web.bind.annotation.RequestMapping; // 이 클래스의 모든 경로 앞에 붙는 공통 경로(prefix) 지정
import org.springframework.web.bind.annotation.ResponseStatus; // 성공 시 돌려줄 HTTP 상태코드를 지정
import org.springframework.web.bind.annotation.RestController; // 이 클래스가 REST API 컨트롤러임을 표시(반환값→JSON)
import org.springframework.web.server.ServerWebExchange;       // 한 번의 요청/응답/세션을 통째로 담은 WebFlux 객체
import org.springframework.web.server.WebSession;              // 서버 세션(로그인 상태를 담아두는 곳)
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
    // @RequestBody = 요청 JSON 본문을 SignupRequest 객체로 변환해서 받는다.
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
    // ServerWebExchange exchange = 이번 요청/응답/세션 묶음. 인증정보를 세션에 저장할 때 필요.
    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login(
            @Valid @RequestBody LoginRequest req, ServerWebExchange exchange) {
        // 1) 입력한 이메일/비번을 "인증 시도 토큰"으로 포장
        var token = new UsernamePasswordAuthenticationToken(req.email(), req.password());
        // 2) 인증 매니저에 검증 위임(내부적으로 유저 조회 + BCrypt 비번 비교)
        return authenticationManager.authenticate(token)
                .flatMap(auth -> {            // 3) auth = 검증 끝난 "신원증"(성공 시에만 들어옴)
                    SecurityContext context = new SecurityContextImpl(auth);  // 신원증을 세션이 알아듣는 표준 그릇에 담기
                    return securityContextRepository.save(exchange, context)  // 세션에 저장 + 응답에 SESSION 쿠키 써넣기
                            .thenReturn(ResponseEntity.ok(Map.of("message", "로그인 성공"))); // 저장 끝나면 → 200 응답 내보내기
                })
                // 4) 인증 실패 → 401 Unauthorized + 안내 메시지 (어느 쪽이 틀렸는지는 일부러 안 알려줌)
                .onErrorResume(AuthenticationException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of("message", "이메일 또는 비밀번호가 올바르지 않습니다."))));
    }

    // ── ③ 로그아웃 ──  POST /api/auth/logout
    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, String>>> logout(ServerWebExchange exchange) {
        // 세션 무효화 → 저장된 SecurityContext 제거 → 이후 요청은 미인증 상태가 된다
        return exchange.getSession()                  // 이번 요청의 세션을 꺼내서
                .flatMap(WebSession::invalidate)      // 세션을 무효화(폐기) → 저장돼 있던 인증정보도 함께 사라짐
                .thenReturn(ResponseEntity.ok(Map.of("message", "로그아웃 되었습니다."))); // 무효화 끝나면 → 200 응답 내보내기
    }

    // ── ④ 내 정보 조회 ──  GET /api/auth/me
    @GetMapping("/me")
    public Mono<UserResponse> me() {
        // 이 경로는 SecurityConfig에서 인증 필수 → 여기 도달하면 항상 로그인된 상태
        return ReactiveSecurityContextHolder.getContext()  // 현재 로그인된 인증정보(SecurityContext)를 꺼내옴
                .map(ctx -> ctx.getAuthentication().getName()) // 인증정보에서 사용자 식별자(email)를 꺼냄
                .flatMap(userRepository::findByEmail)          // 그 email로 DB에서 유저 조회
                .map(u -> new UserResponse(u.id(), u.email(), u.displayName())); // 비밀번호 뺀 정보만 응답
    }

    // ── 참고: 클라이언트가 받는 응답 모습 ──
    //
    //  ① 회원가입 성공            ② 로그인 성공                 ④ 로그인 실패(잘못된 비번)
    //   HTTP/1.1 201 Created       HTTP/1.1 200 OK             HTTP/1.1 401 Unauthorized
    //   {                         Set-Cookie: SESSION=...      {
    //     "id": 1,                {                              "message": "이메일 또는
    //     "email": "a@b.com",       "message": "로그인 성공"                비밀번호가 올바르지 않습니다."
    //     "displayName": "철수"   }                            }
    //   }
}
