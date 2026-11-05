package com.javatodev.finance.security;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for sfind-e580a5bf (any realm token accepted, no role or client check),
 * sfind-a9add981 (approval reachable without staff role) and sfind-930d44f2 (actuator rule traversal).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayAuthorizationRegressionTest {

    record BackendCall(String rawPath, Map<String, List<String>> headers) {
    }

    private static final List<BackendCall> BACKEND_CALLS = new CopyOnWriteArrayList<>();
    private static HttpServer backend;

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backend.createContext("/", exchange -> {
            BACKEND_CALLS.add(new BackendCall(exchange.getRequestURI().getRawPath(), Map.copyOf(exchange.getRequestHeaders())));
            byte[] body = "{\"backend\":\"reached\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        backend.start();
        String backendUri = "http://127.0.0.1:" + backend.getAddress().getPort();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestJwt::jwkSetUri);
        String[] prefixes = {"user", "fund-transfer", "banking-core", "utility-payment"};
        for (int i = 0; i < prefixes.length; i++) {
            String routeId = "route-" + i;
            registry.add("spring.cloud.gateway.routes[" + i + "].id", () -> routeId);
            registry.add("spring.cloud.gateway.routes[" + i + "].uri", () -> backendUri);
            String prefix = prefixes[i];
            registry.add("spring.cloud.gateway.routes[" + i + "].predicates[0]", () -> "Path=/" + prefix + "/**");
            registry.add("spring.cloud.gateway.routes[" + i + "].filters[0]", () -> "StripPrefix=1");
        }
    }

    @BeforeEach
    void reset() {
        BACKEND_CALLS.clear();
    }

    @Test
    void encodedDotSegmentsCannotBypassAuthentication() throws Exception {
        HttpResponse<String> response = send("GET", "/banking-core/actuator/%2e%2e/api/v1/user", null);
        assertEquals(400, response.statusCode(), "gateway answered " + response.statusCode() + ": " + response.body());
        assertEquals(List.of(), BACKEND_CALLS, "request was proxied to the backend without authentication");
    }

    @Test
    void dotSegmentsCannotBypassAuthentication() throws Exception {
        HttpResponse<String> response = send("GET", "/fund-transfer/actuator/../api/v1/transfer", null);
        assertEquals(400, response.statusCode(), "gateway answered " + response.statusCode() + ": " + response.body());
        assertEquals(List.of(), BACKEND_CALLS, "request was proxied to the backend without authentication");
    }

    @Test
    void customerTokenCannotApproveRegistrations() throws Exception {
        HttpResponse<String> response = send("PATCH", "/user/api/v1/bank-users/update/1", TestJwt.customer("alice@example.com"));
        assertEquals(403, response.statusCode());
        assertEquals(List.of(), BACKEND_CALLS, "approval request was proxied for a customer token");
    }

    @Test
    void serviceAccountTokenIsRejected() throws Exception {
        HttpResponse<String> response = send("GET", "/fund-transfer/api/v1/transfer", TestJwt.serviceAccount(TestJwt.CUSTOMER_CLIENT));
        assertEquals(401, response.statusCode());
        assertEquals(List.of(), BACKEND_CALLS, "request with a client-credentials token was proxied");
    }

    @Test
    void tokenIssuedToAnotherClientIsRejected() throws Exception {
        HttpResponse<String> response = send("GET", "/fund-transfer/api/v1/transfer", TestJwt.serviceAccount(TestJwt.INTERNAL_CLIENT));
        assertEquals(401, response.statusCode());
        assertEquals(List.of(), BACKEND_CALLS);
    }

    @Test
    void customerRequestIsProxiedWithVerifiedIdentityNotClientHeader() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/fund-transfer/api/v1/transfer"))
            .header("Authorization", "Bearer " + TestJwt.customer("alice@example.com"))
            .header("X-Auth-Id", "spoofed-victim-id")
            .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(1, BACKEND_CALLS.size());
        assertEquals(List.of(TestJwt.subjectOf("alice@example.com")), BACKEND_CALLS.get(0).headers().get("X-auth-id"));
    }

    @Test
    void staffCanApproveRegistrationsAndHealthIsPublic() throws Exception {
        assertEquals(200, send("PATCH", "/user/api/v1/bank-users/update/1", TestJwt.staff("ib_admin@javatodev.com")).statusCode());
        assertEquals(200, send("GET", "/fund-transfer/actuator/health", null).statusCode());
        assertEquals(200, send("POST", "/user/api/v1/bank-users/register", null).statusCode());
    }

    private HttpResponse<String> send(String method, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .method(method, "GET".equals(method) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString("{\"status\":\"APPROVED\"}"));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
