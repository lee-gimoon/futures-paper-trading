package com.example.futurespapertrading.web;

import java.net.URI;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Expo 웹 빌드를 기존 Spring 서비스의 /mobile 경로에서 제공한다.
 *
 * 정적 JavaScript와 이미지 파일은 Spring Boot의 기본 정적 리소스 처리가 담당하고,
 * Expo Router의 화면 경로는 새로고침해도 앱이 시작되도록 모두 index.html로 연결한다.
 */
@Configuration
public class MobileWebConfig {

    private static final ClassPathResource MOBILE_INDEX =
            new ClassPathResource("static/mobile/index.html");

    private static final Set<String> MOBILE_APP_ROUTES = Set.of(
            "/mobile/",
            "/mobile/market",
            "/mobile/trade",
            "/mobile/orders",
            "/mobile/account",
            "/mobile/chart",
            "/mobile/login",
            "/mobile/signup"
    );

    @Bean
    public RouterFunction<ServerResponse> mobileWebRoutes() {
        return RouterFunctions.route()
                .GET("/mobile", request ->
                        ServerResponse.permanentRedirect(URI.create("/mobile/")).build())
                .route(
                        request -> request.method() == HttpMethod.GET
                                && MOBILE_APP_ROUTES.contains(request.path()),
                        request -> ServerResponse.ok()
                                .contentType(MediaType.TEXT_HTML)
                                .bodyValue(MOBILE_INDEX)
                )
                .build();
    }
}
