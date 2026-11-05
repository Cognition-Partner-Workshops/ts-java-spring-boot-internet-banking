package com.javatodev.finance.configuration;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Principal;

@Configuration
public class GatewayConfiguration {

    private static final String HTTP_HEADER_AUTH_USER_ID = "X-Auth-Id";
    private static final String UNAUTHORIZED_USER_NAME = "SYSTEM USER";

    @Bean
    public GlobalFilter customGlobalFilter() {
        // Replace any client-supplied X-Auth-Id with the authenticated principal (JWT subject).
        return (exchange, chain) -> exchange.getPrincipal()
            .map(Principal::getName)
            .defaultIfEmpty(UNAUTHORIZED_USER_NAME)
            .flatMap(principal -> chain.filter(exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HTTP_HEADER_AUTH_USER_ID, principal)))
                .build()));
    }
}
