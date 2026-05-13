// 역할: 애플리케이션 기동과 health API의 기본 응답을 검증한다.
// 필요: 프로젝트 뼈대가 깨졌는지 작은 테스트로 바로 확인한다.
// 없으면: 설정이나 컨트롤러 변경으로 서버 기준 동작이 깨져도 늦게 발견한다.
package com.example.futurespapertrading;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "paper-trading.binance.enabled=false")
@AutoConfigureMockMvc
class FuturesPaperTradingApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void healthApiReturnsConfiguredValues() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.application").value("futures-paper-trading"))
				.andExpect(jsonPath("$.baseCurrency").value("USDT"))
				.andExpect(jsonPath("$.defaultSymbol").value("BTCUSDT"));
	}

	@Test
	void marketDataStatusApiReturnsDisabledWhenBinanceClientIsDisabled() throws Exception {
		mockMvc.perform(get("/api/system/market-data/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(false))
				.andExpect(jsonPath("$.symbol").value("BTCUSDT"))
				.andExpect(jsonPath("$.overallState").value("DISABLED"))
				.andExpect(jsonPath("$.streams.length()").value(3));
	}

}
