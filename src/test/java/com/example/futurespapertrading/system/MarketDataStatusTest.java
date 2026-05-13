package com.example.futurespapertrading.system;

import com.example.futurespapertrading.market.event.BookTickerEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketDataStatusTest {

	@Test
	void markEventUpdatesAtomicCounterAndLastEventTime() {
		MarketDataStatus status = new MarketDataStatus();
		Instant receivedAt = Instant.parse("2026-05-13T00:00:00Z");
		BookTickerEvent event = new BookTickerEvent(
				"bookTicker",
				1L,
				Instant.parse("2026-05-13T00:00:00Z"),
				Instant.parse("2026-05-13T00:00:00Z"),
				"BTCUSDT",
				new BigDecimal("100.0"),
				new BigDecimal("1.0"),
				new BigDecimal("101.0"),
				new BigDecimal("2.0"),
				receivedAt
		);

		status.configure(true, "BTCUSDT");
		status.registerStream(
				"btcusdt@bookTicker",
				"PUBLIC",
				"wss://fstream.binance.com/public/ws/btcusdt@bookTicker",
				MarketDataStatus.ConnectionState.STOPPED
		);

		long eventCount = status.markEvent("btcusdt@bookTicker", event);
		MarketDataStatus.StreamStatus stream = status.snapshot().streams().getFirst();

		assertEquals(1L, eventCount);
		assertEquals(1L, stream.eventCount());
		assertEquals(receivedAt, stream.lastEventAt());
		assertEquals(MarketDataStatus.ConnectionState.CONNECTED, stream.state());
	}
}
