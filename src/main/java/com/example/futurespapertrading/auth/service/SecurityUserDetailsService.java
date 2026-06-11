package com.example.futurespapertrading.auth.service; // 이 파일이 속한 패키지(폴더) 경로
import com.example.futurespapertrading.auth.domain.User;
import com.example.futurespapertrading.auth.repository.UserRepository;

import org.springframework.security.core.userdetails.ReactiveUserDetailsService;  // 로그인 시 사용자 정보를 조회하는 인터페이스(리액티브). 이 클래스가 직접 구현한다.
import org.springframework.security.core.userdetails.UserDetails;                 // 스프링 시큐리티가 인증에 쓰는 "사용자" 표준 타입(아이디·비밀번호·권한)
import org.springframework.stereotype.Service;                                    // 이 클래스를 서비스 계층 빈으로 등록하는 애너테이션
import reactor.core.publisher.Mono;                                               // 0~1개의 결과를 "나중에" 비동기로 흘려보내는 리액티브 상자

// 로그인 인증 시 Spring Security가 호출. email로 유저를 찾아 UserDetails로 변환한다.
// 못 찾으면 빈 Mono → 인증 매니저가 "자격 증명 불일치"로 처리.
//
// @Service = 이 클래스를 "서비스 계층" 빈으로 등록하는 표시.
//  - @Component의 한 종류(별명) → 컴포넌트 스캔에 잡혀 스프링 컨테이너에 빈으로 자동 등록된다.
//  - 기능은 @Component와 같지만 "비즈니스 로직을 담는 서비스다"라는 의도를 드러내려고 구분해 쓴다.
//
//  ※ 빈 만드는 두 방법 — "누가 new 하느냐"가 차이다:
//     · @Bean    → 설정 클래스 안에서 '내가' 직접 return new ...()로 만든다. (PasswordEncoderConfig가 이 방식)
//     · @Service → 클래스에 딱지만 붙이면 '스프링이' 대신 new 해서 만들어 관리한다. (이 클래스가 이 방식)
//    갈림길: 내가 만든 클래스 → @Service(딱지 붙일 수 있음) / 라이브러리 클래스(수정 불가) → @Bean으로 만든다.
//            예) BCryptPasswordEncoder는 시큐리티 라이브러리 클래스라 @Service를 못 붙여 @Bean으로 등록했다.
//
// implements ReactiveUserDetailsService
//  - 스프링 시큐리티가 정한 규약(인터페이스): "아이디를 주면 사용자 정보를 Mono<UserDetails>로 돌려줘".
//  - 로그인 시 인증 매니저가 이 메서드를 호출해 사용자를 불러온다 → 우리는 "DB에서 어떻게 찾을지"만 채우면 된다.
@Service
public class SecurityUserDetailsService implements ReactiveUserDetailsService {

    // DB 접근 통로. 생성자로 주입받아 보관한다. (final = 한 번 주입되면 안 바뀜)
    private final UserRepository userRepository;

    // 생성자 주입(DI) — 스프링이 UserRepository 빈을 찾아 자동으로 넣어준다.
    //  - 파라미터가 하나뿐이면 @Autowired를 생략해도 스프링이 주입해준다.
    public SecurityUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // @Override = 위 인터페이스(ReactiveUserDetailsService)의 메서드를 구현한다는 표시.
    //  - 파라미터 이름은 규약상 username이지만, 이 앱은 email을 아이디로 쓰므로 email이 들어온다.
    //  - 직접 호출 X — 로그인 시 인증 매니저가 대신 호출한다. (no usage 표시는 정상)
    @Override
    public Mono<UserDetails> findByUsername(String email) {
        // 이 메서드 = "내 User(DB 모델) → 시큐리티 표준 UserDetails"로 바꿔주는 번역 창구(어댑터).
        //   프레임워크는 내 User 클래스를 모르므로, 표준 규격(UserDetails)으로 바꿔 건네줘야 일할 수 있다.
        return userRepository.findByEmail(email) // Mono<User>(우리 엔티티) — 0~1개. 없으면 빈 Mono.
                // .map = 흘러온 User(우리 엔티티)를 스프링 시큐리티가 이해하는 UserDetails로 "변환".
                //  - 빈 Mono면 map은 실행되지 않고 그대로 빈 Mono → 인증 매니저가 "사용자 없음 = 인증 실패"로 처리.
                //  - 아래 User는 우리 엔티티가 아니라 스프링 시큐리티 내장 User(UserDetails 구현체)다.
                //    클래스 이름이 똑같아 충돌하므로, 풀 경로(org.springframework...User)로 구분해 쓴다.
                .map(u -> org.springframework.security.core.userdetails.User
                        .withUsername(u.email())    // 인증 주체의 식별자(=우리는 email)
                        .password(u.passwordHash()) // 저장된 BCrypt 해시 그대로. (실제 비교는 인증 매니저가 PasswordEncoder로 수행)
                        .roles("USER")              // 내 User엔 권한 필드가 없음 → UserDetails는 권한이 '필수'라 기본값 부여 (내부적으로 "ROLE_USER")
                        .build());                  // 설정을 모아 UserDetails 생성 (계정 상태 enabled/잠김X/만료X는 빌더가 기본값으로 채움)
    }
}
