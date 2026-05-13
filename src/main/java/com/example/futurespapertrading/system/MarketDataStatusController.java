package com.example.futurespapertrading.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/market-data")
public class MarketDataStatusController {

	private final MarketDataStatus marketDataStatus;

	public MarketDataStatusController(MarketDataStatus marketDataStatus) {
		this.marketDataStatus = marketDataStatus;
	}

	@GetMapping("/status")
	public MarketDataStatus.Snapshot status() {
		return marketDataStatus.snapshot();
	}
}
