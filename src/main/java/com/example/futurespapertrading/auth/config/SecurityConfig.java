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
import org.springframework.security.web.server.csrf.WebSessionServerCsrfTokenRepository;  // CSRF 토큰을 WebSession에 저장·조회하는 저장소

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
                // 세션 인증을 사용하므로 변경 요청은 WebSession에 저장된 CSRF 토큰으로 검증한다.
                // React SPA(Single Page Application)는 JSON API로 로그인하므로 Spring Security의 기본 폼 로그인·HTTP Basic·로그아웃 리다이렉트를 끈다.
                //
                // CSRF 활성화: WebSessionServerCsrfTokenRepository를 CSRF 토큰 저장소로 지정하면 Spring Security가 CsrfWebFilter를 필터 체인에 넣고,
                // 컨트롤러보다 먼저 실행해 현재 요청의 ServerWebExchange.attributes에 Mono<CsrfToken>을 자동으로 등록한다.
                // 등록되는 attribute의 키는 CsrfToken.class.getName()이고, 값은 Mono<CsrfToken> tokenMono다.
                // 우리가 exchange.getAttributes().put(...)을 직접 작성할 필요는 없다. 내부 동작을 개념적으로 쓰면 다음과 같다.
                // Mono<CsrfToken> tokenMono = ...; // 구독될 때 CSRF 토큰을 조회하거나 필요 시 생성·저장하는 객체
                // exchange.getAttributes().put(CsrfToken.class.getName(), tokenMono);
                // CsrfController는 이후 exchange.getAttribute(CsrfToken.class.getName())으로 같은 Mono를 꺼내 JSON으로 응답한다.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(new WebSessionServerCsrfTokenRepository())) // 실제 저장 공간은 WebSession이고, 이 객체는 그 저장 공간을 다루는 관리 객체다.
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
                // authorizeExchange = 현재 요청의 로그인(인증) 상태를 기준으로 경로별 접근 허용(permitAll) 또는 로그인 요구(authenticated)를 정하는 인가 규칙이다.
                .authorizeExchange(ex -> ex
                        // Swagger UI와 Swagger UI가 읽는 OpenAPI JSON/YAML 명세는 로그인 없이 볼 수 있게 공개한다.
                        .pathMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()
                        // Docker/배포 모드에서는 React 빌드 결과물을 Spring Boot가 직접 서빙한다.
                        .pathMatchers("/", "/index.html", "/assets/**", "/*.ico", "/*.png", "/*.svg").permitAll()
                        // 로그인 전에도 현재 익명 세션의 CSRF 토큰을 받아야 하므로 토큰 발급 API를 공개한다.
                        .pathMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
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
        //     .csrf(csrf -> ...)                  // CSRF를 켠 채 WebSession 토큰 저장소를 명시
        //     .formLogin(FormLoginSpec::disable)  // formLogin 필드 → null (끔)
        //     .securityContextRepository(obj)     // null → 우리 @Bean 객체로 교체
        //     .build();                           // 최종 SecurityWebFilterChain 생성
    }

    // 로그인 컨트롤러가 직접 호출하는 인증 매니저
    // (ReactiveUserDetailsService를 통해 email로 DB 사용자를 찾고, 입력 비밀번호를 DB의 BCrypt 해시와 비교해 인증한다)
    // @Bean → ReactiveAuthenticationManager 타입 빈으로 등록. 파라미터는 스프링이 주입한다.
    //
    // 반환 타입을 ReactiveAuthenticationManager 인터페이스로 선언하는 이유는,
    // 구현체를 교체해도 외부가 authenticate(Authentication)라는 공통 인증 기능에만 의존하도록 하기 위함이다.
    @Bean
    public ReactiveAuthenticationManager authenticationManager(
            ReactiveUserDetailsService userDetailsService, // 사용자 정보 조회
            PasswordEncoder passwordEncoder) {             // PasswordEncoderConfig가 만든 BCrypt 빈
        // "사용자 조회 → 입력 비밀번호와 저장된 해시 비교"를 대신 해주는 표준 구현체
        UserDetailsRepositoryReactiveAuthenticationManager manager =
                new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
        manager.setPasswordEncoder(passwordEncoder); // 비교에 쓸 인코더 지정 (안 하면 기본 인코더가 쓰여 BCrypt 검증이 안 맞음)
        return manager;
    }

    // WebSessionServerSecurityContextRepository 객체는 실제 보관함이 아니라,
    // 로그인 정보(SecurityContext)를 WebSession 보관함에 넣고 다음 요청 때 다시 꺼내는 담당자다.
    // 즉, Spring이 WebSession 보관함을 자동으로 만들고 관리하며,
    // WebSessionServerSecurityContextRepository는 그 보관함의 로그인 정보를 넣고 꺼내는 담당자다.
    //
    // 비유:
    // - 브라우저의 SESSION 쿠키 = "내 보관함 번호표"
    // - 서버의 WebSession      = 실제 보관함
    // - SecurityContext        = 보관함 안의 "누가 로그인했는지" 정보
    //
    // 브라우저는 SESSION 쿠키에 보관함 번호만 담아 보낸다.
    // 실제 이메일·권한 같은 로그인 정보는 서버의 WebSession에 저장한다.
    @Bean
    public ServerSecurityContextRepository securityContextRepository() {
        // WebSessionServerSecurityContextRepository 담당자를 만든다.
        // 이 담당자는 실제 서버 측 WebSession 보관함에 SecurityContext를 저장하고, 다음 요청 때 그 보관함에서 다시 읽는다.
        return new WebSessionServerSecurityContextRepository();
    }
}

// ════════════════════════════════════════════════════════════════════════════
// 빌더 패턴이란?
//   객체를 만들 때 .메서드().메서드()... 로 설정을 하나씩 쌓고, 마지막 .build()로 완성하는 패턴.
//   각 .메서드(...)는 Builder 객체의 필드에 값을 넣고 자신을 반환하는 setter 성격의 메서드다.
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
//   // 현재 CSRF 설정에서 호출하는 CsrfSpec.csrfTokenRepository(...)의 단순화된 구조:
//   class CsrfSpec {
//       private ServerCsrfTokenRepository csrfTokenRepository = new WebSessionServerCsrfTokenRepository();
//
//       public CsrfSpec csrfTokenRepository(ServerCsrfTokenRepository repository) {
//           this.csrfTokenRepository = repository; // CsrfWebFilter가 사용할 토큰 저장소를 명시한 객체로 교체
//           return this;
//       }
//   }
//   ※ csrf -> csrf.csrfTokenRepository(...)는 CsrfSpec 객체를 받으면 그 객체의 토큰 저장소를 설정하라는 람다다.
//      CSRF를 끄는 것이 아니라 CsrfSpec을 유지하므로 build()가 CsrfWebFilter를 필터 체인에 추가한다.
//
// 위 우리 코드(springSecurityFilterChain 빌더 체인)를 정확히 해석하면:
//   .csrf(csrf -> csrf.csrfTokenRepository(new WebSessionServerCsrfTokenRepository()))
//                                         → [A] csrf 객체에 WebSession 기반 토큰 저장소 설정 → CSRF 켬
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
//   - 대부분(csrf 등)은 Customizer<XxxSpec>를 받아 하위 객체를 '설정'한다 → csrf -> ... 람다는 값이 아니라 설정 함수다.
//   - securityContextRepository(...) 만 인자를 필드에 직접 대입한다.
// ════════════════════════════════════════════════════════════════════════════
