package com.example.futurespapertrading.paper.repository;
import com.example.futurespapertrading.paper.domain.PaperAccount;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

// paper_accounts DB 접근 계층. (UserRepository와 같은 구조 — 인터페이스만 선언하면 Spring Data가 구현체를 자동 생성)
public interface PaperAccountRepository extends ReactiveCrudRepository<PaperAccount, Long> {

    // findByUserId → SELECT * FROM paper_accounts WHERE user_id = ?
    // 한 사용자의 계좌를 찾는다. 없으면 빈 Mono → PortfolioService가 시드 계좌를 만들어 저장한다.
    Mono<PaperAccount> findByUserId(Long userId);
}
