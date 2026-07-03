package com.example.futurespapertrading.paper.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 레버리지/마진/청산가 순수 계산기. 도메인 계산 유틸리티 클래스다.
// PositionCalculator가 낸 Position과 레버리지를 받아 마진 관련 값을 낸다.
//   마진 = 포지션을 잡기 위해 잠기는 내 담보금(증거금).
//   진입 명목금액 = 평균진입가(VWAP) × |수량|. 여러 호가 레벨 체결은 VWAP에 이미 반영된다.
//   레버리지 = 진입 명목금액을 마진보다 몇 배 크게 잡는지.
//   청산가 = 손실이 마진을 다 먹어버리는 가격.
//   격리(isolated) 마진 · 유지증거금률(MMR) 0 · 수수료 0 가정 (MVP).
//   PaperTradingEngine·PositionCalculator처럼 DB·웹을 모르는 순수 함수라 단위 테스트가 쉽다.
public final class MarginCalculator {

    private MarginCalculator() {}

    // 진입 명목금액 = 평균진입가(VWAP) × |수량|. 포지션이 없으면 0.
    public static BigDecimal notional(Position pos) {
        return pos.averageEntryPrice().multiply(pos.signedQuantity().abs()); // abs = absolute value(절댓값): 롱/숏 부호를 빼고 포지션 크기만 사용
    }

    // 사용 증거금 = 진입 명목금액 / 레버리지 (격리 마진에서 이 포지션에 묶인 담보금(증거금)). 포지션 없으면 0.
    public static BigDecimal usedMargin(Position pos, int leverage) {
        if (pos.signedQuantity().signum() == 0) return BigDecimal.ZERO;
        return notional(pos).divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP); // 8 = 소수점 8자리까지 계산, HALF_UP = 5 이상이면 올리는 일반 반올림
    }

    // 평균 진입가, 포지션 방향(롱/숏), 레버리지를 가지고 청산가를 계산한다. 포지션 없으면 null.
    // 청산은 손실이 증거금과 같아지는 지점이다.
    // 청산가 계산식(L = leverage): 롱 청산가 = 진입가 - 진입가/L, 숏 청산가 = 진입가 + 진입가/L.
    public static BigDecimal liquidationPrice(Position pos, int leverage) { // 포지션과 레버리지로 청산가를 계산한다.
        int dir = pos.signedQuantity().signum(); // +면 롱, -면 숏, 0이면 포지션 없음.
        if (dir == 0) return null; // 포지션이 없으면 청산가도 없다.
        BigDecimal entryPrice = pos.averageEntryPrice();
        BigDecimal leverageValue = BigDecimal.valueOf(leverage);
        BigDecimal entryPriceDividedByLeverage =
                entryPrice.divide(leverageValue, 8, RoundingMode.HALF_UP);

        // 계산해 둔 진입가/레버리지 값을 포지션 방향에 따라 차감하거나 더한다.
        BigDecimal liquidationPrice = dir > 0
                ? entryPrice.subtract(entryPriceDividedByLeverage)
                : entryPrice.add(entryPriceDividedByLeverage);

        return liquidationPrice.setScale(8, RoundingMode.HALF_UP);
    }

    // 현재 mark 기준으로 포지션이 강제 청산 대상인지 판단한다.
    public static boolean isLiquidated(Position pos, int leverage, BigDecimal mark) {
        BigDecimal liq = liquidationPrice(pos, leverage);
        if (liq == null || mark == null) return false; // 청산가나 현재 mark가 없으면 청산 여부를 판단할 수 없으므로 청산 아님.
        int dir = pos.signedQuantity().signum();

        // compareTo 결과: mark가 청산가보다 작으면 음수, 같으면 0, 크면 양수.
        // dir > 0이면 롱이므로 mark <= 청산가, 아니면 숏이므로 mark >= 청산가이면 청산이다.
        return dir > 0 ? mark.compareTo(liq) <= 0 : mark.compareTo(liq) >= 0;
    }
}
