package com.example.futurespapertrading.system;

import com.example.futurespapertrading.market.event.MarketEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Component
public class MarketDataStatus {

	private final AtomicReference<StatusContext> context = new AtomicReference<>(
			new StatusContext(false, "BTCUSDT", Instant.now())
	);
	private final ConcurrentHashMap<String, AtomicReference<StreamStatus>> streams = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, AtomicLong> eventCounts = new ConcurrentHashMap<>();

	public void configure(boolean enabled, String symbol) {
		context.set(new StatusContext(enabled, symbol, Instant.now()));
	}

	public void registerStream(String streamName, String endpoint, String uri, ConnectionState initialState) {
		eventCounts.put(streamName, new AtomicLong());
		streams.put(streamName, new AtomicReference<>(
				new StreamStatus(
						streamName,
						endpoint,
						uri,
						initialState,
						0,
						null,
						null,
						null,
						null,
						Instant.now()
				)
		));
	}

	public void markConnecting(String streamName) {
		update(streamName, status -> status.withState(ConnectionState.CONNECTING, null));
	}

	public void markConnected(String streamName) {
		Instant now = Instant.now();
		update(streamName, status -> new StreamStatus(
				status.streamName(),
				status.endpoint(),
				status.uri(),
				ConnectionState.CONNECTED,
				status.eventCount(),
				now,
				null,
				status.lastEventAt(),
				null,
				now
		));
	}

	public long markEvent(String streamName, MarketEvent event) {
		AtomicReference<StreamStatus> reference = streams.get(streamName);
		AtomicLong eventCount = eventCounts.get(streamName);
		if (reference == null || eventCount == null) {
			return 0;
		}
		long currentEventCount = eventCount.incrementAndGet();
		StreamStatus updated = reference.updateAndGet(status -> new StreamStatus(
				status.streamName(),
				status.endpoint(),
				status.uri(),
				ConnectionState.CONNECTED,
				currentEventCount,
				status.connectedAt(),
				status.disconnectedAt(),
				event.receivedAt(),
				status.lastError(),
				Instant.now()
		));
		return updated.eventCount();
	}

	public void markReconnecting(String streamName) {
		update(streamName, status -> status.withState(ConnectionState.RECONNECTING, null));
	}

	public void markDisconnected(String streamName) {
		Instant now = Instant.now();
		update(streamName, status -> new StreamStatus(
				status.streamName(),
				status.endpoint(),
				status.uri(),
				ConnectionState.RECONNECTING,
				status.eventCount(),
				status.connectedAt(),
				now,
				status.lastEventAt(),
				status.lastError(),
				now
		));
	}

	public void markFailure(String streamName, Throwable error) {
		update(streamName, status -> status.withState(ConnectionState.DOWN, errorMessage(error)));
	}

	public void markStopped() {
		streams.values().forEach(reference ->
				reference.updateAndGet(status -> status.withState(ConnectionState.STOPPED, null))
		);
	}

	public Snapshot snapshot() {
		StatusContext currentContext = context.get();
		List<StreamStatus> streamStatuses = streams.values().stream()
				.map(AtomicReference::get)
				.sorted(Comparator.comparing(StreamStatus::streamName))
				.toList();

		return new Snapshot(
				currentContext.enabled(),
				currentContext.symbol(),
				overallState(currentContext.enabled(), streamStatuses),
				currentContext.updatedAt(),
				streamStatuses
		);
	}

	private void update(String streamName, Function<StreamStatus, StreamStatus> updater) {
		AtomicReference<StreamStatus> reference = streams.get(streamName);
		if (reference != null) {
			reference.updateAndGet(updater::apply);
		}
	}

	private ConnectionState overallState(boolean enabled, List<StreamStatus> streamStatuses) {
		if (!enabled) {
			return ConnectionState.DISABLED;
		}
		if (streamStatuses.isEmpty()) {
			return ConnectionState.STOPPED;
		}
		if (streamStatuses.stream().anyMatch(status -> status.state() == ConnectionState.CONNECTED)) {
			return ConnectionState.CONNECTED;
		}
		if (streamStatuses.stream().anyMatch(status -> status.state() == ConnectionState.RECONNECTING)) {
			return ConnectionState.RECONNECTING;
		}
		if (streamStatuses.stream().anyMatch(status -> status.state() == ConnectionState.CONNECTING)) {
			return ConnectionState.CONNECTING;
		}
		if (streamStatuses.stream().anyMatch(status -> status.state() == ConnectionState.DOWN)) {
			return ConnectionState.DOWN;
		}
		if (streamStatuses.stream().allMatch(status -> status.state() == ConnectionState.DISABLED)) {
			return ConnectionState.DISABLED;
		}
		return ConnectionState.STOPPED;
	}

	private String errorMessage(Throwable error) {
		if (error == null) {
			return null;
		}
		String message = error.getMessage();
		if (message == null || message.isBlank()) {
			return error.getClass().getSimpleName();
		}
		return error.getClass().getSimpleName() + ": " + message;
	}

	public enum ConnectionState {
		DISABLED,
		STOPPED,
		CONNECTING,
		CONNECTED,
		RECONNECTING,
		DOWN
	}

	public record Snapshot(
			boolean enabled,
			String symbol,
			ConnectionState overallState,
			Instant updatedAt,
			List<StreamStatus> streams
	) {
	}

	public record StreamStatus(
			String streamName,
			String endpoint,
			String uri,
			ConnectionState state,
			long eventCount,
			Instant connectedAt,
			Instant disconnectedAt,
			Instant lastEventAt,
			String lastError,
			Instant updatedAt
	) {

		private StreamStatus withState(ConnectionState state, String lastError) {
			return new StreamStatus(
					streamName,
					endpoint,
					uri,
					state,
					eventCount,
					connectedAt,
					disconnectedAt,
					lastEventAt,
					lastError,
					Instant.now()
			);
		}
	}

	private record StatusContext(boolean enabled, String symbol, Instant updatedAt) {
	}
}
