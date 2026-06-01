package com.example.futurespapertrading.auth;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

// R2DBC 리액티브 리포지토리. 쿼리는 메서드 이름에서 파생된다.
public interface UserRepository extends ReactiveCrudRepository<User, Long> {
    Mono<User> findByEmail(String email);
}
