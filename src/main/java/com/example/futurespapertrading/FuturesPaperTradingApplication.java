package com.example.futurespapertrading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FuturesPaperTradingApplication {

	public static void main(String[] args) {
		System.out.println("[STEP1-boot.main] thread=" + Thread.currentThread().getName());
		SpringApplication.run(FuturesPaperTradingApplication.class, args);
	}

}
