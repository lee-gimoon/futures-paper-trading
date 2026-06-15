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
    // PaperFill 엔티티로 FillResponse 응답 객체를 만들어 주는 static 팩토리 메서드.
    // 생성자(new FillResponse(...))를 호출부마다 직접 쓰면 필드 순서와 매핑을 매번 맞춰야 해서 실수하기 쉽다.
    // from(...)에 변환 규칙을 모아두면 호출부는 FillResponse.from(f)만 쓰면 되고,
    // 엔티티의 어떤 값을 화면 응답에 노출할지 이 한 곳에서 관리할 수 있다.
    public static FillResponse from(PaperFill f) {
        return new FillResponse(f.id(), f.orderId(), f.symbol(), f.side(), f.price(), f.quantity());
    }
}

// 왜 엔티티(PaperFill)를 그대로 응답하지 않고 DTO(FillResponse)를 쓰나:
//   엔티티는 서버 내부의 DB 저장 구조이고, DTO는 외부에 보여줄 API 응답 계약이다.
//   둘을 분리하면 DB 테이블/엔티티가 바뀌어도 API 응답 모양을 안정적으로 유지할 수 있다.
//
//   화면에 필요한 값만 고를 수 있다:
//     PaperFill에는 fee 같은 내부 계산/저장용 값이 있지만, 지금 체결 내역 화면에는 의미가 없어 응답에서 제외한다.
//
//   노출하면 안 되는 값도 숨길 수 있다:
//     인증 쪽 User 엔티티의 passwordHash를 UserResponse에 넣지 않는 것과 같은 패턴이다.
//
//   응답 형태를 프론트에 맞게 가공할 수 있다:
//     DB 컬럼 구조 그대로가 아니라, 화면이 쓰기 좋은 이름과 필드 구성으로 내보낼 수 있다.
//
// 한 줄 정리:
//   엔티티는 내부 저장용, DTO는 외부 응답용이다.
