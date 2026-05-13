package com.example.futurespapertrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paper-trading")
public record PaperTradingProperties(App app) {

	public PaperTradingProperties {
		if (app == null) {
			app = new App(null, null);
		}
	}

	public record App(String baseCurrency, String defaultSymbol) {

		public App {
			if (baseCurrency == null || baseCurrency.isBlank()) {
				baseCurrency = "USDT";
			}
			if (defaultSymbol == null || defaultSymbol.isBlank()) {
				defaultSymbol = "BTCUSDT";
			}
		}
	}
}
