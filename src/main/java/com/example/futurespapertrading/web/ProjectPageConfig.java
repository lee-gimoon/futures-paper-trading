package com.example.futurespapertrading.web;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class ProjectPageConfig {

    @Bean
    RouterFunction<ServerResponse> projectPageRoutes() {
        ClassPathResource projectPage = new ClassPathResource("static/project/index.html");

        return RouterFunctions.route()
                .GET("/project", request ->
                        ServerResponse.permanentRedirect(URI.create("/project/")).build())
                .GET("/project/", request ->
                        ServerResponse.ok()
                                .contentType(MediaType.TEXT_HTML)
                                .body(BodyInserters.fromResource(projectPage)))
                .build();
    }
}
