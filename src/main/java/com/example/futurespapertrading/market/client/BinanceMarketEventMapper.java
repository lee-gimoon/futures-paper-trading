package com.example.futurespapertrading.market.client;

import com.example.futurespapertrading.market.event.AggTradeEvent;
import com.example.futurespapertrading.market.event.BookTickerEvent;
import com.example.futurespapertrading.market.event.MarkPriceEvent;
import com.example.futurespapertrading.market.event.MarketEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

@Component
public class BinanceMarketEventMapper {

	private final ObjectMapper objectMapper;
	private final Clock clock;

	@Autowired
	public BinanceMarketEventMapper(ObjectMapper objectMapper) {
		this(objectMapper, Clock.systemUTC());
	}

	BinanceMarketEventMapper(ObjectMapper objectMapper, Clock clock) {
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	public MarketEvent toEvent(String payload) {
		JsonNode data = readPayload(payload);
		String eventType = requiredText(data, "e");
		Instant receivedAt = Instant.now(clock);

		return switch (eventType) {
			case "bookTicker" -> toBookTickerEvent(data, receivedAt);
			case "markPriceUpdate" -> toMarkPriceEvent(data, receivedAt);
			case "aggTrade" -> toAggTradeEvent(data, receivedAt);
			default -> throw new IllegalArgumentException("Unsupported Binance market event type: " + eventType);
		};
	}

	private JsonNode readPayload(String payload) {
		try {
			JsonNode root = objectMapper.readTree(payload);
			if (root.hasNonNull("data")) {
				return root.get("data");
			}
			return root;
		} catch (JacksonException e) {
			throw new IllegalArgumentException("Invalid Binance market data payload", e);
		}
	}

	private BookTickerEvent toBookTickerEvent(JsonNode data, Instant receivedAt) {
		return new BookTickerEvent(
				requiredText(data, "e"),
				requiredLong(data, "u"),
				requiredInstant(data, "E"),
				requiredInstant(data, "T"),
				requiredText(data, "s"),
				requiredBigDecimal(data, "b"),
				requiredBigDecimal(data, "B"),
				requiredBigDecimal(data, "a"),
				requiredBigDecimal(data, "A"),
				receivedAt
		);
	}

	private MarkPriceEvent toMarkPriceEvent(JsonNode data, Instant receivedAt) {
		return new MarkPriceEvent(
				requiredText(data, "e"),
				requiredInstant(data, "E"),
				requiredText(data, "s"),
				requiredBigDecimal(data, "p"),
				optionalBigDecimal(data, "ap"),
				requiredBigDecimal(data, "i"),
				optionalBigDecimal(data, "P"),
				requiredBigDecimal(data, "r"),
				optionalInstant(data, "T"),
				receivedAt
		);
	}

	private AggTradeEvent toAggTradeEvent(JsonNode data, Instant receivedAt) {
		return new AggTradeEvent(
				requiredText(data, "e"),
				requiredInstant(data, "E"),
				requiredText(data, "s"),
				requiredLong(data, "a"),
				requiredBigDecimal(data, "p"),
				requiredBigDecimal(data, "q"),
				optionalBigDecimal(data, "nq"),
				requiredLong(data, "f"),
				requiredLong(data, "l"),
				requiredInstant(data, "T"),
				requiredBoolean(data, "m"),
				receivedAt
		);
	}

	private String requiredText(JsonNode node, String fieldName) {
		JsonNode value = node.get(fieldName);
		if (value == null || value.isNull() || value.asString().isBlank()) {
			throw new IllegalArgumentException("Missing Binance payload field: " + fieldName);
		}
		return value.asString();
	}

	private long requiredLong(JsonNode node, String fieldName) {
		JsonNode value = node.get(fieldName);
		if (value == null || value.longValueOpt().isEmpty()) {
			throw new IllegalArgumentException("Missing Binance payload field: " + fieldName);
		}
		return value.longValue();
	}

	private boolean requiredBoolean(JsonNode node, String fieldName) {
		JsonNode value = node.get(fieldName);
		if (value == null || value.booleanValueOpt().isEmpty()) {
			throw new IllegalArgumentException("Missing Binance payload field: " + fieldName);
		}
		return value.booleanValue();
	}

	private Instant requiredInstant(JsonNode node, String fieldName) {
		return Instant.ofEpochMilli(requiredLong(node, fieldName));
	}

	private Instant optionalInstant(JsonNode node, String fieldName) {
		JsonNode value = node.get(fieldName);
		if (value == null || value.isNull() || value.longValueOpt().isEmpty()) {
			return null;
		}
		return Instant.ofEpochMilli(value.longValue());
	}

	private BigDecimal requiredBigDecimal(JsonNode node, String fieldName) {
		String value = requiredText(node, fieldName);
		return new BigDecimal(value);
	}

	private BigDecimal optionalBigDecimal(JsonNode node, String fieldName) {
		JsonNode value = node.get(fieldName);
		if (value == null || value.isNull() || value.asString().isBlank()) {
			return null;
		}
		return new BigDecimal(value.asString());
	}
}
