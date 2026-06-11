package com.example.futurespapertrading.auth.config; // 이 파일이 속한 패키지(폴더) 경로

import org.springframework.context.annotation.Bean;  // 메서드가 만든 객체를 스프링 빈으로 등록하는 애너테이션
import org.springframework.context.annotation.Configuration;  // 이 클래스가 "설정 클래스"임을 표시하는 애너테이션
import org.springframework.http.HttpMethod;  // GET/POST 같은 HTTP 메서드 종류(열거형)
import org.springframework.http.HttpStatus;  // 200/401/403 같은 HTTP 상태 코드 모음(열거형)
import org.springframework.security.authentication.ReactiveAuthenticationManager;  // 인증을 수행하는 핵심 인터페이스(리액티브)
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;  // 위 인터페이스 구현체: 사용자 조회 + 비밀번호 비교
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;  // WebFlux(리액티브)용 스프링 시큐리티 활성화 애너테이션
import org.springframework.security.config.web.server.ServerHttpSecurity;  // 보안 필터체인을 쌓아 만드는 빌더(WebFlux용)
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;  // 사용자 정보를 DB 등에서 불러오는 인터페이스(리액티브)
import org.springframework.security.crypto.password.PasswordEncoder;  // 비밀번호 해시/검증용 인터페이스
import org.springframework.security.web.server.SecurityWebFilterChain;  // 완성된 보안 필터 체인(빌더의 최종 결과물)
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;  // 미인증 시 상태코드만 내려주는 진입점 구현체
import org.springframework.security.web.server.context.ServerSecurityContextRepository;  // 인증 정보를 저장/복원하는 "방식" 인터페이스
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;  // 위 인터페이스 구현체: WebSession(쿠키)에 저장

// @Configuration = 객체(빈) 만드는 법을 적어둔 설정 클래스. (자세한 설명은 PasswordEncoderConfig 참고)
//
// @EnableWebFluxSecurity = WebFlux(리액티브) 앱에 스프링 시큐리티를 켜는 스위치.
//  - 이게 있어야 보안 필터체인이 동작하고, 아래 SecurityWebFilterChain @Bean으로 인증/인가 규칙을 직접 정할 수 있다.
//  - Servlet(MVC)용 @EnableWebSecurity의 WebFlux 버전이다. (이 프로젝트는 리액티브 스택이라 이걸 쓴다)
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig { // 스프링 시큐리티 설정(인증/인가 규칙·인증 매니저 등)을 모아둔 클래스

    // @Bean → 아래가 만든 SecurityWebFilterChain(완성된 보안 필터 체인)을 컨테이너에 등록.
    //  - 들어오는 모든 요청이 이 필터 체인을 통과한다 → "어떤 경로는 열고, 어떤 경로는 로그인 필요" 규칙을 여기서 정의.
    //  - 파라미터는 스프링이 자동 주입: http = 설정을 쌓는 빌더, securityContextRepository = 아래 @Bean(인증정보 저장 방식)
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
                        // .authenticated()는 "인증을 수행하는" 게 아니라, "이 경로에 들어오려면 인증된 상태여야 한다"는 접근 조건(출입 규칙)이에요.
                        .anyExchange().authenticated())
                .build();
        // Spring이 내부적으로 이렇게 만들어 놓음 (기본값 포함)
        // class ServerHttpSecurity {
        //     CsrfSpec csrf          = new CsrfSpec();        // 기본: CSRF 켜져 있음
        //     FormLoginSpec formLogin = new FormLoginSpec();   // 기본: 폼로그인 켜져 있음
        //     HttpBasicSpec httpBasic = new HttpBasicSpec();   // 기본: HTTP Basic 켜져 있음
        //     ServerSecurityContextRepository securityContextRepository = null; // 기본: null
        // }
        //
        // 우리가 하는 일 = 기본값 중 필요한 것만 세터로 덮어씀
        // http
        //     .csrf(CsrfSpec::disable)           // csrf 필드 → null (끔)
        //     .formLogin(FormLoginSpec::disable)  // formLogin 필드 → null (끔)
        //     .securityContextRepository(obj)     // null → 우리 @Bean 객체로 교체
        //     .build();                           // 최종 SecurityWebFilterChain 생성
    }

    // 로그인 컨트롤러가 직접 호출하는 인증 매니저 (UserDetailsService로 사용자 조회 + BCrypt로 비밀번호 비교)
    // @Bean → ReactiveAuthenticationManager 타입 빈으로 등록. 파라미터는 스프링이 주입한다.
    //  - userDetailsService = 사용자 정보 조회, passwordEncoder = PasswordEncoderConfig가 만든 그 BCrypt 빈
    @Bean
    public ReactiveAuthenticationManager authenticationManager(
            ReactiveUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        // "사용자 조회 → 입력 비밀번호와 저장된 해시 비교"를 대신 해주는 표준 구현체
        UserDetailsRepositoryReactiveAuthenticationManager manager =
                new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
        manager.setPasswordEncoder(passwordEncoder); // 비교에 쓸 인코더 지정 (안 하면 기본 인코더가 쓰여 BCrypt 검증이 안 맞음)
        return manager;
    }

    // 인증 성공 후 SecurityContext(누가 로그인했는지 정보)를 어디에 저장/복원할지 정하는 빈
    // @Bean → ServerSecurityContextRepository 타입으로 등록 → 위 필터체인의 .securityContextRepository(...)로 주입된다.
    @Bean
    public ServerSecurityContextRepository securityContextRepository() {
        // WebSession에 저장 → 브라우저엔 SESSION 쿠키만 오가고, 실제 인증정보는 서버 세션에 보관한다
        return new WebSessionServerSecurityContextRepository();
    }
}

// ════════════════════════════════════════════════════════════════════════════
// 빌더 패턴이란?
//   객체를 만들 때 .메서드().메서드()... 로 설정을 하나씩 쌓고, 마지막 .build()로 완성하는 패턴.
//
//   ★ 핵심: 빌더 패턴은 클래스가 '두 개'다.
//       (1) 빌더 클래스         = 설정을 모으기만 하는 임시 객체 (체이닝 세터 메서드들이 여기 들어있음)
//       (2) 진짜(결과물) 클래스 = .build()가 만들어서 돌려주는, 우리가 실제로 쓸 객체
//     예) PersonBuilder        --.build()-->  Person
//         ServerHttpSecurity   --.build()-->  SecurityWebFilterChain   (← 우리 코드가 이 경우)
//
//   ▸ 메서드(.name(...), .age(...) 같은 것) = "(1)빌더 자신의 필드 하나를 채우는 세터" 단계다.
//       - 보통 메서드 이름 = 설정하려는 필드 이름.
//       - 반환값으로 this(빌더 자신)를 돌려줘서 다음 .메서드()를 또 이어붙일 수 있다(체이닝).
//   ▸ () 안에 들어가는 것 = 그 필드에 넣을 '값(인자)'.
//   ▸ .build() = (1)빌더에 쌓아둔 필드값들을 모아 (2)진짜 객체를 생성해서 반환한다.
//
//   표준 예시 (Spring과 무관한 일반 빌더):
//     class PersonBuilder {
//         private String name;                                                       // 채워질 필드
//         private int    age;
//         public PersonBuilder name(String name) { this.name = name; return this; }  // 메서드=세터, 인자=값
//         public PersonBuilder age(int age)       { this.age  = age;  return this; }
//         public Person build() {
//             return new Person(name, age);   // ★ 빌더의 필드(name, age)를 '읽어서' Person 생성자에 넘김
//         }                                   //   → 빌더와는 별개인 '새 Person 객체'가 만들어진다
//     }
//     Person p = new PersonBuilder()
//             .name("Tom")   // .name(...) = 메서드,  "Tom" = () 안의 값   →   (빌더의) name 필드 = "Tom"
//             .age(20)       //                                              (빌더의) age  필드 = 20
//             .build();      // 빌더 필드값을 모아 new Person("Tom", 20) 실행 → 그 Person 을 p 에 반환
//
//   ※ build() 메커니즘 (가장 헷갈리는 부분!):
//     - 빌더(PersonBuilder)와 결과물(Person)은 메모리에 '따로' 존재하는 별개의 객체다.
//     - build() 는 빌더를 Person 으로 '변신'시키는 게 아니다.
//       빌더의 필드값을 '읽어서' Person 생성자 인자로 복사해 넘겨 '새 Person'을 만들 뿐이다.
//     - build() 후 빌더는 보통 버려지고(GC 대상), 반환된 Person 만 남아서 쓰인다.
//
//         PersonBuilder{name="Tom", age=20}   --값 읽음-->   new Person("Tom", 20)
//            (빌더 = 재료 보관함, 별개 객체)                    (결과물 = 실제 사용, 별개 객체)
//
//     ※ ServerHttpSecurity 도 똑같다: http(빌더) 의 필드들을 모아
//       .build() 가 '별개의' SecurityWebFilterChain 객체를 새로 만들어 반환한다.
//
// 실제 ServerHttpSecurity(Spring Security 7.0.5)를 단순화하면 이런 구조다 (javap로 확인한 실제 시그니처):
//
//   public class ServerHttpSecurity {
//       // ── 필드: 설정 결과가 여기 쌓인다 ───────────────────────────────
//       private CsrfSpec               csrf;                       // 하위 '설정 객체'를 담는 필드
//       private HttpBasicSpec          httpBasic;
//       private FormLoginSpec          formLogin;
//       private LogoutSpec             logout;
//       private ExceptionHandlingSpec  exceptionHandling;
//       private AuthorizeExchangeSpec  authorizeExchange;
//       private ServerSecurityContextRepository securityContextRepository;  // '값'을 직접 담는 필드
//
//       // ── 세터 메서드는 두 종류다! (사용자가 헷갈린 부분) ──────────────
//
//       // [종류 A] Customizer 방식 — 인자는 '값'이 아니라 "하위 객체를 설정하는 함수"
//       //   Customizer<T> = 함수형 인터페이스 { void customize(T t); }
//       public ServerHttpSecurity csrf(Customizer<CsrfSpec> customizer) {
//           customizer.customize(this.csrf);  // csrf 객체에 설정을 '적용'할 뿐, 대입(=)이 아니다
//           return this;
//       }
//       // httpBasic / formLogin / logout / exceptionHandling / authorizeExchange 도 전부 이 방식
//
//       // [종류 B] 직접 대입 방식 — 인자가 곧 필드값
//       public ServerHttpSecurity securityContextRepository(ServerSecurityContextRepository repo) {
//           this.securityContextRepository = repo;  // ← 이것만 진짜 'field = value'
//           return this;
//       }
//
//       public SecurityWebFilterChain build() { /* 필드들을 모아 필터체인 생성 */ }
//   }
//
//   // CsrfSpec.disable()의 실제 동작(바이트코드로 확인):
//   class CsrfSpec {
//       public ServerHttpSecurity disable() {
//           ServerHttpSecurity.this.csrf = null;   // csrf 필드를 null로 → CSRF 필터를 안 만든다
//           return ServerHttpSecurity.this;
//       }
//   }
//   ※ CsrfSpec::disable 은 메서드 참조(method reference) 문법이다. (Java 8)
//       :: = "이 클래스의 이 메서드를 함수로 넘긴다"는 의미("CsrfSpec의 disable 메서드를 함수로 넘긴다")
//       CsrfSpec::disable  ==  spec -> spec.disable()   (람다와 완전히 동일, 짧게 쓴 것)
//       → 값이 아니라 "나중에 CsrfSpec 객체를 받으면 .disable()을 호출해줘" 라는 동작(함수)을 넘기는 것
//
// 위 우리 코드(springSecurityFilterChain 빌더 체인)를 정확히 해석하면:
//   .csrf(CsrfSpec::disable)              → [A] csrf 객체에 disable() 적용 → csrf 필드를 null로 (CSRF 끔)
//   .formLogin(FormLoginSpec::disable)    → [A] formLogin 필드를 null로 (폼로그인 끔)
//   .httpBasic(HttpBasicSpec::disable)    → [A] httpBasic 필드를 null로
//   .logout(LogoutSpec::disable)          → [A] logout 필드를 null로
//   .exceptionHandling(ex -> ...)         → [A] exceptionHandling 객체에 entryPoint 설정 적용
//   .securityContextRepository(securityContextRepository)
//                                         → [B] securityContextRepository 필드 = 인자(이 @Bean이 만든 객체)
//   .authorizeExchange(ex -> ...)         → [A] authorizeExchange 객체에 경로별 인가 규칙 적용
//   .build()                              → 쌓인 필드들을 모아 SecurityWebFilterChain 생성
//
// 핵심: 빌더 메서드가 전부 'field = value'는 아니다.
//   - 대부분(csrf 등)은 Customizer<XxxSpec>를 받아 하위 객체를 '설정'한다 → CsrfSpec::disable 은 값이 아니라 함수!
//   - securityContextRepository(...) 만 인자를 필드에 직접 대입한다.
// ════════════════════════════════════════════════════════════════════════════