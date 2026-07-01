package com.example.futurespapertrading.market.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

// OrderBookQuotes 단위 테스트. 정렬 가정 안 하고 max/min으로 best를 고르는지, 빈쪽은 null인지 확인.
class OrderBookQuotesTest {

    @Test
    void bestBid는_뒤섞인_bids에서_최고가() {
        var snapshot = snapshot(
                List.of(lvl("99.8", "1"), lvl("99.9", "1"), lvl("99.7", "1")),  // 일부러 섞음
                List.of());

        assertEquals(0, new BigDecimal("99.9").compareTo(OrderBookQuotes.bestBid(snapshot)));
    }

    @Test
    void bestAsk는_뒤섞인_asks에서_최저가() {
        var snapshot = snapshot(
                List.of(),
                List.of(lvl("100.2", "1"), lvl("100.0", "1"), lvl("100.1", "1"))); // 섞음

        assertEquals(0, new BigDecimal("100.0").compareTo(OrderBookQuotes.bestAsk(snapshot)));
    }

    @Test
    void 한쪽이_비면_null() {
        var snapshot = snapshot(List.of(), List.of());

        assertNull(OrderBookQuotes.bestBid(snapshot));
        assertNull(OrderBookQuotes.bestAsk(snapshot));
    }

    private static OrderBookLevel lvl(String price, String qty) {
        return new OrderBookLevel(new BigDecimal(price), new BigDecimal(qty));
    }

    private static OrderBookSnapshot snapshot(List<OrderBookLevel> bids, List<OrderBookLevel> asks) {
        return new OrderBookSnapshot("BTCUSDT", 0L, bids, asks);
    }
}
