package com.example.futurespapertrading.system;

import com.example.futurespapertrading.config.PaperTradingProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버의 기본 상태와 핵심 설정값을 확인하는 운영성 API를 제공한다.
 *
 * <p>이 컨트롤러는 거래 기능이 아니라 개발 중 기준점 역할을 한다. 이후 시세, 주문, 계좌 기능을
 * 추가한 뒤에도 서버가 정상 기동되고 설정이 읽히는지 빠르게 확인할 수 있다.
 */
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

	/**
	 * 애플리케이션이 살아 있고 기본 설정이 바인딩됐는지 확인하는 health endpoint다.
	 *
	 * @return 서버 상태, 애플리케이션 이름, 기준 통화, 기본 심볼
	 */
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

	/**
	 * {@code GET /api/health}의 JSON 응답 모델이다.
	 *
	 * <p>record를 사용해 단순 조회 응답을 불변 값 객체로 표현한다.
	 */
	public record HealthResponse(
			String status,
			String application,
			String baseCurrency,
			String defaultSymbol
	) {
	}
}
