package com.example.futurespapertrading.auth.service; // 이 파일이 속한 패키지(폴더) 경로
import com.example.futurespapertrading.auth.domain.User;
import com.example.futurespapertrading.auth.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder; // 비밀번호를 단방향 해시(BCrypt)로 만들어주는 도구
import org.springframework.stereotype.Service;                       // 이 클래스가 "비즈니스 로직 계층"의 스프링 빈임을 표시
import reactor.core.publisher.Mono;                                  // 0~1개의 결과를 "나중에" 비동기로 흘려보내는 리액티브 상자

// @Service = 스프링이 이 클래스를 빈(Bean)으로 등록한다.
//  - 컨트롤러는 HTTP 입출구만 담당하고, 진짜 "규칙(로직)"은 이 서비스 계층에 모은다.
//  - 여기 목적: 회원가입 규칙(이메일 중복 검사 + 비밀번호 해싱 + DB 저장)을 한곳에 모음.
@Service
public class AuthService { // 회원가입 비즈니스 로직을 담은 서비스

    // ── 의존성 주입(DI): 아래 2개는 필드 선언(빈 그릇). 스프링이 생성자를 통해 실제 객체를 채워준다 ──
    private final UserRepository userRepository;   // DB에서 유저를 조회/저장 (이메일로 찾기, 새 유저 저장)
    private final PasswordEncoder passwordEncoder; // 비밀번호를 BCrypt 해시로 변환 (원본은 절대 저장 안 함)

    // 생성자 주입 = 스프링이 미리 만들어둔 빈들을 이 생성자를 호출할 때 인자로 넘겨준다.
    // 개발자: "생성자 작성 + this.xxx = xxx 대입 코드 작성"
    // 스프링: "이미 만들어둔 빈(UserRepository, PasswordEncoder)을 타입에 맞춰 인자로 전달"
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ── 회원가입 로직 ──
    // 규칙: 이메일이 이미 있으면 에러, 없으면 BCrypt로 해시한 비번을 DB에 저장.
    // 반환 Mono<User> = "나중에 User 한 개가 흘러올 예약". 구독되는 순간 아래 흐름이 실제로 실행된다.
    public Mono<User> signup(String email, String rawPassword, String displayName) {
        return userRepository.findByEmail(email)
                // ── 케이스 ①: 이메일이 이미 DB에 있으면(=중복) ──
                //   flatMap = flat(평탄화/납작하게 펴기) + map(매핑/짝지어 변환)
                //     · map만 쓰면 람다가 반환한 Mono가 한 겹 더 쌓여 Mono<Mono<User>>가 됨(중첩).
                //     · flatMap은 그 안쪽 Mono를 "납작하게(flat) 펴서" 흐름에 그대로 이어준다.
                //   findByEmail이 User 한 개를 흘려보냄 → flatMap이 받아서 즉시 에러 Mono로 바꿈.
                //   Mono.<User>error(...) = "정상 결과 대신 예외를 흘려보내는 Mono"
                //   여기서 던진 IllegalStateException은 → AuthExceptionHandler가 잡아 HTTP 409로 변환한다.
                .flatMap(existing -> Mono.<User>error(
                        new IllegalStateException("이미 가입된 이메일입니다.")))
                // ── 케이스 ②: 이메일이 DB에 없으면(=신규) ──
                //   findByEmail의 반환 타입은 여전히 Mono<User>지만,
                //   값(User)을 안 흘리고 onComplete 시그널만 흘리는 "빈 Mono" 상태가 된다.
                //   switchIfEmpty = 원본 Mono가 비어 있으면 대체 Mono를 사용한다.
                //     · Mono.empty().switchIfEmpty(Mono.just("fallback")) → 결과는 "fallback"
                //     · Mono.just("data").switchIfEmpty(Mono.just("fallback")) → 결과는 "data"
                //   여기서는 findByEmail(email)이 비어 있을 때만 신규 가입용 Mono로 넘어간다.
                //
                //   Mono.defer(() -> { ... }) = 안쪽 코드를 지금 실행하지 않고, 나중에 필요할 때 실행한다.
                //     · switchIfEmpty가 실제로 대체 Mono를 사용해야 할 때만 아래 블록이 실행된다.
                //     · 그래서 이메일이 이미 존재하는 경우에는 User 생성과 BCrypt 해싱을 하지 않는다.
                //
                //   만약 Mono.defer 없이 아래처럼 쓰면:
                //     · .switchIfEmpty(userRepository.save(new User(
                //           null, email, passwordEncoder.encode(rawPassword), displayName)))
                //   문제: switchIfEmpty가 fallback을 쓸지 결정하기 전에, 자바가 메서드 인자를 먼저 만든다.
                //     · 그래서 이메일이 이미 존재해도 passwordEncoder.encode(rawPassword)가 실행될 수 있다.
                //     · BCrypt 해싱은 무거운 작업이라, 신규 가입이 아닌 경우에는 불필요한 낭비가 된다.
                .switchIfEmpty(Mono.defer(() -> {
                    User user = new User(
                            null,                              // id = null → DB가 AUTO_INCREMENT로 자동 부여
                            email,
                            passwordEncoder.encode(rawPassword), // 원본 비번 → BCrypt 해시로 변환 (단방향, 복호화 불가)
                            displayName);
                    return userRepository.save(user);          // DB INSERT → 저장된 User(id 채워진 상태)를 흘려보냄
                }));
        // 최종 결과:
        //   - 중복이면      → IllegalStateException 흘러나감 → 컨트롤러 거쳐 AuthExceptionHandler가 409로 변환
        //   - 신규면        → 저장된 User 흘러나감          → 컨트롤러가 UserResponse로 변환해 201로 응답
    }

    // ── 참고: 왜 raw password를 DB에 저장하지 않는가 ──
    //
    //   BCrypt 해시는 "단방향"이라 해시값에서 원본 비번을 되돌릴 수 없다.
    //   → DB가 털려도 공격자는 비번 원문을 알아낼 수 없음.
    //   로그인 시에는 "사용자가 입력한 비번 + DB에 저장된 해시"를 BCrypt가 다시 해싱·비교해서 일치 여부만 확인한다.
    //   (이 비교는 SecurityUserDetailsService + ReactiveAuthenticationManager가 자동 처리)
}
