package com.javatodev.finance;

import com.javatodev.finance.configuration.feign.CustomFeignClientConfiguration;
import com.javatodev.finance.configuration.keycloak.ServiceAccessTokenProvider;
import com.javatodev.finance.service.rest.BankingCoreRestClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BankingCoreRestClientAuthenticationTest.TestConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false"})
class BankingCoreRestClientAuthenticationTest {

    private static final AtomicReference<String> authorization = new AtomicReference<>();
    private static final AtomicReference<String> path = new AtomicReference<>();
    private static final HttpServer server = startServer();

    @Autowired
    private BankingCoreRestClient bankingCoreRestClient;

    @Autowired
    private UnrelatedClient unrelatedClient;

    @MockBean
    private ServiceAccessTokenProvider tokenProvider;

    @Configuration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableFeignClients(clients = {BankingCoreRestClient.class, UnrelatedClient.class})
    @Import(CustomFeignClientConfiguration.class)
    static class TestConfiguration {
    }

    @FeignClient(name = "unrelated-service")
    interface UnrelatedClient {
        @GetMapping("/unrelated")
        String read();
    }

    private static HttpServer startServer() {
        try {
            HttpServer stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            stub.createContext("/", exchange -> {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                path.set(exchange.getRequestURI().getPath());
                byte[] body = "{\"identificationNumber\":\"123456789\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            stub.start();
            return stub;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @DynamicPropertySource
    static void configureUrls(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        registry.add("spring.cloud.openfeign.client.config.core-banking-service.url", () -> url);
        registry.add("spring.cloud.openfeign.client.config.unrelated-service.url", () -> url);
    }

    @BeforeEach
    void resetCapturedRequest() {
        authorization.set(null);
        path.set(null);
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void coreBankingLookupCarriesServiceBearerToken() {
        when(tokenProvider.getAccessToken()).thenReturn("stubbed-token");

        var response = bankingCoreRestClient.readUser("123456789");

        assertThat(response.getIdentificationNumber()).isEqualTo("123456789");
        assertThat(path.get()).isEqualTo("/api/v1/user/123456789");
        assertThat(authorization.get()).isEqualTo("Bearer stubbed-token");
    }

    @Test
    void unrelatedClientDoesNotCarryServiceBearerToken() {
        unrelatedClient.read();

        assertThat(path.get()).isEqualTo("/unrelated");
        assertThat(authorization.get()).isNull();
    }

    @Test
    void tokenFailurePreventsAnonymousLookup() {
        when(tokenProvider.getAccessToken()).thenThrow(new IllegalStateException("Token unavailable"));

        assertThatThrownBy(() -> bankingCoreRestClient.readUser("123456789"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Token unavailable");
        assertThat(path.get()).isNull();
    }
}
