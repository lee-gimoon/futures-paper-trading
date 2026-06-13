package com.example.futurespapertrading.paper.dto;

import com.example.futurespapertrading.paper.domain.PaperFill;
import com.example.futurespapertrading.paper.domain.PaperOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// 주문 처리 결과를 클라이언트에 돌려줄 응답 DTO(요약만).
//  - 방향: 자바 객체 → JSON (직렬화). 컨트롤러가 이 record를 반환하면 @RestController + Jackson이
//    JSON으로 바꿔 내보낸다. (요청 CreateOrderRequest의 JSON → 객체[역직렬화]와 정반대 방향)
//  - 엔티티(PaperOrder/PaperFill)를 그대로 노출하지 않고, 외부에 보여줄 값만 골라 담는다.
//    (UserResponse가 User에서 passwordHash를 빼고 내보낸 것과 같은 "응답 전용 그릇" 패턴)
//  - 쪼개진 개별 체결(fill) 목록은 응답에 넣지 않는다. fill은 DB(paper_fills)에 저장되며,
//    필요해지면 별도 조회 API로 꺼낸다. 여기선 avgPrice(가중평균 체결가) 한 값으로 요약한다.
//  - 검증 애너테이션(@NotBlank 등)이 없는 이유: 외부에서 받는 입력이 아니라 우리가 직접 만들어
//    내보내는 값이라 검증이 필요 없다. (입력인 CreateOrderRequest는 검증하지만, 출력인 이건 안 한다)
//  - 왜 record인가: 우리가 만들어 한 번 내보내고 끝인 읽기 전용 데이터라, 불변(immutable) record가 딱 맞다.
public record OrderResponse(
        Long id,
        String symbol,
        String side,
        String type,
        String status,             // C단계: FILLED / REJECTED. (E단계에서 OPEN 추가)
        BigDecimal limitPrice,     // 지정가 주문이면 사용자가 걸어둔 가격, 시장가면 null.
        BigDecimal quantity,       // 주문 수량
        BigDecimal filledQuantity, // 실제 체결된 누적 수량 (시장가 부분체결이면 quantity보다 작을 수 있음)
        BigDecimal avgPrice        // 가중평균 체결가 = Σ(price×qty)/Σqty. 0건 체결이면 null.
) {

    // ── from/averagePrice 두 메서드는 record가 자동 생성한 게 아니라, 본문 {}에 직접 추가한 static 메서드다. ──
    //   record도 결국 '클래스'라 일반 클래스처럼 메서드를 넣을 수 있다.
    //   (단, 추가 인스턴스 필드는 못 넣는다 — record의 데이터는 () 컴포넌트로 고정. static 메서드/필드는 OK.)
    //
    // 목적: 주문(엔티티) + 체결목록 → OrderResponse(DTO)로 변환하는 '정적 팩토리 메서드'.
    //   엔티티의 어느 필드가 응답의 어느 필드로 가는지(매핑) + avgPrice 계산을 한 곳에 모은다.
    //   호출부는 OrderResponse.from(saved, fills) 한 줄이면 끝 → avgPrice는 fill들의 수량가중평균으로 자동 계산.
    //   매개변수 fills의 출처: 호출부(컨트롤러)가  List<PaperFill> fills = engine.tryFill(order, snapshot); 로
    //     엔진에게 계산시킨 체결 목록을 그대로 넘긴다. (from은 fills를 만들지 않고, 받아서 쓰기만 한다)
    //   없으면? 컨트롤러가 new OrderResponse(...)에 8개 인자를 직접 채우고 avgPrice 계산까지 떠안아야 한다.
    //           (averagePrice가 private이라 더 곤란) 게다가 응답 만드는 곳마다(D단계 GET 등) 그 코드가 중복된다.
    public static OrderResponse from(PaperOrder order, List<PaperFill> fills) {
        return new OrderResponse(
                order.id(), order.symbol(), order.side(), order.type(),
                order.status(), order.limitPrice(), order.quantity(), order.filledQuantity(),
                averagePrice(fills));
    }

    // 가중평균가 = Σ(체결가 × 체결수량) / Σ체결수량. 체결이 하나도 없으면 null.
    //   왜 '가중'평균인가? 체결마다 수량이 달라서다. 단순평균이 아니라 수량으로 가중해야 실제 평균 단가가 나온다.
    //     예) 100원에 1개 + 200원에 9개 → 단순평균은 (100+200)/2=150이지만, 실제론 (100×1 + 200×9)/10 = 190원에 산 셈.
    //   나눗셈은 끝이 안 떨어질 수 있어 scale 8 + HALF_UP로 반올림한다(DB NUMERIC(38,8)과 같은 정밀도).
    //   ※ BigDecimal.divide는 정밀도를 안 정하면 나누어떨어지지 않을 때 ArithmeticException을 던진다 → 반드시 지정.
    private static BigDecimal averagePrice(List<PaperFill> fills) {
        if (fills.isEmpty()) return null;      // 체결이 0건이면 평균낼 대상이 없음 → null

        // BigDecimal.ZERO = 미리 만들어둔 '0' 상수. BigDecimal.valueOf(0)이나 new BigDecimal("0")로 써도 되지만 ZERO가 제일 깔끔.
        //   ※ 그냥 0(int)은 못 넣는다 — BigDecimal은 객체라 타입이 다르고, 다음 줄 .add()도 못 부른다.
        BigDecimal notional = BigDecimal.ZERO; // Σ(price×qty) — 총 체결 '금액' 누적칸 (0에서 시작)
        BigDecimal qty = BigDecimal.ZERO;      // Σqty        — 총 체결 '수량' 누적칸 (0에서 시작)
        // fill을 하나씩 돌며 두 합계를 누적한다. (multiply/add는 ×/+ 의 BigDecimal판 — 연산자를 못 써서 메서드로)
        for (PaperFill f : fills) {
            notional = notional.add(f.price().multiply(f.quantity())); // 이 체결의 금액(가격×수량)을 총금액에 더함
            qty = qty.add(f.quantity());                               // 이 체결의 수량을 총수량에 더함
        }

        if (qty.signum() == 0) return null;    // 방어: 총수량이 0이면 0으로 나누게 되니 막는다(나눗셈 불가)
        // divide(나눌수, scale, 반올림모드) = ÷ 의 BigDecimal판. 인자 3개의 뜻:
        //   qty     = 나누는 수(divisor)        → notional ÷ qty
        //   8       = scale: 결과를 소수점 아래 8자리까지 유지 (DB NUMERIC(38,8) 정밀도와 맞춤)
        //   HALF_UP = 반올림 방식: 버릴 자리가 5 이상이면 올린다 (우리가 아는 보통 반올림. 예: ...5 → 올림)
        return notional.divide(qty, 8, RoundingMode.HALF_UP); // 총금액 ÷ 총수량 = 가중평균가
    }
}

// ════════════════════════════════════════════════════════════════════════════
// 팩토리 메서드(factory method)란? — from()이 그 예다.
//
// ── 한 줄 정의 ──
//   팩토리(factory) = "객체를 만들어내는 것"(공장이 물건을 찍어내듯).
//   팩토리 메서드   = new 를 직접 부르는 대신, 객체를 만들어 돌려주는 메서드(보통 static).
//
// ── new 와 비교 ──
//   보통:        OrderResponse r = new OrderResponse(id, symbol, ... , avgPrice);  // 8개 인자 직접
//   팩토리 메서드: OrderResponse r = OrderResponse.from(order, fills);              // 내부에서 new 해서 돌려줌
//   → from 안을 보면 결국 return new OrderResponse(...) 다. 즉 from = "OrderResponse를 만들어주는 공장".
//
// ── 왜 static 인가 ──
//   객체를 '만들기 전에' 부르는 거라 아직 인스턴스가 없다 → 클래스에 대고 호출(OrderResponse.from(...)) → static.
//
// ── 왜 new 대신 쓰나 (이점) ──
//   1) 이름으로 의도를 드러냄   — from = "~로부터 변환". new는 클래스명으로 고정이라 의도가 안 보임.
//   2) 호출부 인자를 줄임        — 8개 대신 (order, fills) 2개만.
//   3) 생성 전 로직을 감춤        — 필드 매핑 + avgPrice 계산을 메서드 안에 숨김.
//   4) (고급) 매번 new 안 할 수도 — 기존 객체/캐시를 돌려줄 수도 있다. (예: OrderSide.valueOf 는 기존 싱글톤 반환)
//
// ── 사실 이미 써봤다 ──
//   OrderSide.valueOf("BUY")  ·  BigDecimal.valueOf(10)  ·  List.of("a","b")  ← 전부 팩토리 메서드.
//
// ── 이름 관례 ──
//   from    : 다른 타입으로부터 변환  (예: OrderResponse.from(order))   ← 이 파일이 쓴 것
//   of      : 이 값들로 생성           (예: List.of(a, b))
//   valueOf : 이 값에 해당하는 것을 줘  (예: OrderSide.valueOf("BUY"))
// ════════════════════════════════════════════════════════════════════════════
