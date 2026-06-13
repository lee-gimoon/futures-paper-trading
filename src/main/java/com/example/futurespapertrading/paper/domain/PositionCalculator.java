package com.example.futurespapertrading.paper.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

// 9단계 핵심 도메인 로직. 한 사용자의 체결 목록(오래된 순)을 받아 "현재 포지션 + 실현 PnL"을 계산하는 순수 함수다.
//   PaperTradingEngine과 같은 성격 — DB·웹을 모르고 입력(fills)만 보고 답을 낸다 → 단위 테스트가 쉽다.
//
// 계산 모델 (선물이라 롱/숏·포지션 뒤집기를 다룬다):
//   체결을 시간 순서대로 하나씩 누적하며 보유 수량(signed)·평균 진입가·실현 PnL을 갱신한다.
//   각 체결은 둘 중 하나다.
//     ① 포지션이 없거나 같은 방향 → '증가': 평균 진입가를 수량가중으로 다시 잡는다.
//     ② 반대 방향 → '축소/청산/뒤집기': 닿는 만큼 기존 포지션을 닫아 실현 PnL을 확정하고,
//        남는 수량이 있으면 그 가격으로 반대 포지션을 새로 연다.
//   (자세한 단계별 예시·증명은 STAGE9-PORTFOLIO.md 참고.)
//
// 수수료(fill.fee)는 8·9단계 MVP라 0이므로 PnL에 넣지 않는다(로드맵: 수수료/펀딩비는 나중 단계).
public final class PositionCalculator {

    private PositionCalculator() {}   // 유틸이라 인스턴스 생성 금지 (OrderBookQuotes와 같은 패턴)

    public static Position compute(List<PaperFill> fills) {
        BigDecimal qty = BigDecimal.ZERO;        // 부호 있는 보유 수량 (+롱 / -숏 / 0 flat)
        BigDecimal avgEntry = BigDecimal.ZERO;   // 현재 열린 포지션의 평균 진입가 (flat이면 0)
        BigDecimal realized = BigDecimal.ZERO;   // 확정된 실현 PnL 누적

        for (PaperFill f : fills) {
            // 체결 방향을 부호 있는 수량으로: BUY는 +, SELL은 -.
            BigDecimal signed = OrderSide.BUY.name().equals(f.side()) ? f.quantity() : f.quantity().negate();
            BigDecimal price = f.price();

            if (qty.signum() == 0 || qty.signum() == signed.signum()) {
                // ① 포지션 없음 or 같은 방향 → 증가. 평균 진입가 = (기존수량×기존평균 + 추가수량×체결가) / 총수량.
                BigDecimal absQty = qty.abs();
                BigDecimal absSigned = signed.abs();
                BigDecimal totalAbs = absQty.add(absSigned);
                avgEntry = absQty.multiply(avgEntry).add(absSigned.multiply(price))
                        .divide(totalAbs, 8, RoundingMode.HALF_UP);   // NUMERIC(38,8) 정밀도에 맞춰 반올림
                qty = qty.add(signed);
            } else {
                // ② 반대 방향 → 닿는 만큼(closeQty) 기존 포지션을 닫는다.
                BigDecimal closeQty = signed.abs().min(qty.abs());
                // 닫는 부분의 실현 손익 = (체결가 − 평균진입가) × 닫은수량 × 방향부호.
                //   롱(qty>0): 비싸게 팔면(+) 이익 / 숏(qty<0): 싸게 사면(부호가 뒤집혀) 이익.
                BigDecimal direction = BigDecimal.valueOf(qty.signum());   // 롱 +1, 숏 -1
                realized = realized.add(price.subtract(avgEntry).multiply(closeQty).multiply(direction));

                BigDecimal newQty = qty.add(signed);
                if (newQty.signum() == 0) {
                    avgEntry = BigDecimal.ZERO;        // 정확히 청산 → flat
                } else if (qty.signum() != newQty.signum()) {
                    avgEntry = price;                  // 다 닫고 남은 수량이 반대 포지션을 새로 엶 → 진입가 = 이 체결가
                }
                // 그 외(부분 청산, 방향 유지) → 평균 진입가 그대로.
                qty = newQty;
            }
        }
        return new Position(qty, avgEntry, realized);
    }

    // 현재 열려 있는 포지션이 '진입한 시점의 레버리지'를 돌려준다(격리 마진의 포지션 레버리지 고정).
    //   체결을 시간순으로 보며, 포지션이 0→비0으로 '새로 열리거나' 방향이 '뒤집힌' 순간의 주문 레버리지를 채택한다.
    //   같은 방향으로 추가될 때는 그 run의 진입 레버리지를 유지한다. 포지션이 없으면(flat) fallback(계좌 현재 레버리지).
    //   orderLeverage = orderId → 주문 시점 레버리지 맵.
    public static int openLeverage(List<PaperFill> fills, Map<Long, Integer> orderLeverage, int fallback) {
        BigDecimal qty = BigDecimal.ZERO;
        int lev = fallback;
        for (PaperFill f : fills) {
            BigDecimal signed = OrderSide.BUY.name().equals(f.side()) ? f.quantity() : f.quantity().negate();
            int before = qty.signum();
            qty = qty.add(signed);
            int after = qty.signum();
            if (after == 0)
                lev = fallback;                                    // 닫힘(flat) → 다음 진입 전까지 기본값으로
            else if (before == 0 || before != after)
                lev = orderLeverage.getOrDefault(f.orderId(), fallback); // 새로 열림 or 방향 뒤집힘 → 이 주문의 레버리지 채택
        }
        return lev;
    }
}
