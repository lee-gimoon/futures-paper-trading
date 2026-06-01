package com.example.futurespapertrading.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
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
