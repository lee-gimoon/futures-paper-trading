package com.example.futurespapertrading.market.client;

import com.example.futurespapertrading.config.BinanceProperties;
import com.example.futurespapertrading.market.event.AggTradeEvent;
import com.example.futurespapertrading.market.event.BookTickerEvent;
import com.example.futurespapertrading.market.event.MarkPriceEvent;
import com.example.futurespapertrading.market.event.MarketEvent;
import com.example.futurespapertrading.system.MarketDataStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class BinanceMarketDataClient implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(BinanceMarketDataClient.class);

	private final BinanceProperties properties;
	private final BinanceMarketEventMapper eventMapper;
	private final MarketDataStatus marketDataStatus;
	private final WebSocketClient webSocketClient;
	private final List<Disposable> subscriptions = new CopyOnWriteArrayList<>();
	private final AtomicBoolean running = new AtomicBoolean(false);

	public BinanceMarketDataClient(
			BinanceProperties properties,
			BinanceMarketEventMapper eventMapper,
			MarketDataStatus marketDataStatus
	) {
		this.properties = properties;
		this.eventMapper = eventMapper;
		this.marketDataStatus = marketDataStatus;
		this.webSocketClient = new ReactorNettyWebSocketClient();
	}

	@Override
	public void start() {
		List<BinanceStreamDescriptor> descriptors = BinanceStreamDescriptor.forSymbol(properties.streamSymbol());
		registerStreams(descriptors);

		if (!properties.isEnabled()) {
			log.info("[market-data] Binance WebSocket client is disabled");
			return;
		}
		if (!running.compareAndSet(false, true)) {
			return;
		}

		descriptors.forEach(descriptor -> {
			Disposable subscription = connectLoop(descriptor).subscribe(
					ignored -> {
					},
					error -> {
						if (running.get()) {
							marketDataStatus.markFailure(descriptor.streamName(), error);
							log.error("[market-data] stream loop stopped stream={}", descriptor.streamName(), error);
						}
					}
			);
			subscriptions.add(subscription);
		});
	}

	@Override
	public void stop() {
		if (!running.compareAndSet(true, false)) {
			return;
		}
		subscriptions.forEach(Disposable::dispose);
		subscriptions.clear();
		marketDataStatus.markStopped();
		log.info("[market-data] Binance WebSocket client stopped");
	}

	@Override
	public boolean isRunning() {
		return running.get();
	}

	@Override
	public boolean isAutoStartup() {
		return true;
	}

	private void registerStreams(List<BinanceStreamDescriptor> descriptors) {
		marketDataStatus.configure(properties.isEnabled(), properties.normalizedSymbol());
		MarketDataStatus.ConnectionState initialState = properties.isEnabled()
				? MarketDataStatus.ConnectionState.STOPPED
				: MarketDataStatus.ConnectionState.DISABLED;

		descriptors.forEach(descriptor -> marketDataStatus.registerStream(
				descriptor.streamName(),
				descriptor.endpoint().name(),
				descriptor.uri(properties).toString(),
				initialState
		));
	}

	private Mono<Void> connectLoop(BinanceStreamDescriptor descriptor) {
		return Mono.defer(() -> openConnection(descriptor))
				.doOnError(error -> marketDataStatus.markFailure(descriptor.streamName(), error))
				.retryWhen(Retry.fixedDelay(Long.MAX_VALUE, properties.reconnectDelay())
						.filter(error -> running.get())
						.doBeforeRetry(retrySignal -> {
							marketDataStatus.markReconnecting(descriptor.streamName());
							log.warn(
									"[market-data] reconnect scheduled stream={} attempt={} delay={} reason={}",
									descriptor.streamName(),
									retrySignal.totalRetries() + 1,
									properties.reconnectDelay(),
									retrySignal.failure().toString()
							);
						}))
				.repeatWhen(completed -> completed
						.filter(ignored -> running.get())
						.delayElements(properties.reconnectDelay())
						.doOnNext(ignored -> {
							marketDataStatus.markReconnecting(descriptor.streamName());
							log.warn(
									"[market-data] reconnect scheduled after close stream={} delay={}",
									descriptor.streamName(),
									properties.reconnectDelay()
							);
						}))
				.then();
	}

	private Mono<Void> openConnection(BinanceStreamDescriptor descriptor) {
		if (!running.get()) {
			return Mono.empty();
		}

		URI uri = descriptor.uri(properties);
		marketDataStatus.markConnecting(descriptor.streamName());
		log.info("[market-data] connecting stream={} uri={}", descriptor.streamName(), uri);

		return webSocketClient.execute(uri, session -> {
					marketDataStatus.markConnected(descriptor.streamName());
					log.info("[market-data] connected stream={}", descriptor.streamName());

					return session.receive()
							.map(WebSocketMessage::getPayloadAsText)
							.doOnNext(payload -> handlePayload(descriptor, payload))
							.then();
				})
				.doOnSuccess(ignored -> {
					if (running.get()) {
						marketDataStatus.markDisconnected(descriptor.streamName());
						log.warn("[market-data] disconnected stream={}", descriptor.streamName());
					}
				});
	}

	private void handlePayload(BinanceStreamDescriptor descriptor, String payload) {
		try {
			MarketEvent event = eventMapper.toEvent(payload);
			long eventCount = marketDataStatus.markEvent(descriptor.streamName(), event);
			if (shouldLog(eventCount)) {
				logEvent(descriptor, event, eventCount);
			}
		} catch (RuntimeException e) {
			marketDataStatus.markFailure(descriptor.streamName(), e);
			log.warn(
					"[market-data] failed to parse payload stream={} payload={}",
					descriptor.streamName(),
					payload,
					e
			);
		}
	}

	private boolean shouldLog(long eventCount) {
		return eventCount == 1 || eventCount % properties.logEveryEvents() == 0;
	}

	private void logEvent(BinanceStreamDescriptor descriptor, MarketEvent event, long eventCount) {
		if (event instanceof BookTickerEvent bookTicker) {
			log.info(
					"[market-data] stream={} count={} symbol={} bid={} ask={} eventTime={}",
					descriptor.streamName(),
					eventCount,
					bookTicker.symbol(),
					bookTicker.bidPrice(),
					bookTicker.askPrice(),
					bookTicker.eventTime()
			);
			return;
		}
		if (event instanceof MarkPriceEvent markPrice) {
			log.info(
					"[market-data] stream={} count={} symbol={} markPrice={} fundingRate={} eventTime={}",
					descriptor.streamName(),
					eventCount,
					markPrice.symbol(),
					markPrice.markPrice(),
					markPrice.fundingRate(),
					markPrice.eventTime()
			);
			return;
		}
		if (event instanceof AggTradeEvent aggTrade) {
			log.info(
					"[market-data] stream={} count={} symbol={} price={} quantity={} eventTime={}",
					descriptor.streamName(),
					eventCount,
					aggTrade.symbol(),
					aggTrade.price(),
					aggTrade.quantity(),
					aggTrade.eventTime()
			);
		}
	}
}
