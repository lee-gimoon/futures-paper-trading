package com.example.futurespapertrading.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 이메일이 이미 있으면 에러, 없으면 BCrypt 해시 후 저장.
    public Mono<User> signup(String email, String rawPassword, String displayName) {
        return userRepository.findByEmail(email)
                .flatMap(existing -> Mono.<User>error(
                        new IllegalStateException("이미 가입된 이메일입니다.")))
                .switchIfEmpty(Mono.defer(() -> {
                    User user = new User(
                            null,
                            email,
                            passwordEncoder.encode(rawPassword),
                            displayName);
                    return userRepository.save(user);
                }));
    }
}
