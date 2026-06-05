package com.example.futurespapertrading.auth; // 이 파일이 속한 패키지(폴더) 경로

import org.springframework.data.annotation.Id;                   // PK(기본키) 필드임을 표시하는 애너테이션
import org.springframework.data.relational.core.mapping.Column;  // 필드를 특정 DB 컬럼명에 매핑하는 애너테이션
import org.springframework.data.relational.core.mapping.Table;   // 이 클래스를 특정 DB 테이블에 매핑하는 애너테이션

// ── 이 클래스가 "엔티티(Entity)"인 근거 (무엇을 보고 DB와 연결된다고 판단하나) ──
//  - @Table("users")가 붙음 → "이 클래스를 users 테이블에 매핑하라"고 직접 선언한 것.
//  - @Id 필드가 있음 → 각 행을 식별하는 기본키를 가진다 (DB 테이블의 PK처럼).
//  - UserRepository가 ReactiveCrudRepository<User, Long>의 도메인 타입으로 사용 → 스프링 데이터가 이 타입을 엔티티로 다룬다.
//  → 종합하면: 엔티티 = DB 테이블의 한 줄(row)을 자바 객체로 표현한 것. (이 객체 1개 = users 테이블 1행)
//
//  - record = 자바의 "불변 데이터 운반용" 클래스.
//      · 생성자, 접근자, equals/hashCode/toString을 컴파일러가 자동 생성해준다.
//      · 필드 읽기는 getEmail()이 아니라 email() 형태 → SecurityUserDetailsService의 u.email()/u.passwordHash()가 이것.
//  - created_at/updated_at은 DB가 DEFAULT now()로 채우므로 여기 필드로 두지 않는다.
//
// @Table("users")가 트리거가 되어, 부팅 시 Spring Data R2DBC의 매핑 엔진(R2dbcMappingContext)이
// 이 클래스를 스캔하고 @Table/@Column/@Id를 읽어 "매핑 모델"을 메모리에 구축한다:
//     User 클래스       ↔ users 테이블
//     passwordHash 필드 ↔ password_hash 컬럼
//     id 필드           ↔ @Id (PK)
@Table("users") // 이 record ↔ DB의 users 테이블 연결
public record User(
        @Id Long id, // PK(기본키). 저장 시 null이면 INSERT(새 행), 값이 있으면 그 행 UPDATE로 처리. INSERT 후 DB가 만든 id가 이 필드에 채워진다.
        String email, // 필드명과 컬럼명이 같으면(email) 매핑 애너테이션 생략 가능
        @Column("password_hash") String passwordHash, // 자바는 카멜(passwordHash) ↔ DB는 스네이크(password_hash). 이름이 달라 @Column으로 직접 매핑
        @Column("display_name") String displayName // 위와 동일 (displayName ↔ display_name)
) {
}

// ════════════════════════════════════════════════════════════════════════════
// 이 User 엔티티를 "왜" 만들었나 (목적)
//
//  ── 근거: 실제로 어디에 쓰이나 (두 방향) ──
//   ① 저장 방향 — AuthService.signup: new User(null, email, 해시, 이름) → userRepository.save(user)
//                 → 자바에서 만든 사용자 데이터를 DB에 INSERT 할 때의 "그릇".
//   ② 조회 방향 — UserRepository.findByEmail: DB의 한 행을 User 객체로 받음
//                 → SecurityUserDetailsService가 u.email()/u.passwordHash()를 꺼내 로그인 인증에 사용.
//   즉 "사용자 한 명" 데이터를 DB ↔ 자바 사이로 실어 나르는 통로가 이 엔티티다.
//
//  ── 왜 굳이 클래스로? (엔티티가 없다면) ──
//   엔티티 없이 DB 결과를 다루면: String hash = (String) row.get("password_hash"); // 문자열 키+캐스팅, 오타가 런타임에야 터짐
//   엔티티가 있으면:              String hash = user.passwordHash();               // 컴파일러가 검증, 타입 안전, 자동완성
//   → DB 테이블의 한 행을 "타입 있는 자바 객체"로 안전하게 다루기 위함.
//
//  ── 핵심: 엔티티는 'DB 저장/조회 전용'이라 외부(HTTP)와 직접 주고받지 않는다 ──
//   비밀번호 해시(passwordHash)까지 들고 있어서(저장에 필요) 그대로 응답에 노출하면 안 된다.
//   그래서 외부와 주고받는 건 별도 DTO를 쓴다 — 역할 분리:
//       User          = DB ↔ 자바    (passwordHash 포함, 내부 저장용)
//       SignupRequest = 외부 → 서버   (회원가입 입력)
//       UserResponse  = 서버 → 외부   (id/email/displayName만, 비밀번호 제외)
//   예) AuthController.signup: 저장된 User → new UserResponse(u.id(), u.email(), u.displayName())로 변환해 응답.
//
//  ── 결론 ──
//   User 엔티티 = users 테이블과 1:1 매핑되어, 사용자 데이터를 DB에 저장하고 꺼내올 때 쓰는
//                "영속성 전용 그릇". 외부 노출용이 아니다(그건 DTO 몫).
//
//  ── 용어: 위에 나온 "영속성(Persistence)"이란? ──
//   영속 = 영원히 지속됨. 데이터가 프로그램을 꺼도 사라지지 않고 계속 남는 성질.
//       · 그냥 자바 변수/객체 → 메모리(RAM)에 있음 → 프로그램 끄면 사라짐 (휘발성)
//       · DB에 저장한 데이터  → 디스크(DB)에 있음  → 프로그램 꺼도 남아있음 (영속성)
//     → 회원가입한 유저가 서버를 껐다 켜도 그대로 남아있는 이유가 이것.
//   스프링에서 이 DB 저장/조회를 담당하는 부분을 "영속성 계층(Persistence Layer)"이라 부른다.
//   spring-data, Repository, Entity가 다 여기 속한다. (자주 나오는 정식 용어라 알아두면 좋다)
// ════════════════════════════════════════════════════════════════════════════
