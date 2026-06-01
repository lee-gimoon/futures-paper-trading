package com.example.futurespapertrading.auth;

import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

// 로그인 인증 시 Spring Security가 호출. email로 유저를 찾아 UserDetails로 변환한다.
// 못 찾으면 빈 Mono → 인증 매니저가 "자격 증명 불일치"로 처리.
@Service
public class SecurityUserDetailsService implements ReactiveUserDetailsService {

    private final UserRepository userRepository;

    public SecurityUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Mono<UserDetails> findByUsername(String email) {
        return userRepository.findByEmail(email)
                .map(u -> org.springframework.security.core.userdetails.User
                        .withUsername(u.email())
                        .password(u.passwordHash()) // BCrypt 해시 그대로
                        .roles("USER")
                        .build());
    }
}
