package com.example.futurespapertrading.paper.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

// MarginCalculator 단위 테스트. 격리 마진·MMR 0 가정.
class MarginCalculatorTest {

    @Test
    void 사용증거금은_명목금액_나누기_레버리지() {
        Position longPos = new Position(new BigDecimal("1"), new BigDecimal("60000"), BigDecimal.ZERO);
        // 명목 60000, 10x → 6000
        assertEquals(0, new BigDecimal("6000").compareTo(MarginCalculator.usedMargin(longPos, 10)));
    }

    @Test
    void 롱_청산가는_진입가_곱_1빼기_1over레버리지() {
        Position longPos = new Position(new BigDecimal("1"), new BigDecimal("60000"), BigDecimal.ZERO);
        // 10x → 60000 × (1 − 0.1) = 54000
        assertEquals(0, new BigDecimal("54000").compareTo(MarginCalculator.liquidationPrice(longPos, 10)));
    }

    @Test
    void 숏_청산가는_진입가_곱_1더하기_1over레버리지() {
        Position shortPos = new Position(new BigDecimal("-1"), new BigDecimal("60000"), BigDecimal.ZERO);
        // 10x → 60000 × (1 + 0.1) = 66000
        assertEquals(0, new BigDecimal("66000").compareTo(MarginCalculator.liquidationPrice(shortPos, 10)));
    }

    @Test
    void 포지션이_없으면_증거금0_청산가null() {
        Position flat = new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(MarginCalculator.usedMargin(flat, 10)));
        assertNull(MarginCalculator.liquidationPrice(flat, 10));
    }

    @Test
    void 롱은_mark가_청산가_이하면_청산() {
        Position longPos = new Position(new BigDecimal("1"), new BigDecimal("60000"), BigDecimal.ZERO);
        assertTrue(MarginCalculator.isLiquidated(longPos, 10, new BigDecimal("54000")));   // 딱 청산가
        assertTrue(MarginCalculator.isLiquidated(longPos, 10, new BigDecimal("53000")));   // 아래
        assertFalse(MarginCalculator.isLiquidated(longPos, 10, new BigDecimal("55000")));  // 위 — 아직 안전
    }

    @Test
    void 숏은_mark가_청산가_이상이면_청산() {
        Position shortPos = new Position(new BigDecimal("-1"), new BigDecimal("60000"), BigDecimal.ZERO);
        assertTrue(MarginCalculator.isLiquidated(shortPos, 10, new BigDecimal("66000")));
        assertTrue(MarginCalculator.isLiquidated(shortPos, 10, new BigDecimal("67000")));
        assertFalse(MarginCalculator.isLiquidated(shortPos, 10, new BigDecimal("65000")));
    }
}
