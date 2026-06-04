package com.example.futurespapertrading.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            ServerSecurityContextRepository securityContextRepository) {
        return http
                // SPA + 우선 단순화: CSRF off, 폼로그인/베이직 리다이렉트 off (커스텀 JSON 로그인 사용)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                // 미인증(401) 요청이 보호 경로에 닿았을 때의 응답 방식을 설정한다.
                //   - ex = Spring이 넘겨주는 ExceptionHandlingSpec(예외 처리 설정 객체)
                //   - authenticationEntryPoint(전략) = 미인증 요청에 "어떻게 응답할지" 지정 (권한 부족 403은 accessDeniedHandler 담당)
                //   - HttpStatusServerEntryPoint(코드) = 본문·헤더 없이 그 상태코드만 내려주는 최소 구현체
                //   - HttpStatus.UNAUTHORIZED = 내려줄 코드(401) → "WWW-Authenticate" 헤더가 안 붙어 브라우저 로그인 팝업이 안 뜸
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                // 인증 컨텍스트를 WebSession(=쿠키)에 저장/복원
                .securityContextRepository(securityContextRepository)
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        // 기존 시세/호가 API는 로그인 없이도 접근 가능하게 유지
                        .pathMatchers("/api/binance-futures/**").permitAll()
                        .anyExchange().authenticated())
                .build();
    }

    // 로그인 컨트롤러가 직접 호출하는 인증 매니저 (UserDetailsService + BCrypt 비교)
    @Bean
    public ReactiveAuthenticationManager authenticationManager(
            ReactiveUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        UserDetailsRepositoryReactiveAuthenticationManager manager =
                new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
        manager.setPasswordEncoder(passwordEncoder);
        return manager;
    }

    // 인증 성공 후 SecurityContext를 세션에 저장 → SESSION 쿠키 발급/복원
    @Bean
    public ServerSecurityContextRepository securityContextRepository() {
        return new WebSessionServerSecurityContextRepository();
    }
}
