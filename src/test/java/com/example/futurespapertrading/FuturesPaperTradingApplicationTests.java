package com.example.futurespapertrading;

import com.example.futurespapertrading.market.stream.BinanceFuturesRawDepthStreamer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.r2dbc.url=r2dbc:h2:mem:///futures_paper_trading_test?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.r2dbc.username=sa",
		"spring.r2dbc.password=",
		"spring.sql.init.schema-locations=classpath:schema-h2.sql"
})
class FuturesPaperTradingApplicationTests {

	@MockitoBean
	private BinanceFuturesRawDepthStreamer binanceFuturesRawDepthStreamer;

	@Test
	void contextLoads() {
	}

}
