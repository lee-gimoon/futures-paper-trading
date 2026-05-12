package com.example.futurespapertrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code application.yaml}의 {@code paper-trading.*} 설정을 Java 코드에서 쓰기 위한 바인딩 객체다.
 *
 * <p>0단계에서는 기준 통화와 기본 심볼만 관리한다. 이후 Binance 접속 정보, 리스크 정책,
 * 주문 정책 같은 설정이 늘어나면 이 패키지에 별도 Properties 클래스를 추가한다.
 */
@ConfigurationProperties(prefix = "paper-trading")
public record PaperTradingProperties(App app) {

	/**
	 * 설정 파일에서 {@code paper-trading.app} 블록이 빠져도 애플리케이션이 기본값으로 뜨도록 보정한다.
	 */
	public PaperTradingProperties {
		if (app == null) {
			app = new App(null, null);
		}
	}

	/**
	 * 모의 거래소 애플리케이션 전체에서 사용하는 기본 동작 값을 담는다.
	 *
	 * @param baseCurrency 정산과 잔고 표시의 기준 통화
	 * @param defaultSymbol 처음 시세 수신과 테스트에 사용할 기본 선물 심볼
	 */
	public record App(String baseCurrency, String defaultSymbol) {

		/**
		 * 설정값이 비어 있을 때 0단계의 기본 실행값으로 보정한다.
		 */
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
