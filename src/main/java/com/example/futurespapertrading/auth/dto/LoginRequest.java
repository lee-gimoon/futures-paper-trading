package com.example.futurespapertrading.auth.dto; // 이 파일이 속한 패키지(폴더) 경로 (dto = Data Transfer Object 모음)

import jakarta.validation.constraints.NotBlank; // @NotBlank: null·빈문자열("")·공백만(" ") 입력을 막음 (공백 아닌 글자 1개 이상 필수)

// LoginRequest = 로그인 요청 본문(JSON)을 담는 DTO. 흐름은 SignupRequest와 동일(@RequestBody가 JSON→객체, @Valid가 검증).
//  - record·DTO·검증 흐름의 자세한 설명은 SignupRequest.java 참고. (여기선 차이점만 설명)
//  - AuthController.login(@Valid @RequestBody LoginRequest req, ...) 에서 사용됨.
//
// ※ 회원가입(SignupRequest)과 달리 @Email·@Size 없이 @NotBlank만 쓰는 이유:
//    로그인은 "새 값을 만드는" 게 아니라 "이미 가입된 값과 일치하는지" 확인할 뿐이라, 비어있지만 않으면 된다.
//    형식·길이가 틀려도 어차피 DB의 유저와 안 맞아 인증 실패(401)로 처리된다.
public record LoginRequest(
        // email = 로그인할 이메일. (빈 값만 막으면 됨)
        @NotBlank String email,
        // password = 입력한 비밀번호. DB의 BCrypt 해시와 비교해 일치 여부를 확인한다. (AuthController.login)
        @NotBlank String password
) {
}
