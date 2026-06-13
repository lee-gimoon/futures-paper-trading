package com.example.futurespapertrading.paper.dto;

import java.math.BigDecimal;

import com.example.futurespapertrading.paper.domain.PaperFill;

// 체결 내역 화면용 응답 DTO. GET /api/paper/fills 가 돌려준다.
//   엔티티 PaperFill을 그대로 노출하지 않고 화면에 보일 값만 담는다(fee는 9단계 MVP라 의미 없어 제외).
//   executed_at은 엔티티가 안 들고 있어(DB DEFAULT) 여기에도 없다 — 순서는 id 오름차순이 곧 시간순.
public record FillResponse(
        Long id,
        Long orderId,
        String symbol,
        String side,
        BigDecimal price,
        BigDecimal quantity
) {
    public static FillResponse from(PaperFill f) {
        return new FillResponse(f.id(), f.orderId(), f.symbol(), f.side(), f.price(), f.quantity());
    }
}
