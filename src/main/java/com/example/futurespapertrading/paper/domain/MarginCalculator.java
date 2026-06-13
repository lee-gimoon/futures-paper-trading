package com.example.futurespapertrading.paper.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 레버리지/마진/청산가 순수 계산기. PositionCalculator가 낸 Position과 레버리지를 받아 마진 관련 값을 낸다.
//   격리(isolated) 마진 · 유지증거금률(MMR) 0 · 수수료 0 가정 (MVP). 자세한 모델은 STAGE9-PORTFOLIO.md 참고.
//   PaperTradingEngine·PositionCalculator처럼 DB·웹을 모르는 순수 함수라 단위 테스트가 쉽다.
public final class MarginCalculator {

    private MarginCalculator() {}

    // 명목금액 = 평균진입가 × |수량|. 포지션이 없으면 0.
    public static BigDecimal notional(Position pos) {
        return pos.averageEntryPrice().multiply(pos.signedQuantity().abs());
    }

    // 사용 증거금 = 명목금액 / 레버리지 (격리 마진에서 이 포지션에 묶인 돈). 포지션 없으면 0.
    public static BigDecimal usedMargin(Position pos, int leverage) {
        if (pos.signedQuantity().signum() == 0) return BigDecimal.ZERO;
        return notional(pos).divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);
    }

    // 청산가(격리, MMR 0): 롱 = 진입가×(1 − 1/L), 숏 = 진입가×(1 + 1/L). 포지션 없으면 null.
    //   = mark가 진입가에서 약 1/L 만큼 불리하게 움직이면 묶인 증거금이 전부 소진돼 청산된다는 뜻.
    //   L=1(레버리지 없음)이면 롱 청산가 0(=사실상 청산 안 됨), 숏 청산가 2×진입가.
    public static BigDecimal liquidationPrice(Position pos, int leverage) {
        int dir = pos.signedQuantity().signum();
        if (dir == 0) return null;
        BigDecimal factor = BigDecimal.ONE.divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);
        BigDecimal multiplier = dir > 0 ? BigDecimal.ONE.subtract(factor) : BigDecimal.ONE.add(factor);
        return pos.averageEntryPrice().multiply(multiplier).setScale(8, RoundingMode.HALF_UP);
    }

    // 현재 mark에서 청산 조건에 닿았는가: 롱이면 mark ≤ 청산가, 숏이면 mark ≥ 청산가.
    public static boolean isLiquidated(Position pos, int leverage, BigDecimal mark) {
        BigDecimal liq = liquidationPrice(pos, leverage);
        if (liq == null || mark == null) return false;
        int dir = pos.signedQuantity().signum();
        return dir > 0 ? mark.compareTo(liq) <= 0 : mark.compareTo(liq) >= 0;
    }
}
