package com.javatodev.finance.configuration.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import java.util.List;
import java.util.Locale;

import reactor.core.publisher.Mono;

/**
 * Rejects request paths with dot segments, encoded dots/slashes/backslashes, double encoding or
 * path parameters, so a path matched by a public rule cannot be rewritten into a protected route.
 */
public class PathTraversalRejectionFilter implements WebFilter {

    private static final List<String> FORBIDDEN_SEQUENCES = List.of("..", "%2e", "%2f", "%5c", "%25", "\\", ";", "//");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String rawPath = exchange.getRequest().getURI().getRawPath();
        String path = exchange.getRequest().getPath().value();
        if (isSuspicious(rawPath) || isSuspicious(path)) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    static boolean isSuspicious(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        return FORBIDDEN_SEQUENCES.stream().anyMatch(normalized::contains);
    }
}
