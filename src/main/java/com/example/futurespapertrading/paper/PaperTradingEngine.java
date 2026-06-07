package com.example.futurespapertrading.paper;

import java.math.BigDecimal;      // 가격·수량 정밀 계산 (남은 수량 차감 subtract, 둘 중 작은 값 min)
import java.util.ArrayList;       // 체결 결과(fill)를 레벨마다 하나씩 담는 가변 리스트
import java.util.Comparator;      // 호가를 가격순으로 정렬 (오름차순 / reversed로 내림차순)
import java.util.List;            // 반환 타입(fill 목록)과 정렬된 호가 레벨 목록

import com.example.futurespapertrading.market.OrderBookLevel;     // 호가창의 한 레벨(price, quantity) — 긁는 단위
import com.example.futurespapertrading.market.OrderBookSnapshot;  // 한 시점 호가창(bids/asks) — 체결 기준 입력

// 순수 체결 엔진. "주문 1건 + 호가 snapshot → 체결(fill) 목록"만 계산한다.
//   순수 함수 = 입력만 보고 출력만 낸다. DB도 웹도 외부 상태도 안 건드린다 → 단위 테스트가 쉽다. (B단계 ★)
//
// 이 엔진은 "체결을 어떻게 나누나"만 책임진다. 주문 상태(FILLED/OPEN/REJECTED)를 정하거나 DB에 저장하는 건
// 호출부(C·G단계 컨트롤러/matcher)의 몫이다. 엔진은 그저 fill 목록을 돌려줄 뿐이다.
public class PaperTradingEngine {

    private static final BigDecimal FEE_ZERO = BigDecimal.ZERO;   // 8단계 MVP 수수료 0

    // 주문을 현재 snapshot에 대고 체결해 본다.
    //   반환 = 체결된 fill 목록. 호가 레벨을 가격순으로 긁으며 레벨마다 1건씩 → 주문 1 : fill N.
    //   empty 목록의 의미(둘 다 "체결 0건"):
    //     · 시장가인데 먹을 호가가 비어 있음        → 호출부가 REJECTED로 판단
    //     · 지정가인데 한도가 best에 안 닿음          → 호출부가 OPEN으로 등록
    public List<PaperFill> tryFill(PaperOrder order, OrderBookSnapshot snapshot) {
        OrderSide side = OrderSide.valueOf(order.side());
        OrderType type = OrderType.valueOf(order.type());

        // 어느 쪽 호가를 먹나:
        //   BUY  → asks(매도호가)를 싼 가격부터 (오름차순)
        //   SELL → bids(매수호가)를 비싼 가격부터 (내림차순)
        List<OrderBookLevel> levels = (side == OrderSide.BUY)
                ? sortedAscending(snapshot.asks())
                : sortedDescending(snapshot.bids());

        List<PaperFill> fills = new ArrayList<>();
        BigDecimal remaining = order.quantity();        // 아직 못 채운 수량

        for (OrderBookLevel level : levels) {
            if (remaining.signum() <= 0) break;          // 다 채웠으면 멈춤

            // 지정가 가격 한도: 한도를 벗어난 레벨이 나오는 순간 멈춘다(가격순이라 그 뒤는 다 벗어남).
            //   BUY  : level.price > limit → 더 비싸짐 → 멈춤
            //   SELL : level.price < limit → 더 싸짐   → 멈춤
            //   limit과 '딱 같은' 가격은 체결된다(로드맵 ≤/≥ 경계 포함).
            if (type == OrderType.LIMIT) {
                BigDecimal limit = order.limitPrice();
                if (side == OrderSide.BUY && level.price().compareTo(limit) > 0) break;
                if (side == OrderSide.SELL && level.price().compareTo(limit) < 0) break;
            }

            BigDecimal take = remaining.min(level.quantity());   // 이 레벨에서 먹는 수량(남은 양 vs 호가 물량 중 작은 쪽)
            fills.add(new PaperFill(
                    null,                // id — DB가 부여
                    order.id(),          // 어느 주문의 체결인지
                    order.accountId(),   // 9단계 전까지 null
                    order.symbol(),
                    order.side(),
                    level.price(),       // 체결가 = 이 호가 레벨의 가격 (레벨마다 달라 fill이 쪼개진다)
                    take,                // 체결 수량
                    FEE_ZERO
            ));
            remaining = remaining.subtract(take);
        }

        return fills;
    }

    // "정렬 가정 안 함": 받은 순서에 기대지 않고 가격 기준으로 직접 정렬해 best부터 긁는다.
    private static List<OrderBookLevel> sortedAscending(List<OrderBookLevel> levels) {
        return levels.stream()
                .sorted(Comparator.comparing(OrderBookLevel::price))
                .toList();
    }

    private static List<OrderBookLevel> sortedDescending(List<OrderBookLevel> levels) {
        return levels.stream()
                .sorted(Comparator.comparing(OrderBookLevel::price).reversed())
                .toList();
    }
}
