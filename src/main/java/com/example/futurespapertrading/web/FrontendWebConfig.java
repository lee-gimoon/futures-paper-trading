package com.example.futurespapertrading.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

// 이 프로젝트는 Railway 서비스 하나로 배포하기 위해 React 빌드 결과를 Spring JAR의 정적 리소스에 포함한다.
// 따라서 Spring Boot가 React의 HTML·JavaScript·CSS 파일과 API 응답을 함께 제공한다.
// 이 설정은 React 화면 경로인 /trade와 /trade/ 직접 요청을 공통 진입점 static/index.html과 연결한다.
@Configuration
public class FrontendWebConfig {

    // Docker 빌드가 React의 dist 파일을 Spring 정적 리소스로 복사한 뒤 bootJar로 패키징한다.
    // 따라서 이 객체는 실행 중인 JAR 내부의 static/index.html을 가리킨다.
    private static final ClassPathResource FRONTEND_INDEX =
            new ClassPathResource("static/index.html");

    // 브라우저가 /trade 또는 /trade/로 직접 접속하거나 새로고침할 때 같은 React index.html을 반환하도록 경로를 등록한다.
    // 이 라우터는 두 경로만 처리하므로 /api와 /mobile 요청은 각각 기존 API 컨트롤러와 MobileWebConfig가 처리한다.
    // 반환된 RouterFunction은 Bean으로 등록되며, Spring WebFlux의 RouterFunctionMapping이 자동으로 찾아 HTTP 경로 처리에 사용한다.
    @Bean
    public RouterFunction<ServerResponse> frontendWebRoutes() {
        return RouterFunctions.route()
                .GET("/trade", request -> indexHtml())
                .GET("/trade/", request -> indexHtml())
                .build();
    }

    // /trade 전용 HTML 파일 대신 공통 진입점인 index.html을 반환하며, 리다이렉트하지 않아 현재 URL은 유지된다.
    // index.html이 로드되면 BrowserRouter가 현재 주소를 확인하고 TradingPage를 렌더링한다.
    private static Mono<ServerResponse> indexHtml() {
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .bodyValue(FRONTEND_INDEX);
    }
}

// 결론: Spring은 컴포넌트 스캔으로 이 @Configuration 클래스를 찾고,
// @Bean 메서드가 반환한 RouterFunction 객체를 빈으로 등록한다.
// Spring WebFlux는 자동 구성된 RouterFunctionMapping으로 RouterFunction 타입의 빈을 찾아 라우팅 규칙으로 사용하며,
// GET /trade 요청이 들어오면 등록된 함수를 실행해 static/index.html을 반환한다.
