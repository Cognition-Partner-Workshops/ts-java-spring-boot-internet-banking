package com.javatodev.finance;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.cloud.gateway.discovery.locator.enabled=false"
})
@AutoConfigureWebTestClient
class GatewayRealJwkAuthorizationRegressionTest {
    private static final String JWKS_PATH = "/jwks";
    private static final String KEY_ID = "gateway-test-rsa-key";
    private static final RSAKey SIGNING_KEY = generateSigningKey();
    private static final AtomicInteger DOWNSTREAM_REQUESTS = new AtomicInteger();
    private static HttpServer jwksServer;
    private static HttpServer downstreamServer;

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        startServers();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://127.0.0.1:" + jwksServer.getAddress().getPort() + JWKS_PATH);
        addRoute(registry, 0, "core", "/banking-core/**");
        addRoute(registry, 1, "user", "/user/**");
        addRoute(registry, 2, "fund-transfer", "/fund-transfer/**");
    }

    @BeforeEach
    void resetDownstreamRequests() {
        DOWNSTREAM_REQUESTS.set(0);
    }

    @AfterAll
    static void stopServers() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
        if (downstreamServer != null) {
            downstreamServer.stop(0);
        }
    }

    @ParameterizedTest
    @MethodSource("restrictedRoutes")
    void customersAndReadOnlyTokensCannotReachAdminRoutes(String roleName, String path, List<String> roles) {
        int status = get(path, roles);
        assertEquals(403, status,
            roleName + " " + path + " expected HTTP 403 but observed HTTP " + status);
        assertEquals(0, DOWNSTREAM_REQUESTS.get(),
            roleName + " " + path + " reached downstream " + DOWNSTREAM_REQUESTS.get() + " time(s)");
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> restrictedRoutes() {
        return Stream.of("/banking-core/api/v1/user", "/user/api/v1/bank-users")
            .flatMap(path -> Stream.of(
                arguments("customer", path, List.of("bank-customer")),
                arguments("read-only", path, List.of())
            ));
    }

    @Test
    void bankAdminCanReachCoreAndUserAdminRoutes() {
        for (String path : List.of("/banking-core/api/v1/user", "/user/api/v1/bank-users")) {
            DOWNSTREAM_REQUESTS.set(0);
            int status = get(path, List.of("bank-admin"));
            assertEquals(200, status, "bank-admin " + path + " observed HTTP " + status);
            assertEquals(1, DOWNSTREAM_REQUESTS.get(),
                "bank-admin " + path + " downstream request count " + DOWNSTREAM_REQUESTS.get());
        }
    }

    @Test
    void customerCanReachFundTransferRoute() {
        int status = client.post().uri("/fund-transfer/api/v1/transfer")
            .header("Authorization", bearerToken(List.of("bank-customer")))
            .bodyValue("{}")
            .exchange()
            .returnResult(String.class)
            .getStatus()
            .value();
        assertEquals(200, status, "bank-customer fund-transfer route observed HTTP " + status);
        assertEquals(1, DOWNSTREAM_REQUESTS.get(), "fund-transfer downstream request count " + DOWNSTREAM_REQUESTS.get());
    }

    @Test
    void registrationRemainsPublic() {
        int status = client.post().uri("/user/api/v1/bank-users/register")
            .bodyValue("{}")
            .exchange()
            .returnResult(String.class)
            .getStatus()
            .value();
        assertEquals(200, status, "public registration route observed HTTP " + status);
        assertEquals(1, DOWNSTREAM_REQUESTS.get(), "registration downstream request count " + DOWNSTREAM_REQUESTS.get());
    }

    private int get(String path, List<String> roles) {
        return client.get().uri(path)
            .header("Authorization", bearerToken(roles))
            .exchange()
            .returnResult(String.class)
            .getStatus()
            .value();
    }

    private static String bearerToken(List<String> roles) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject("gateway-synthetic-subject")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("realm_access", Map.of("roles", roles))
            .build();
        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).type(JOSEObjectType.JWT).build(),
            claims);
        try {
            jwt.sign(new RSASSASigner(SIGNING_KEY));
        } catch (JOSEException e) {
            throw new IllegalStateException("could not sign synthetic gateway JWT", e);
        }
        return "Bearer " + jwt.serialize();
    }

    private static RSAKey generateSigningKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        } catch (JOSEException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void addRoute(DynamicPropertyRegistry registry, int index, String id, String path) {
        String prefix = "spring.cloud.gateway.routes[" + index + "].";
        registry.add(prefix + "id", () -> id);
        registry.add(prefix + "uri",
            () -> "http://127.0.0.1:" + downstreamServer.getAddress().getPort());
        registry.add(prefix + "predicates[0]", () -> "Path=" + path);
        registry.add(prefix + "filters[0]", () -> "StripPrefix=1");
    }

    private static synchronized void startServers() {
        if (jwksServer != null) {
            return;
        }
        try {
            jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] jwks = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            jwksServer.createContext(JWKS_PATH, exchange -> respond(exchange, jwks, "application/json"));
            jwksServer.start();

            downstreamServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            downstreamServer.createContext("/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                DOWNSTREAM_REQUESTS.incrementAndGet();
                respond(exchange, "{\"stub\":true}".getBytes(StandardCharsets.UTF_8), "application/json");
            });
            downstreamServer.start();
        } catch (IOException e) {
            throw new IllegalStateException("could not start local JWKS and downstream test servers", e);
        }
    }

    private static void respond(HttpExchange exchange, byte[] body, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(body);
        }
    }
}
