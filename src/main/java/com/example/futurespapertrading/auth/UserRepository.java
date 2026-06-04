package com.example.futurespapertrading.auth; // 이 파일이 속한 패키지(폴더) 경로

import org.springframework.data.repository.reactive.ReactiveCrudRepository; // 리액티브용 기본 CRUD(Create/Read/Update/Delete) 리포지토리
import reactor.core.publisher.Mono;                                         // 0~1개의 결과를 "나중에" 비동기로 흘려보내는 리액티브 상자

// ── 리포지토리(Repository) 인터페이스 ──
//  - Repository = 영어로 "저장소, 보관소"라는 뜻. (도서관·창고·GitHub repository 같은 그 단어)
//    DB라는 거대한 창고에서 데이터를 꺼내다 주는 "사서" 역할이라 이렇게 이름 붙였다.
//    (DDD라는 설계 방법론에서 정해진 용어 → 스프링이 그대로 채택)
//  - "DB 접근 계층". 서비스가 SQL을 직접 쓰지 않고 이 인터페이스의 메서드를 호출해 DB와 대화한다.
//  - interface인데 구현 클래스를 우리가 만들지 않는다.
//    스프링 데이터(Spring Data)가 시작 시점에 자동으로 구현체를 만들어 빈으로 등록해준다. (프록시 객체)
//  - R2DBC = "Reactive Relational DB Connectivity". JDBC의 리액티브(비동기) 버전. 결과를 Mono/Flux로 받는다.
//
// extends ReactiveCrudRepository<User, Long>
//  - 제네릭 <User, Long> = "이 리포지토리가 다루는 엔티티는 User, 그 엔티티의 ID 타입은 Long"
//  - 상속받는 것만으로 아래 기본 메서드들이 자동 제공된다:
//      save(user), findById(id), findAll(), deleteById(id), count(), existsById(id) ...
//    → 그래서 인터페이스 본문이 거의 비어 있어도 충분히 동작한다.
public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    // ── 커스텀 조회 메서드 ──
    //  - 메서드 이름이 곧 쿼리(Query Derivation): "findBy" + 필드명 + ...
    //    → 스프링 데이터가 이름을 파싱해 SQL을 자동 생성한다.
    //    예) findByEmail(String email)  →  SELECT * FROM users WHERE email = ?
    //  - 반환 Mono<User> = "나중에 User 0~1개가 흘러올 예약".
    //    → 일치하는 유저가 있으면 1개 흘려보내고, 없으면 빈 Mono를 흘려보낸다.
    //      (AuthService.signup의 switchIfEmpty 분기가 이 "빈 Mono"를 이용해 신규 가입을 처리)
    Mono<User> findByEmail(String email);

    // ── 참고: 우리가 이 파일에서 안 쓴 기본 메서드들 ──
    //
    //   userRepository.save(user)        → INSERT 또는 UPDATE (id가 null이면 INSERT)
    //   userRepository.findById(1L)      → SELECT * FROM users WHERE id = 1
    //   userRepository.deleteById(1L)    → DELETE FROM users WHERE id = 1
    //   userRepository.findAll()         → SELECT * FROM users (Flux<User>로 여러 개 흘려보냄)
    //
    //   AuthService.signup이 마지막에 호출하는 userRepository.save(user)가 바로 위 save가 작동한 것.

    // ── 참고: ReactiveCrudRepository vs 전통 Spring MVC(Tomcat)의 리포지토리 ──
    //
    //   같은 "리포지토리"지만 동작 방식이 완전히 다르다. 핵심은 "기다리느냐 vs 다른 일 하느냐".
    //
    //   ┌───────────────────┬───────────────────────────────┬──────────────────────────────┐
    //   │ 항목               │ 전통 Spring MVC (Tomcat)      │ Spring WebFlux (이 프로젝트)   │
    //   ├───────────────────┼───────────────────────────────┼──────────────────────────────┤
    //   │ 베이스 인터페이스    │ CrudRepository, JpaRepository │ ReactiveCrudRepository       │
    //   │ DB 드라이버        │ JDBC (블로킹)                  │ R2DBC (논블로킹)               │
    //   │ 반환 타입          │ User, Optional<User>, List    │ Mono<User>, Flux<User>        │
    //   │ 호출 방식          │ 동기 — 결과 올 때까지 멈춤        │ 비동기 — 구독해야 실행           │
    //   │ 웹 서버            │ Tomcat (요청당 스레드 1개)       │ Netty (적은 스레드로 다수)      │
    //   └───────────────────┴───────────────────────────────┴──────────────────────────────┘
    //
    //   ── 코드로 비교 ──
    //
    //   [전통 Spring MVC]
    //     Optional<User> findByEmail(String email);
    //     User u = userRepository.findByEmail(email).orElseThrow(); // 결과 올 때까지 스레드 멈춤
    //
    //   [Spring WebFlux — 이 파일]
    //     Mono<User> findByEmail(String email);
    //     userRepository.findByEmail(email).map(u -> ...);          // 결과 오면 그때 실행, 스레드 안 멈춤
    //
    //   ── 왜 비동기가 좋은가? ──
    //   전통 Tomcat은 "요청 1개 = 스레드 1개" 점유. DB 응답을 기다리는 동안 그 스레드는 놀고 있다.
    //   → 동시 요청이 많아지면 스레드 풀이 금방 바닥난다.
    //   WebFlux는 "기다리지 말고 다른 요청 처리해" 모델이라 적은 스레드로 더 많은 요청을 받을 수 있다.
    //
    //   ── 대신 단점 ──
    //   코드가 어렵다(Mono/Flux + flatMap/switchIfEmpty 체이닝). AuthService.signup의 그 복잡한 흐름이
    //   바로 그 비용이다. 학습 단계에서는 "왜 이렇게 쓰지?"가 헷갈리지만, 익숙해지면 대용량 처리에 강하다.
}
