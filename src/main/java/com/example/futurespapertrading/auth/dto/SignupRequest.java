package com.example.futurespapertrading.auth.dto; // 이 파일이 속한 패키지(폴더) 경로 (dto = Data Transfer Object 모음)

import jakarta.validation.constraints.Email;    // @Email: 문자열이 이메일 형식(@ 포함, 도메인 등)인지 검증
import jakarta.validation.constraints.NotBlank; // @NotBlank: null·빈문자열("")·공백만(" ") 입력을 막음 (공백 아닌 글자 1개 이상 필수)
import jakarta.validation.constraints.Size;     // @Size: 문자열 길이(글자 수)의 최소(min)/최대(max) 범위를 제한

// SignupRequest = 회원가입 요청 본문(JSON)을 담는 DTO(Data Transfer Object, 계층 간 데이터 운반 전용 객체).
//  - DTO를 쓰는 이유는 여러가지가 있지만 가장 큰 이유는: @Valid + @Email·@NotBlank 같은 검증 애너테이션은 DTO 필드에만 붙일 수 있어서,
//    DTO 없이는 검증 코드를 직접 짜야 한다.
//  - 흐름: 클라이언트가 보낸 JSON → 컨트롤러의 @RequestBody가 이 객체로 변환 → @Valid가 아래 검증 규칙을 검사.
//    (AuthController.signup(@Valid @RequestBody SignupRequest req) 에서 사용됨)
//  - 검증 규칙(@Email 등)을 위반하면 서비스 로직에 도달하기 전에 스프링이 자동으로 HTTP 400(Bad Request)으로 막는다.
//
// record = 자바 16+의 "불변(immutable) 데이터 운반용" 클래스 단축 문법.
//  - 괄호 안 항목(email, password, displayName)을 "레코드 컴포넌트(record component, 구성요소)"라고 부른다.
//    (※ 정확한 명칭은 "컴포넌트". 실무에선 "필드"라고도 하는데, 아래처럼 실제로 필드가 만들어지기 때문이다.)
//  - 컴포넌트 하나하나로부터 컴파일러가 자동으로 만들어준다:
//      ① private final 필드(멤버 변수)   ② 값을 꺼내는 접근자 메서드(email()/password()/displayName())
//      ③ 이들을 한 번에 받는 생성자       ④ equals()·hashCode()·toString()
//    (그래서 본문 {}는 비어 있어도 됨)
//  - 한 번 만들어지면 값을 바꿀 수 없어, 요청 본문 같은 "읽기 전용 데이터" 그릇으로 안전하다.
public record SignupRequest(
        // email = 가입자 이메일. @Email로 형식 검사 + @NotBlank로 빈 값 금지.
        //  ※ @Email만 쓰면 null/빈문자열도 "형식 검사 통과"로 봐서 누락을 못 막는다 → @NotBlank를 같이 붙여 실제 값을 강제.
        @Email @NotBlank String email,
        // password = 원본 비밀번호. @NotBlank로 빈 값 금지 + @Size로 길이 8~100자로 제한.
        //  ※ 여기 들어온 원본은 그대로 저장하지 않고, AuthService에서 BCrypt 해시로 변환해 DB에 저장한다.
        @NotBlank @Size(min = 8, max = 100) String password,
        // displayName = 화면에 보일 이름(선택 항목). @Size(max=100)만 있어 null이면 통과(필수 아님), 값이 있으면 100자 이하만 허용.
        @Size(max = 100) String displayName
) {
}

// ── 실제로 클라이언트가 보내는 요청 본문 모습 (예: 회원가입 요청 시) ──
//
//   POST /api/auth/signup
//   Content-Type: application/json
//
//   {
//     "email": "a@b.com",      ← @Email @NotBlank 검사 대상
//     "password": "secret12",  ← @NotBlank @Size(8~100) 검사 대상 (8자 이상이라 통과)
//     "displayName": "철수"     ← @Size(max=100) 검사 대상 (생략 가능)
//   }
//
//   - 위 JSON이 @RequestBody에 의해 SignupRequest 객체로 변환된다.
//   - 만약 password가 "123"(3자)처럼 규칙을 어기면 → @Valid가 걸러내 HTTP 400 응답이 나가고, 컨트롤러 로직은 실행되지 않는다.
//
// ── JSON ↔ 자바 객체 변환은 누가 하나: Jackson ──
//
//   Jackson = 자바에서 가장 널리 쓰이는 JSON 라이브러리.
//   Spring Boot가 spring-boot-starter-web 의존성 안에 자동으로 포함시켜 두기 때문에
//   개발자가 별도로 설정하지 않아도 @RequestBody / @ResponseBody 뒤에서 알아서 동작한다.
//
//   두 방향 변환을 모두 담당한다:
//     역직렬화(deserialization): JSON → 자바 객체  ← 요청 본문을 SignupRequest로 변환할 때
//     직렬화  (serialization)  : 자바 객체 → JSON  ← UserResponse를 응답 본문으로 변환할 때
//
//   Content-Type: application/json 헤더를 보고 Spring이 Jackson을 선택하는 흐름:
//     요청 수신 → Content-Type 확인 → "application/json이니까 Jackson으로 파싱"
//              → JSON 문자열을 SignupRequest 객체로 변환 → 컨트롤러 메서드에 전달
