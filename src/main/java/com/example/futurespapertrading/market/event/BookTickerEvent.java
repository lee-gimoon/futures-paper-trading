package com.example.futurespapertrading.market.event;

import java.math.BigDecimal;
import java.time.Instant;

public record BookTickerEvent(
		String eventType,
		long updateId,
		Instant eventTime,
		Instant transactionTime,
		String symbol,
		BigDecimal bidPrice,
		BigDecimal bidQuantity,
		BigDecimal askPrice,
		BigDecimal askQuantity,
		Instant receivedAt
) implements MarketEvent {
}
