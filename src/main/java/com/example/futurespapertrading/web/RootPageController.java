package com.example.futurespapertrading.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

// React SPA 루트 진입점 컨트롤러.
//
// frontend/index.html은 개발 원본이고, Docker 빌드 중 npm run build 결과물인
// frontend/dist/index.html이 Spring Boot 정적 리소스(static/index.html)로 복사된다.
// 배포된 jar 안에서는 이 파일을 classpath:static/index.html로 읽을 수 있다.
@RestController
public class RootPageController {

    // 로컬 소스에 직접 있는 파일이 아니라, Docker/Vite 빌드 후 jar 안에 포함되는 정적 리소스다.
    private final Resource indexHtml = new ClassPathResource("static/index.html");

    // Live App 루트 주소("/")로 접속했을 때 React 앱의 첫 HTML을 응답한다.
    // 이 매핑이 없으면 /index.html은 열리더라도 "/"는 404가 날 수 있다.
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<Resource>> index() {
        if (!indexHtml.exists()) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        return Mono.just(ResponseEntity.ok()          // 이미 준비된 200 OK 응답 객체를 Mono로 감싸 WebFlux에 넘긴다.
                .contentType(MediaType.TEXT_HTML)     // 응답 본문이 HTML 문서임을 브라우저에 알려준다.
                .cacheControl(CacheControl.noCache()) // index.html은 매번 서버에 재검증하게 해서 오래된 React 진입 파일을 피한다.
                .body(indexHtml));                    // 실제 응답 본문으로 jar 안의 static/index.html 리소스를 담는다.
    }
}
