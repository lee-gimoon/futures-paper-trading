package com.example.futurespapertrading;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 0단계 프로젝트 뼈대가 정상적으로 뜨는지 검증하는 통합 테스트다.
 *
 * <p>Spring 컨텍스트 기동과 health API 응답을 확인해, 다음 단계에서 기능을 붙이기 전
 * 최소 실행 기준이 깨지지 않았는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FuturesPaperTradingApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	/**
	 * Spring ApplicationContext가 예외 없이 생성되는지 확인한다.
	 */
	@Test
	void contextLoads() {
	}

	/**
	 * health API가 서버 상태와 설정값을 예상 JSON으로 반환하는지 검증한다.
	 */
	@Test
	void healthApiReturnsConfiguredValues() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.application").value("futures-paper-trading"))
				.andExpect(jsonPath("$.baseCurrency").value("USDT"))
				.andExpect(jsonPath("$.defaultSymbol").value("BTCUSDT"));
	}

}
