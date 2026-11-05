package com.javatodev.finance;

import com.javatodev.finance.configuration.GatewayConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/test-jwks"
})
@AutoConfigureWebTestClient
@Import(GatewayAuthorizationRegressionTest.TestJwtConfiguration.class)
class GatewayAuthorizationRegressionTest {
    @Autowired WebTestClient client;

    @ParameterizedTest
    @ValueSource(strings = {"/banking-core/api/v1/user", "/user/api/v1/bank-users"})
    void customerCannotAccessAdminRoutes(String path) {
        int status = client.get().uri(path)
            .header("Authorization", "Bearer customer").exchange().returnResult(String.class).getStatus().value();
        assertEquals(403, status, "customer " + path + " expected HTTP 403 but observed " + status);
    }

    @Test
    void adminCanReachAdminRoutesAndRegistrationIsPublic() {
        int adminCore = client.get().uri("/banking-core/api/v1/user")
            .header("Authorization", "Bearer admin").exchange().returnResult(String.class).getStatus().value();
        assertFalse(adminCore == 401 || adminCore == 403, "admin core route observed HTTP " + adminCore);
        int adminUsers = client.get().uri("/user/api/v1/bank-users")
            .header("Authorization", "Bearer admin").exchange().returnResult(String.class).getStatus().value();
        assertFalse(adminUsers == 401 || adminUsers == 403, "admin user route observed HTTP " + adminUsers);

        int registration = client.post().uri("/user/api/v1/bank-users/register")
            .bodyValue(Map.of("email", "public@example.test")).exchange()
            .returnResult(String.class).getStatus().value();
        assertFalse(registration == 401 || registration == 403,
            "registration expected permitAll but observed HTTP " + registration);
    }

    @Test
    void globalFilterOverwritesForgedAuthIdWithJwtPrincipal() {
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/banking-core/api/v1/user")
                .header("X-Auth-Id", "attacker-controlled")
                .build()
        ).mutate().principal(Mono.just(new TestingAuthenticationToken("jwt-subject", "credentials"))).build();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        new GatewayConfiguration().customGlobalFilter().filter(exchange, value -> {
            forwarded.set(value);
            return Mono.empty();
        }).block();

        assertEquals("jwt-subject", forwarded.get().getRequest().getHeaders().getFirst("X-Auth-Id"),
            "forwarded X-Auth-Id observed " + forwarded.get().getRequest().getHeaders().getFirst("X-Auth-Id"));
    }

    @TestConfiguration
    static class TestJwtConfiguration {
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return token -> {
                List<String> roles = "admin".equals(token) ? List.of("bank-admin") : List.of("bank-customer");
                Instant now = Instant.now();
                return Mono.just(Jwt.withTokenValue(token).header("alg", "none").subject("gateway-sub")
                    .claim("realm_access", Map.of("roles", roles))
                    .issuedAt(now).expiresAt(now.plusSeconds(3600)).build());
            };
        }
    }
}
