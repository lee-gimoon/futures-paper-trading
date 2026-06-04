package com.example.futurespapertrading.auth.dto; // 이 파일이 속한 패키지(폴더) 경로 (dto = Data Transfer Object 모음)

// UserResponse = 클라이언트에 "돌려줄" 유저 정보를 담는 응답 DTO. (요청용 SignupRequest와 반대 방향)
//  - 방향: 자바 객체 → JSON (직렬화). Jackson이 이 객체를 응답 본문(JSON)으로 변환한다.
//  - 검증 애너테이션(@NotBlank 등)이 없는 이유: 외부에서 받는 입력이 아니라 우리가 직접 만들어 내보내는 값이라 검증 불필요.
//  - AuthController의 signup/me 응답에서 new UserResponse(...)로 만들어 반환됨.
//
// ※ User 엔티티를 그대로 반환하지 않고 굳이 UserResponse를 따로 두는 이유:
//    User에는 passwordHash(비밀번호 해시) 같은 민감 필드가 들어있다 → 그대로 응답하면 해시가 클라이언트로 새 나간다.
//    그래서 안전한 필드(id·email·displayName)만 골라 담은 "응답 전용 그릇"을 따로 만든다. (해시는 절대 포함 X)
public record UserResponse(Long id, String email, String displayName) {
}
