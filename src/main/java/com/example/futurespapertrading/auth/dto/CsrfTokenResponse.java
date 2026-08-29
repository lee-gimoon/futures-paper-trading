package com.example.futurespapertrading.auth.dto;

// Spring Security가 만든 CSRF 토큰을 클라이언트에 JSON으로 전달하는 응답 DTO다.
// 응답 JSON 예시:
// {
//   "headerName": "X-CSRF-TOKEN",
//   "token": "abc123"
// }
public record CsrfTokenResponse(
        String headerName,
        String token
) {
}
