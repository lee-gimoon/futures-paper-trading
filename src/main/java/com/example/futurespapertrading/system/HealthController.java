package com.example.futurespapertrading.system;

import com.example.futurespapertrading.config.PaperTradingProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

	private final PaperTradingProperties properties;
	private final String applicationName;

	public HealthController(
			PaperTradingProperties properties,
			@Value("${spring.application.name}") String applicationName
	) {
		this.properties = properties;
		this.applicationName = applicationName;
	}

	@GetMapping("/health")
	public HealthResponse health() {
		PaperTradingProperties.App app = properties.app();

		return new HealthResponse(
				"UP",
				applicationName,
				app.baseCurrency(),
				app.defaultSymbol()
		);
	}

	public record HealthResponse(
			String status,
			String application,
			String baseCurrency,
			String defaultSymbol
	) {
	}
}
