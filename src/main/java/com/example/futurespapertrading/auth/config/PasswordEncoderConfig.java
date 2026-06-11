package com.example.futurespapertrading.auth.config; // 이 파일이 속한 패키지(폴더) 경로

import org.springframework.context.annotation.Bean;                       // 메서드가 만든 객체를 스프링 빈으로 등록하라는 표시 애너테이션
import org.springframework.context.annotation.Configuration;              // 이 클래스가 스프링 "설정 클래스"임을 표시하는 애너테이션
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;  // BCrypt 알고리즘으로 비밀번호를 해시하는 구현체
import org.springframework.security.crypto.password.PasswordEncoder;      // 비밀번호 인코더의 공통 인터페이스(타입)

// 비밀번호는 평문 저장 금지 → BCrypt 단방향 해시.
//
// @Configuration = "객체를 어떻게 만들지 적어둔 설정 클래스"라는 표시.
//  - 객체 생성법(@Bean)을 코드 곳곳에 흩어두지 않고 한 곳에 모아두는 곳.
//    → 옛날 스프링이 XML에 적던 "이런 객체 만들어라"를 Java로 대체한 것 (이름이 설정=Configuration인 이유).
//  - 스프링 부팅 시 이 클래스를 읽어, 안의 @Bean 메서드대로 객체를 만들어 컨테이너에 보관한다.
@Configuration
public class PasswordEncoderConfig { // 비밀번호 인코더 빈을 정의하는 설정 클래스

    // @Bean = 이 메서드가 만든 객체(PasswordEncoder)를 스프링 컨테이너에 "빈(Bean)"으로 등록하라는 표시.
    //  - 빈(Bean) = 스프링 컨테이너가 만들어서 관리하는 객체.
    //    (관리 = 개발자가 직접 new로 만드는 게 아니라, 컨테이너가 생성·보관·주입·소멸까지 대신 맡는다는 뜻)
    //  - 기본적으로 메서드 이름("passwordEncoder")이 빈 이름이 된다.
    //  - 다른 클래스에서 PasswordEncoder를 주입(생성자/@Autowired)받으면 여기서 만든 이 빈이 들어간다.
    //  - 빈은 기본적으로 싱글톤이다(컨테이너에 딱 하나만 만들어 보관) → 앱 전체가 이 인코더 객체 하나를 공유한다.
    //
    // 직접 호출 X — 스프링이 컨테이너를 만들 때 한 번 호출해 빈을 생성/보관한다. (no usage 표시는 정상)
    @Bean
    // 반환 타입 PasswordEncoder(인터페이스) = 구현체를 숨겨 나중에 다른 인코더로 교체가 쉽도록 한 것
    public PasswordEncoder passwordEncoder() {
        // BCryptPasswordEncoder = BCrypt 해시 구현체.
        //  - 해시할 때마다 무작위 salt를 자동 생성 → 같은 비밀번호라도 매번 다른 해시가 나온다.
        //  - 단방향이라 복호화 불가 → 검증은 matches(평문, 저장된해시)로 비교한다.
        return new BCryptPasswordEncoder();
    }
}
