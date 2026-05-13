package com.example.futurespapertrading.market.event;

import java.math.BigDecimal;
import java.time.Instant;

public record AggTradeEvent(
		String eventType,
		Instant eventTime,
		String symbol,
		long aggregateTradeId,
		BigDecimal price,
		BigDecimal quantity,
		BigDecimal normalQuantity,
		long firstTradeId,
		long lastTradeId,
		Instant tradeTime,
		boolean buyerMarketMaker,
		Instant receivedAt
) implements MarketEvent {
}
