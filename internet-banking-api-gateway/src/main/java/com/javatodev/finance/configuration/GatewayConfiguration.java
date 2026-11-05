package com.javatodev.finance.configuration;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Principal;

import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfiguration {

    private static final String HTTP_HEADER_AUTH_USER_ID = "X-Auth-Id";
    private static final String UNAUTHORIZED_USER_NAME = "SYSTEM USER";

    @Bean
    public GlobalFilter customGlobalFilter() {
        return (exchange, chain) -> exchange.getPrincipal().map(Principal::getName).defaultIfEmpty(UNAUTHORIZED_USER_NAME).map(principal -> {
            // adds header to proxied request
            var request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HTTP_HEADER_AUTH_USER_ID);
                    headers.set(HTTP_HEADER_AUTH_USER_ID, principal);
                })
                .build();
            return exchange.mutate().request(request).build();
        }).flatMap(chain::filter).then(Mono.fromRunnable(() -> {

        }));
    }

}
