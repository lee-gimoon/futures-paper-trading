package com.example.futurespapertrading.paper.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

// 레버리지 변경 요청 본문. PUT /api/paper/account/leverage 의 @RequestBody.
//   UI 프리셋과 같은 값만 @Valid 게이트가 컨트롤러 전에 검사 → 위반 시 400. (서비스에도 같은 가드가 있다)
public record SetLeverageRequest(
        @NotNull Integer leverage
) {
    private static final Set<Integer> ALLOWED_LEVERAGES = Set.of(1, 3, 5, 10, 20, 50);

    @AssertTrue(message = "레버리지는 1, 3, 5, 10, 20, 50 중 하나여야 합니다")
    public boolean isAllowedLeverage() {
        return leverage == null || ALLOWED_LEVERAGES.contains(leverage);
    }
}

// @Valid 흐름 정리:
//   PortfolioController.setLeverage(...)의 파라미터에 @Valid가 붙어 있다.
//   Spring은 요청 JSON을 SetLeverageRequest 객체로 만든 뒤, 컨트롤러 메서드 본문을 실행하기 전에 Bean Validation을 돌린다.
//   이때 @NotNull, @AssertTrue 같은 검증 애너테이션을 찾아 검사한다.
//
//   @NotNull Integer leverage:
//     leverage 값이 null이면 검증 실패.
//
//   @AssertTrue public boolean isAllowedLeverage():
//     Bean Validation이 이 boolean 메서드를 자동 호출한다.
//     반환값이 true면 통과, false면 검증 실패.
//     검증 실패 시 컨트롤러 메서드 본문에 들어가기 전에 HTTP 400 응답으로 막힌다.
//
//   isAllowedLeverage()에서 leverage == null을 true로 둔 이유:
//     null 여부는 @NotNull이 이미 담당한다.
//     이 메서드는 null 검사가 아니라 "허용된 레버리지 값인가"만 검사하려고 분리한 것이다.
