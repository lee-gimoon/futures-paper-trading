package com.example.futurespapertrading.market.event;

import java.time.Instant;

public sealed interface MarketEvent permits BookTickerEvent, MarkPriceEvent, AggTradeEvent {

	String eventType();

	String symbol();

	Instant eventTime();

	Instant receivedAt();
}
