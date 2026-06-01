package com.example.futurespapertrading.auth;

import com.example.futurespapertrading.auth.dto.LoginRequest;
import com.example.futurespapertrading.auth.dto.SignupRequest;
import com.example.futurespapertrading.auth.dto.UserResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final ReactiveAuthenticationManager authenticationManager;
    private final ServerSecurityContextRepository securityContextRepository;

    public AuthController(AuthService authService,
                          UserRepository userRepository,
                          ReactiveAuthenticationManager authenticationManager,
                          ServerSecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserResponse> signup(@Valid @RequestBody SignupRequest req) {
        return authService.signup(req.email(), req.password(), req.displayName())
                .map(u -> new UserResponse(u.id(), u.email(), u.displayName()));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login(
            @Valid @RequestBody LoginRequest req, ServerWebExchange exchange) {
        var token = new UsernamePasswordAuthenticationToken(req.email(), req.password());
        return authenticationManager.authenticate(token)
                .flatMap(auth -> {
                    // 인증 성공 → SecurityContext를 세션에 저장(= SESSION 쿠키 발급)
                    SecurityContext context = new SecurityContextImpl(auth);
                    return securityContextRepository.save(exchange, context)
                            .thenReturn(ResponseEntity.ok(Map.of("message", "로그인 성공")));
                })
                .onErrorResume(AuthenticationException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of("message", "이메일 또는 비밀번호가 올바르지 않습니다."))));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, String>>> logout(ServerWebExchange exchange) {
        // 세션 무효화 → 저장된 SecurityContext 제거 → 이후 요청은 미인증
        return exchange.getSession()
                .flatMap(WebSession::invalidate)
                .thenReturn(ResponseEntity.ok(Map.of("message", "로그아웃 되었습니다.")));
    }

    @GetMapping("/me")
    public Mono<UserResponse> me() {
        // 이 경로는 SecurityConfig에서 인증 필수 → 여기 도달하면 항상 인증된 상태
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName()) // email
                .flatMap(userRepository::findByEmail)
                .map(u -> new UserResponse(u.id(), u.email(), u.displayName()));
    }
}
