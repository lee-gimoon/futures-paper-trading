// 역할: futures-paper-trading 백엔드의 Spring Boot 진입점이다.
// 필요: 애플리케이션 컨텍스트와 내장 웹 서버를 시작한다.
// 없으면: 서버를 main 메서드로 실행할 수 없다.
package com.example.futurespapertrading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FuturesPaperTradingApplication {

	public static void main(String[] args) {
		SpringApplication.run(FuturesPaperTradingApplication.class, args);
	}

}
