package com.example.futurespapertrading.market.client;

import com.example.futurespapertrading.market.event.AggTradeEvent;
import com.example.futurespapertrading.market.event.BookTickerEvent;
import com.example.futurespapertrading.market.event.MarkPriceEvent;
import com.example.futurespapertrading.market.event.MarketEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BinanceMarketEventMapperTest {

	private final Instant receivedAt = Instant.parse("2026-05-13T00:00:00Z");
	private final BinanceMarketEventMapper mapper = new BinanceMarketEventMapper(
			new ObjectMapper(),
			Clock.fixed(receivedAt, ZoneOffset.UTC)
	);

	@Test
	void mapsBookTickerPayload() {
		String payload = """
				{
				  "e": "bookTicker",
				  "u": 400900217,
				  "E": 1568014460893,
				  "T": 1568014460891,
				  "s": "BTCUSDT",
				  "b": "25.35190000",
				  "B": "31.21000000",
				  "a": "25.36520000",
				  "A": "40.66000000"
				}
				""";

		MarketEvent event = mapper.toEvent(payload);

		BookTickerEvent bookTicker = assertInstanceOf(BookTickerEvent.class, event);
		assertEquals("BTCUSDT", bookTicker.symbol());
		assertEquals(new BigDecimal("25.35190000"), bookTicker.bidPrice());
		assertEquals(new BigDecimal("25.36520000"), bookTicker.askPrice());
		assertEquals(Instant.ofEpochMilli(1568014460893L), bookTicker.eventTime());
		assertEquals(receivedAt, bookTicker.receivedAt());
	}

	@Test
	void mapsMarkPricePayloadFromCombinedStreamWrapper() {
		String payload = """
				{
				  "stream": "btcusdt@markPrice@1s",
				  "data": {
				    "e": "markPriceUpdate",
				    "E": 1562305380000,
				    "s": "BTCUSDT",
				    "p": "11794.15000000",
				    "ap": "11794.15000000",
				    "i": "11784.62659091",
				    "P": "11784.25641265",
				    "r": "0.00038167",
				    "T": 1562306400000
				  }
				}
				""";

		MarketEvent event = mapper.toEvent(payload);

		MarkPriceEvent markPrice = assertInstanceOf(MarkPriceEvent.class, event);
		assertEquals("BTCUSDT", markPrice.symbol());
		assertEquals(new BigDecimal("11794.15000000"), markPrice.markPrice());
		assertEquals(new BigDecimal("0.00038167"), markPrice.fundingRate());
		assertEquals(Instant.ofEpochMilli(1562306400000L), markPrice.nextFundingTime());
		assertEquals(receivedAt, markPrice.receivedAt());
	}

	@Test
	void mapsAggTradePayload() {
		String payload = """
				{
				  "e": "aggTrade",
				  "E": 123456789,
				  "s": "BTCUSDT",
				  "a": 5933014,
				  "p": "0.001",
				  "q": "100",
				  "nq": "99",
				  "f": 100,
				  "l": 105,
				  "T": 123456785,
				  "m": true
				}
				""";

		MarketEvent event = mapper.toEvent(payload);

		AggTradeEvent aggTrade = assertInstanceOf(AggTradeEvent.class, event);
		assertEquals("BTCUSDT", aggTrade.symbol());
		assertEquals(5933014L, aggTrade.aggregateTradeId());
		assertEquals(new BigDecimal("0.001"), aggTrade.price());
		assertEquals(new BigDecimal("100"), aggTrade.quantity());
		assertEquals(new BigDecimal("99"), aggTrade.normalQuantity());
		assertEquals(Instant.ofEpochMilli(123456785L), aggTrade.tradeTime());
		assertEquals(receivedAt, aggTrade.receivedAt());
	}
}
