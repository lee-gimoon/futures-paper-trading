package com.example.futurespapertrading.auth.dto;

// 응답에는 비밀번호 해시를 절대 포함하지 않는다.
public record UserResponse(Long id, String email, String displayName) {
}
