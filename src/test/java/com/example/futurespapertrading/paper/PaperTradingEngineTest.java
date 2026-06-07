package com.example.futurespapertrading.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.futurespapertrading.market.OrderBookLevel;
import com.example.futurespapertrading.market.OrderBookSnapshot;

// PaperTradingEngine.tryFill 단위 테스트. DB·웹 없이 순수 로직만 (new로 직접 생성).
//   커버: 시장가 BUY/SELL 체결가, 1:N 호가 긁기, 지정가 크로싱/미크로싱, 딱 닿는 경계(≤/≥),
//         정렬 가정 안 함, 빈 호가.
class PaperTradingEngineTest {

    private final PaperTradingEngine engine = new PaperTradingEngine();

    // ── 시장가 ────────────────────────────────────────────────────────────────

    @Test
    void 시장가매수_best_ask_한줄에서_체결() {
        var snapshot = snapshot(
                List.of(),                                  // bids (안 씀)
                List.of(lvl("100.0", "5")));                // asks
        var order = order("BUY", "MARKET", null, "0.01");

        List<PaperFill> fills = engine.tryFill(order, snapshot);

        assertEquals(1, fills.size());
        assertFill(fills.get(0), "100.0", "0.01");
    }

    @Test
    void 시장가매수_여러레벨을_긁어_fill이_쪼개진다_1대N() {
        var snapshot = snapshot(
                List.of(),
                List.of(lvl("100.0", "3"), lvl("100.1", "2"), lvl("100.2", "8")));
        var order = order("BUY", "MARKET", null, "10");      // best 3개로 부족 → 위로 긁음

        List<PaperFill> fills = engine.tryFill(order, snapshot);

        assertEquals(3, fills.size());                       // 3 + 2 + 5
        assertFill(fills.get(0), "100.0", "3");
        assertFill(fills.get(1), "100.1", "2");
        assertFill(fills.get(2), "100.2", "5");
    }

    @Test
    void 시장가매도_best_bid부터_아래로_체결() {
        var snapshot = snapshot(
                List.of(lvl("99.9", "3"), lvl("99.8", "4")), // bids
                List.of());                                  // asks (안 씀)
        var order = order("SELL", "MARKET", null, "5");

        List<PaperFill> fills = engine.tryFill(order, snapshot);

        assertEquals(2, fills.size());                       // 3 + 2
        assertFill(fills.get(0), "99.9", "3");
        assertFill(fills.get(1), "99.8", "2");
    }

    @Test
    void 시장가매수_먹을_호가가_없으면_빈목록() {                  // → 호출부가 REJECTED 판단
        var snapshot = snapshot(List.of(), List.of());
        var order = order("BUY", "MARKET", null, "0.01");

        assertTrue(engine.tryFill(order, snapshot).isEmpty());
    }

    // ── 지정가 ────────────────────────────────────────────────────────────────

    @Test
    void 지정가매수_크로싱_한도까지만_긁고_멈춘다() {
        var snapshot = snapshot(
                List.of(),
                List.of(lvl("100.0", "3"), lvl("100.1", "2"), lvl("100.2", "8")));
        var order = order("BUY", "LIMIT", "100.1", "10");    // 100.2는 한도 초과 → 안 먹음

        List<PaperFill> fills = engine.tryFill(order, snapshot);

        assertEquals(2, fills.size());                       // 100.0×3, 100.1×2 (나머지 5는 미체결)
        assertFill(fills.get(0), "100.0", "3");
        assertFill(fills.get(1), "100.1", "2");
    }

    @Test
    void 지정가매수_best_ask에_안닿으면_빈목록() {                 // → 호출부가 OPEN 등록
        var snapshot = snapshot(List.of(), List.of(lvl("100.0", "3")));
        var order = order("BUY", "LIMIT", "99.5", "1");      // 99.5 < bestAsk 100.0

        assertTrue(engine.tryFill(order, snapshot).isEmpty());
    }

    @Test
    void 지정가매수_한도가_best_ask와_딱_같으면_체결된다_경계포함() { // limit >= bestAsk (≥)
        var snapshot = snapshot(List.of(), List.of(lvl("100.0", "3")));
        var order = order("BUY", "LIMIT", "100.0", "1");

        List<PaperFill> fills = engine.tryFill(order, snapshot);

        assertEquals(1, fills.size());
        assertFill(fills.get(0), "100.0", "1");
    }

    @Test
    void 지정가매도_best_bid에_안닿으면_빈목록() {
        var snapshot = snapshot(List.of(lvl("99.9", "3")), List.of());
        var order = order("SELL", "LIMIT", "100.5", "1");    // 100.5 > bestBid 99.9

        assertTrue(engine.tryFill(order, snapshot).isEmpty());
    }

    @Test
    void 지정가매도_한도가_best_bid와_딱_같으면_체결된다_경계포함() { // limit <= bestBid (≤)
        var snapshot = snapshot(List.of(lvl("99.9", "3")), List.of());
        var order = order("SELL", "LIMIT", "99.9", "1");

        List<PaperFill> fills = engine.tryFill(order, snapshot);

        assertEquals(1, fills.size());
        assertFill(fills.get(0), "99.9", "1");
    }

    // ── 정렬 가정 안 함 ──────────────────────────────────────────────────────────

    @Test
    void 호가가_뒤섞여_와도_best부터_긁는다() {
        var snapshot = snapshot(
                List.of(),
                List.of(lvl("100.2", "8"), lvl("100.0", "3"), lvl("100.1", "2"))); // 일부러 섞음
        var order = order("BUY", "MARKET", null, "4");

        List<PaperFill> fills = engine.tryFill(order, snapshot);

        assertEquals(2, fills.size());                       // 100.0×3, 100.1×1
        assertFill(fills.get(0), "100.0", "3");
        assertFill(fills.get(1), "100.1", "1");
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private static OrderBookLevel lvl(String price, String qty) {
        return new OrderBookLevel(new BigDecimal(price), new BigDecimal(qty));
    }

    private static OrderBookSnapshot snapshot(List<OrderBookLevel> bids, List<OrderBookLevel> asks) {
        return new OrderBookSnapshot("BTCUSDT", 0L, bids, asks);
    }

    private static PaperOrder order(String side, String type, String limitPrice, String quantity) {
        return new PaperOrder(
                1L, 1L, null, "BTCUSDT",
                side, type, "NEW",
                limitPrice == null ? null : new BigDecimal(limitPrice),
                new BigDecimal(quantity),
                BigDecimal.ZERO);
    }

    // BigDecimal은 scale이 달라도(예: 3 vs 3.0) 값이 같으면 통과해야 하므로 compareTo로 비교.
    private static void assertFill(PaperFill fill, String price, String quantity) {
        assertEquals(0, new BigDecimal(price).compareTo(fill.price()),
                () -> "price expected " + price + " but was " + fill.price());
        assertEquals(0, new BigDecimal(quantity).compareTo(fill.quantity()),
                () -> "quantity expected " + quantity + " but was " + fill.quantity());
    }
}
