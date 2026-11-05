package com.javatodev.finance.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GatewayConfigurationTest {

    @Test
    void replacesSpoofedHeadersWithAuthenticatedPrincipal() {
        ServerWebExchange exchange = spoofedExchange().mutate()
            .principal(Mono.just((Principal) () -> "jwt-subject"))
            .build();

        assertForwardedAuthId(exchange, "jwt-subject");
    }

    @Test
    void replacesSpoofedHeadersWithServerDefaultWithoutPrincipal() {
        assertForwardedAuthId(spoofedExchange(), "SYSTEM USER");
    }

    @Test
    void replacesSpoofedHeadersWhenRequestHeadersAreDefensiveCopies() {
        MockServerWebExchange original = spoofedExchange();
        ServerHttpRequest request = new ServerHttpRequestDecorator(original.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders copy = new HttpHeaders();
                copy.putAll(super.getHeaders());
                return HttpHeaders.readOnlyHttpHeaders(copy);
            }
        };
        ServerWebExchange exchange = original.mutate()
            .request(request)
            .principal(Mono.just((Principal) () -> "jwt-subject"))
            .build();

        assertForwardedAuthId(exchange, "jwt-subject");
    }

    private MockServerWebExchange spoofedExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/")
            .header("X-Auth-Id", "spoofed-user", "another-spoofed-user"));
    }

    private void assertForwardedAuthId(ServerWebExchange exchange, String expected) {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = filteredExchange -> {
            forwarded.set(filteredExchange);
            return Mono.empty();
        };

        new GatewayConfiguration().customGlobalFilter().filter(exchange, chain).block();

        assertNotNull(forwarded.get());
        assertEquals(List.of(expected), forwarded.get().getRequest().getHeaders().get("X-Auth-Id"));
    }
}
