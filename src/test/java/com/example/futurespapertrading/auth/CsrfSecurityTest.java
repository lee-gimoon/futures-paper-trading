package com.example.futurespapertrading.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.example.futurespapertrading.market.stream.BinanceFuturesRawDepthStreamer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(properties = {
        "spring.r2dbc.url=r2dbc:h2:mem:///csrf_security_test?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql"
})
@AutoConfigureWebTestClient
class CsrfSecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private BinanceFuturesRawDepthStreamer binanceFuturesRawDepthStreamer;

    @MockitoBean
    private ReactiveAuthenticationManager authenticationManager;

    @BeforeEach
    void rejectLoginCredentials() {
        given(authenticationManager.authenticate(any()))
                .willReturn(Mono.error(new BadCredentialsException("잘못된 테스트 로그인 정보")));
    }

    @Test
    void csrfTokenEndpointReturnsHeaderNameAndToken() {
        webTestClient.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.headerName").isEqualTo("X-CSRF-TOKEN")
                .jsonPath("$.token").isNotEmpty();
    }

    @Test
    void loginWithoutCsrfIsForbidden() {
        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"missing@example.com","password":"wrong-password"}
                        """)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void loginWithCsrfPassesCsrfFilterAndReachesAuthentication() {
        webTestClient.mutateWith(csrf())
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"missing@example.com","password":"wrong-password"}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void authenticatedOrderWithoutCsrfIsForbidden() {
        webTestClient.mutateWith(mockUser("missing@example.com"))
                .post()
                .uri("/api/paper/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"symbol":"BTCUSDT","side":"BUY","type":"MARKET","quantity":0.001}
                        """)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void authenticatedOrderWithCsrfPassesCsrfFilter() {
        HttpStatusCode status = webTestClient
                .mutateWith(mockUser("missing@example.com"))
                .mutateWith(csrf())
                .post()
                .uri("/api/paper/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"symbol":"BTCUSDT","side":"BUY","type":"MARKET","quantity":0.001}
                        """)
                .exchange()
                .returnResult(Void.class)
                .getStatus();

        assertThat(status).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void safeGetDoesNotRequireCsrf() {
        HttpStatusCode status = webTestClient
                .mutateWith(mockUser("missing@example.com"))
                .get()
                .uri("/api/paper/account")
                .exchange()
                .returnResult(Void.class)
                .getStatus();

        assertThat(status).isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
