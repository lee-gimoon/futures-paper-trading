package com.example.futurespapertrading.market.event;

import java.math.BigDecimal;
import java.time.Instant;

public record MarkPriceEvent(
		String eventType,
		Instant eventTime,
		String symbol,
		BigDecimal markPrice,
		BigDecimal movingAverageMarkPrice,
		BigDecimal indexPrice,
		BigDecimal estimatedSettlePrice,
		BigDecimal fundingRate,
		Instant nextFundingTime,
		Instant receivedAt
) implements MarketEvent {
}
