package com.example.futurespapertrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Locale;

@ConfigurationProperties(prefix = "paper-trading.binance")
public record BinanceProperties(
		Boolean enabled,
		String webSocketBaseUrl,
		String symbol,
		Duration reconnectDelay,
		Integer logEveryEvents
) {

	private static final String DEFAULT_WEB_SOCKET_BASE_URL = "wss://fstream.binance.com";
	private static final String DEFAULT_SYMBOL = "BTCUSDT";
	private static final Duration DEFAULT_RECONNECT_DELAY = Duration.ofSeconds(5);
	private static final int DEFAULT_LOG_EVERY_EVENTS = 50;

	public BinanceProperties {
		if (enabled == null) {
			enabled = true;
		}
		if (webSocketBaseUrl == null || webSocketBaseUrl.isBlank()) {
			webSocketBaseUrl = DEFAULT_WEB_SOCKET_BASE_URL;
		}
		if (symbol == null || symbol.isBlank()) {
			symbol = DEFAULT_SYMBOL;
		}
		if (reconnectDelay == null || reconnectDelay.isNegative() || reconnectDelay.isZero()) {
			reconnectDelay = DEFAULT_RECONNECT_DELAY;
		}
		if (logEveryEvents == null || logEveryEvents < 1) {
			logEveryEvents = DEFAULT_LOG_EVERY_EVENTS;
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String normalizedSymbol() {
		return symbol.toUpperCase(Locale.ROOT);
	}

	public String streamSymbol() {
		return symbol.toLowerCase(Locale.ROOT);
	}
}
