package com.javatodev.finance.configuration;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.security.Principal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;


@Configuration
public class GatewayConfiguration {

    private static final String HTTP_HEADER_AUTH_USER_ID = "X-Auth-Id";

    @Bean
    public GlobalFilter customGlobalFilter() {
        return (exchange, chain) -> exchange.getPrincipal()
            .map(principal -> {
                String subject = principal instanceof JwtAuthenticationToken jwt
                    ? jwt.getToken().getSubject()
                    : ((Principal) principal).getName();
                return exchange.mutate().request(request -> request.headers(headers ->
                    headers.set(HTTP_HEADER_AUTH_USER_ID, subject))).build();
            })
            .defaultIfEmpty(exchange.mutate().request(request -> request.headers(headers ->
                headers.remove(HTTP_HEADER_AUTH_USER_ID))).build())
            .flatMap(chain::filter);
    }

}
