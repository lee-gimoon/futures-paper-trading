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
