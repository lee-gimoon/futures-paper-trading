package com.example.futurespapertrading.paper.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

// PositionCalculator.compute 단위 테스트. DB·웹 없이 순수 로직만.
//   커버: 단순 롱/숏 진입, 수량가중 평균 진입가, 완전 청산 실현 PnL, 부분 청산, 포지션 뒤집기(flip).
class PositionCalculatorTest {

    @Test
    void 매수_한건이면_롱포지션_평균가는_체결가_실현은0() {
        Position p = PositionCalculator.compute(List.of(
                fill("BUY", "100", "2")));

        assertSigned(p, "2");
        assertAvgEntry(p, "100");
        assertRealized(p, "0");
    }

    @Test
    void 매수_두건이면_평균진입가는_수량가중평균() {
        // (100×1 + 110×3) / 4 = 107.5
        Position p = PositionCalculator.compute(List.of(
                fill("BUY", "100", "1"),
                fill("BUY", "110", "3")));

        assertSigned(p, "4");
        assertAvgEntry(p, "107.5");
        assertRealized(p, "0");
    }

    @Test
    void 매수후_같은수량_매도면_flat이고_실현PnL확정() {
        // 100에 1개 사서 110에 1개 팔면 +10 실현, 포지션 0.
        Position p = PositionCalculator.compute(List.of(
                fill("BUY", "100", "1"),
                fill("SELL", "110", "1")));

        assertSigned(p, "0");
        assertRealized(p, "10");
    }

    @Test
    void 롱_부분청산이면_닫은만큼만_실현되고_진입가는_유지() {
        // 100에 3개 롱 → 110에 1개 매도: 실현 (110-100)×1 = 10, 남은 롱 2개, 진입가 100 유지.
        Position p = PositionCalculator.compute(List.of(
                fill("BUY", "100", "3"),
                fill("SELL", "110", "1")));

        assertSigned(p, "2");
        assertAvgEntry(p, "100");
        assertRealized(p, "10");
    }

    @Test
    void 롱을_초과매도하면_숏으로_뒤집히고_새진입가는_그_체결가() {
        // 100에 1개 롱 → 110에 3개 매도: 1개 닫아 +10 실현, 남은 2개가 110에 숏 진입.
        Position p = PositionCalculator.compute(List.of(
                fill("BUY", "100", "1"),
                fill("SELL", "110", "3")));

        assertSigned(p, "-2");
        assertAvgEntry(p, "110");
        assertRealized(p, "10");
    }

    @Test
    void 숏진입후_더싸게_되사면_이익이_실현된다() {
        // 100에 1개 숏 → 90에 1개 매수(커버): (100-90)×1 = +10 실현, flat.
        Position p = PositionCalculator.compute(List.of(
                fill("SELL", "100", "1"),
                fill("BUY", "90", "1")));

        assertSigned(p, "0");
        assertRealized(p, "10");
    }

    // ── 진입 시점 레버리지 고정(openPositionLeverage) ──────────────────────────

    @Test
    void 진입_run의_주문레버리지를_같은방향_추가에도_유지() {
        // 주문1(20x) 롱 진입 → 주문2(5x) 같은 방향 추가 → run 진입 레버리지 20 유지
        List<PaperFill> fills = List.of(fillO(1L, "BUY", "100", "1"), fillO(2L, "BUY", "110", "1"));
        assertEquals(20, PositionCalculator.openPositionLeverage(fills, Map.of(1L, 20, 2L, 5), 10));
    }

    @Test
    void 포지션이_닫히면_fallback_레버리지() {
        List<PaperFill> fills = List.of(fillO(1L, "BUY", "100", "1"), fillO(2L, "SELL", "110", "1"));
        assertEquals(7, PositionCalculator.openPositionLeverage(fills, Map.of(1L, 20, 2L, 5), 7)); // flat → fallback
    }

    @Test
    void 뒤집히면_뒤집은_주문의_레버리지() {
        // 롱1(20x) → 매도3개(5x): 1닫고 2숏 진입 → run 레버리지 5
        List<PaperFill> fills = List.of(fillO(1L, "BUY", "100", "1"), fillO(2L, "SELL", "110", "3"));
        assertEquals(5, PositionCalculator.openPositionLeverage(fills, Map.of(1L, 20, 2L, 5), 10));
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private static PaperFill fill(String side, String price, String quantity) {
        return new PaperFill(null, 1L, "BTCUSDT",
                side, new BigDecimal(price), new BigDecimal(quantity), BigDecimal.ZERO);
    }

    // orderId를 지정하는 변형 (openPositionLeverage가 주문별 레버리지 맵을 orderId로 찾으므로).
    private static PaperFill fillO(long orderId, String side, String price, String quantity) {
        return new PaperFill(null, orderId, "BTCUSDT",
                side, new BigDecimal(price), new BigDecimal(quantity), BigDecimal.ZERO);
    }

    // BigDecimal은 scale이 달라도 값이 같으면 통과해야 하므로 compareTo로 비교.
    private static void assertSigned(Position p, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(p.signedQuantity()),
                () -> "signedQuantity expected " + expected + " but was " + p.signedQuantity());
    }

    private static void assertAvgEntry(Position p, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(p.averageEntryPrice()),
                () -> "averageEntryPrice expected " + expected + " but was " + p.averageEntryPrice());
    }

    private static void assertRealized(Position p, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(p.realizedPnl()),
                () -> "realizedPnl expected " + expected + " but was " + p.realizedPnl());
    }
}
