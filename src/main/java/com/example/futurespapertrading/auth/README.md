# auth 폴더 — 회원 인증(Authentication) 모듈

이 폴더는 **"누가 우리 서비스의 사용자인가"를 책임지는 곳**입니다.
회원가입·로그인·로그아웃·내 정보 조회, 그리고 비밀번호 암호화와
세션(쿠키) 기반 로그인 유지까지 인증에 관련된 모든 것을 여기서 처리합니다.

이 폴더 덕분에 나머지 코드(시세·주문 등)는 인증을 신경 쓰지 않고
"이미 로그인된 사용자"라고 가정하고 일할 수 있습니다.

---

## 파일별 역할

| 파일 | 계층 | 역할 |
|---|---|---|
| `AuthController` | 입구(웹) | HTTP 요청을 받는 곳. `/signup` `/login` `/logout` `/me` 4개 엔드포인트 |
| `AuthService` | 비즈니스 로직 | 회원가입 규칙(이메일 중복 검사 + 비밀번호 해싱 후 저장) |
| `User` | 엔티티 | `users` 테이블 한 줄과 1:1 매핑되는 데이터 모델 |
| `UserRepository` | DB 접근 | DB에서 유저를 찾고/저장 (`findByEmail`) |
| `dto/` | 데이터 운반 | 요청/응답 전용 객체 (`SignupRequest`, `LoginRequest`, `UserResponse`) |
| `SecurityConfig` | 보안 설정 | "어떤 URL은 로그인 필수, 어떤 URL은 누구나" 같은 규칙 정의 |
| `SecurityUserDetailsService` | 보안 연결 | 로그인 시 Spring Security가 호출 — email로 유저를 찾아줌 |
| `PasswordEncoderConfig` | 보안 설정 | BCrypt 비밀번호 암호화기 등록 |
| `AuthExceptionHandler` | 예외 처리 | 이메일 중복 등 예외 → HTTP 409 응답으로 변환 |

---

## 두 개의 핵심 흐름

### ① 회원가입 (`POST /api/auth/signup`)

```
Controller → Service(이메일 중복 검사 → 비밀번호 BCrypt 해싱) → Repository(DB 저장)
```

이미 가입된 이메일이면 `AuthService`가 예외를 던지고
→ `AuthExceptionHandler`가 받아 HTTP 409로 변환합니다.

### ② 로그인 (`POST /api/auth/login`)

```
Controller → authenticationManager(인증) → SecurityUserDetailsService(유저 조회)
        → BCrypt로 비밀번호 비교 → 성공 시 SecurityContext를 세션에 저장(=쿠키 발급)
```

이후 사용자는 그 쿠키를 들고 다니고, `/api/auth/me` 같은 보호된 경로에
접근하면 `SecurityConfig`가 "쿠키 있나?"를 검사해 통과/차단합니다.

---

## 한 줄 요약

> **"가입시키고(signup), 로그인 확인하고(login), 비밀번호를 안전하게 보관하고(BCrypt),
> 로그인 상태를 쿠키로 유지(session)"** — 이 4가지를 묶은 게 `auth` 폴더입니다.

---

## 참고: 인증 없이 열려 있는 경로

`/api/binance-futures/**`(시세·호가)는 `SecurityConfig`에서
**로그인 없이도 접근 가능**하도록 열어둔 상태입니다.
시세·호가는 **공개 데이터**라서 로그인 뒤에 숨길 이유가 없고,
실제 거래소도 로그인 없이 차트·호가는 다 보여줍니다.
인증이 필요한 건 그다음 단계 — 주문·포지션·잔고처럼 "내 계정"에 관련된 것들입니다.

```java
.pathMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
.pathMatchers("/api/binance-futures/**").permitAll()
.anyExchange().authenticated()   // ← 나머지는 전부 로그인 필수
```

마지막 줄 `anyExchange().authenticated()` 덕분에 **앞으로 추가할 주문/포지션 API는
따로 설정 안 해도 자동으로 인증 필수**가 됩니다.
즉 "열어둔 것만 공개, 나머지는 잠금"이라 안전한 기본값입니다.

---

# 부록: 내 코드 vs 라이브러리 전체 목록

> 코드를 읽을 때 "이게 **내가 만든 것**인가, **스프링이 준 것**인가"가 헷갈릴 때 보는 표.
> **구분 비법 = `import` 접두사:**
> - `com.example.futurespapertrading.*` → **[내것]**
> - `org.springframework.*` / `jakarta.*` / `reactor.*` / `java.*` → **[라이브러리]**
>
> (IntelliJ에서 Ctrl+클릭 시 내 소스로 점프하면 내것, 디컴파일된 라이브러리로 가면 라이브러리)

## 1. 내가 만든 것 — 파일 11개

`AuthController`, `AuthService`, `SecurityConfig`, `SecurityUserDetailsService`,
`PasswordEncoderConfig`, `AuthExceptionHandler`, `User`, `UserRepository`,
`dto/SignupRequest`, `dto/LoginRequest`, `dto/UserResponse`

(+ 설정 파일 `application.yaml`, `schema.sql` 도 내가 작성)

## 2. 라이브러리 — import 해서 쓰는 것 (카테고리별)

### A. Spring 코어 / DI (빈 등록·설정)
| 이름 | 역할 | 쓰는 파일 |
|---|---|---|
| `@Configuration` | 설정 클래스 표시 | SecurityConfig, PasswordEncoderConfig |
| `@Bean` | 메서드 반환 객체를 빈으로 등록 | SecurityConfig, PasswordEncoderConfig |
| `@Service` | 서비스 계층 빈으로 등록 | AuthService, SecurityUserDetailsService |

### B. Spring Web (WebFlux) — HTTP 입출구
| 이름 | 역할 | 쓰는 파일 |
|---|---|---|
| `@RestController` | REST 컨트롤러 표시(반환값→JSON) | AuthController |
| `@RequestMapping` | 공통 경로(prefix) 지정 | AuthController |
| `@PostMapping` / `@GetMapping` | HTTP 메서드 매핑 | AuthController |
| `@RequestBody` | JSON 본문 → 자바 객체 | AuthController |
| `@ResponseStatus` | 성공 시 상태코드 지정 | AuthController |
| `@RestControllerAdvice` | 전역 예외 처리 | AuthExceptionHandler |
| `@ExceptionHandler` | 특정 예외 → 처리 메서드 매핑 | AuthExceptionHandler |
| `ResponseEntity` | 상태코드+본문+헤더 묶음 | AuthController, AuthExceptionHandler |
| `HttpStatus` | 상태코드 열거형(201/401/409…) | AuthController, AuthExceptionHandler, SecurityConfig |
| `HttpMethod` | HTTP 메서드 열거형 | SecurityConfig |
| `ServerWebExchange` | 요청/응답/세션 묶음 | AuthController |
| `WebSession` | 서버 세션 | AuthController |

### C. Spring Security — 인증/인가 (런타임)

> **인증(Authentication)** = "너 누구냐?" 신원 확인 — 로그인 시 이메일/비번이 맞는지 검증.
> **인가(Authorization)** = "너 이거 해도 되냐?" 권한 확인 — 그 경로/자원에 접근할 자격이 있는지.
> 순서는 **인증 먼저 → 인가 나중** (누군지 알아야 권한도 따진다). 아래 표는 대부분 *인증* 담당이고,
> *인가* 규칙 자체는 주로 `SecurityConfig`의 `authorizeExchange`(`anyExchange().authenticated()` 등)에 있다.

| 이름 | 역할 | 쓰는 파일 |
|---|---|---|
| `ReactiveAuthenticationManager` | 인증 수행 인터페이스 | AuthController, SecurityConfig |
| `UserDetailsRepositoryReactiveAuthenticationManager` | 위 구현체(유저 조회+비번 비교) | SecurityConfig |
| `UsernamePasswordAuthenticationToken` | "이 이메일+비번으로 로그인 시도" 토큰 | AuthController |
| `AuthenticationException` | 인증 실패 시 던져지는 예외 | AuthController |
| `ReactiveUserDetailsService` | 유저 조회 인터페이스 (**내가 구현**) | SecurityConfig, SecurityUserDetailsService |
| `UserDetails` | 인증용 "사용자" 표준 타입 | SecurityUserDetailsService |
| `User` (security 내장) | `UserDetails` 구현체/빌더 (내 엔티티와 동명이라 풀경로 사용) | SecurityUserDetailsService |
| `SecurityContext` / `SecurityContextImpl` | 인증 결과(누가 로그인했나) 보관 그릇/구현체 | AuthController |
| `ReactiveSecurityContextHolder` | 현재 로그인 정보를 꺼내는 통로 | AuthController |
| `ServerSecurityContextRepository` | 인증 결과를 세션에 저장/복원하는 인터페이스 | AuthController, SecurityConfig |
| `WebSessionServerSecurityContextRepository` | 위 구현체(WebSession=쿠키에 저장) | SecurityConfig |

### D. Spring Security — 설정/필터
| 이름 | 역할 | 쓰는 파일 |
|---|---|---|
| `@EnableWebFluxSecurity` | WebFlux용 시큐리티 활성화 스위치 | SecurityConfig |
| `ServerHttpSecurity` | 보안 필터체인을 쌓는 빌더 | SecurityConfig |
| `SecurityWebFilterChain` | 완성된 보안 필터 체인 | SecurityConfig |
| `HttpStatusServerEntryPoint` | 미인증 요청에 상태코드만 응답 | SecurityConfig |

### E. Spring Security — 비밀번호
| 이름 | 역할 | 쓰는 파일 |
|---|---|---|
| `PasswordEncoder` | 비밀번호 해시/검증 인터페이스 | PasswordEncoderConfig, AuthService, SecurityConfig |
| `BCryptPasswordEncoder` | BCrypt 해시 구현체 | PasswordEncoderConfig |

### F. Spring Data R2DBC — 영속성(DB)
| 이름 | 역할 | 쓰는 파일 |
|---|---|---|
| `ReactiveCrudRepository` | 리액티브 기본 CRUD 리포지토리 | UserRepository |
| `@Id` | PK(기본키) 필드 표시 | User |
| `@Table` | 클래스 ↔ DB 테이블 매핑 | User |
| `@Column` | 필드 ↔ DB 컬럼 매핑 | User |

### G. Jakarta Bean Validation — 입력 검증
| 이름 | 역할 | 쓰는 파일 |
|---|---|---|
| `@Valid` | 검증 실행 트리거 | AuthController |
| `@Email` | 이메일 형식 검증 | SignupRequest |
| `@NotBlank` | 빈 값(null/""/공백) 금지 | SignupRequest, LoginRequest |
| `@Size` | 길이 최소/최대 제한 | SignupRequest |

### H. Reactor — 리액티브
| 이름 | 역할 | 쓰는 파일 |
|---|---|---|
| `Mono` | 0~1개 결과를 비동기로 흘려보내는 상자 | 거의 전 파일 |

### I. JDK (자바 표준)
| 이름 | 역할 | 쓰는 파일 |
|---|---|---|
| `Map` | 응답 본문 `{"message": ...}` 자료구조 | AuthController, AuthExceptionHandler |
| `IllegalStateException` | 중복 이메일 등 에러 신호 | AuthService |

## 3. 숨은 라이브러리 — import 없이 뒤에서 동작 ★

코드에 안 보이지만 실제로 일하는 것들 (가장 놓치기 쉬움):

| 이름 | 역할 |
|---|---|
| **Jackson** | JSON ↔ 자바 객체 변환. `@RequestBody`/`@ResponseBody` 뒤에서 동작 (`spring-boot-starter-web`에 포함) |
| **Spring Data 구현 엔진** | `UserRepository` 인터페이스의 실제 구현체를 부팅 시 자동 생성, `@Table`/`@Column` 읽어 SQL 생성·매핑 |
| **r2dbc-postgresql** | 실제 PostgreSQL과 통신하는 드라이버 |
| **Reactor Netty** | WebFlux의 웹서버(요청을 받아들이는 곳) |

## 4. 경계 부품(이음새) — 가장 헷갈리는 3곳

내 코드와 라이브러리가 손을 맞잡는 지점. 여기만 정확히 알면 나머지는 배선일 뿐:

1. **`UserRepository`** — 내가 **인터페이스만 선언**, 구현체는 **Spring Data가 자동 생성**.
2. **`SecurityUserDetailsService`** — 내가 **라이브러리 인터페이스(`ReactiveUserDetailsService`)를 구현** → 로그인 때 **프레임워크가 내 `findByUsername()`을 호출**.
3. **`SecurityConfig`의 `@Bean`들** — 내가 **라이브러리 클래스(`UserDetailsRepositoryReactiveAuthenticationManager` 등)를 `new`해서 조립**.

> 핵심: 내가 `implements`한 인터페이스는 "내가 부르는 것"이 아니라 **"프레임워크가 불러주는 것"**이다.
> (제어의 역전, IoC — "Don't call us, we'll call you")
>
> **왜 부르나?** 로그인 같은 공통 절차는 프레임워크가 다 갖고 있고, "사용자를 어디서 불러오나"처럼
> *앱마다 다른 한 조각*만 인터페이스로 비워 나에게 맡긴다 → 그 차례가 되면 내 구현(`findByUsername`)을 호출한다.
> (그 조각이 없으면 비번 비교에 쓸 "저장된 해시"를 못 가져와서 로그인 절차를 완성할 수 없기 때문)
