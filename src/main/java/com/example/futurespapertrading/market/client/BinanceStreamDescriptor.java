package com.example.futurespapertrading.market.client;

import com.example.futurespapertrading.config.BinanceProperties;

import java.net.URI;
import java.util.List;

public record BinanceStreamDescriptor(String streamName, Endpoint endpoint) {

	public URI uri(BinanceProperties properties) {
		return URI.create(properties.webSocketBaseUrl() + endpoint.path() + "/ws/" + streamName);
	}

	public static List<BinanceStreamDescriptor> forSymbol(String streamSymbol) {
		return List.of(
				new BinanceStreamDescriptor(streamSymbol + "@bookTicker", Endpoint.PUBLIC),
				new BinanceStreamDescriptor(streamSymbol + "@markPrice@1s", Endpoint.MARKET),
				new BinanceStreamDescriptor(streamSymbol + "@aggTrade", Endpoint.MARKET)
		);
	}

	public enum Endpoint {
		PUBLIC("/public"),
		MARKET("/market");

		private final String path;

		Endpoint(String path) {
			this.path = path;
		}

		public String path() {
			return path;
		}
	}
}
