package com.example.futurespapertrading.paper.dto;

import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

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
