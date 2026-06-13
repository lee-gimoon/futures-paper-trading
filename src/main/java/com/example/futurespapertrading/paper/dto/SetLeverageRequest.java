package com.example.futurespapertrading.paper.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// 레버리지 변경 요청 본문. PUT /api/paper/account/leverage 의 @RequestBody.
//   1 ~ 125 범위는 @Valid 게이트가 컨트롤러 전에 검사 → 위반 시 400. (서비스에도 같은 범위 가드가 있다)
public record SetLeverageRequest(
        @NotNull @Min(1) @Max(125) Integer leverage
) {
}
