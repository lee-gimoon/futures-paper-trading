package com.example.futurespapertrading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * futures-paper-trading 백엔드의 시작점이다.
 *
 * <p>Spring Boot 애플리케이션 컨텍스트를 띄우고, Controller와 설정 Bean을 등록해서
 * 이후 단계의 시세 수신, 주문, 체결 엔진 기능이 붙을 수 있는 서버 뼈대를 만든다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FuturesPaperTradingApplication {

	/**
	 * JVM 프로세스가 시작될 때 호출되는 진입점이다.
	 *
	 * <p>{@link SpringApplication#run(Class, String...)}을 통해 Spring Bean을 구성하고
	 * 내장 웹 서버를 실행한다.
	 */
	public static void main(String[] args) {
		SpringApplication.run(FuturesPaperTradingApplication.class, args);
	}

}
