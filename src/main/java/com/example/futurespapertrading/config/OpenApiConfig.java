package com.example.futurespapertrading.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI futuresPaperTradingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Futures Paper Trading API")
                        .description("선물 모의투자 서비스 API 문서")
                        .version("v1"));
    }
}
