package com.javatodev.finance.configuration.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.List;

@Configuration
public class SecurityConfiguration {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkUri;

    @Value("${app.security.allowed-client-ids:javatodev-internet-banking-api-client}")
    private List<String> allowedClientIds;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        http.addFilterAt(new PathTraversalRejectionFilter(), SecurityWebFiltersOrder.FIRST);

        http.authorizeExchange(exchanges -> exchanges
            //ALLOW USER REGISTRATION API ENDPOINT
            .pathMatchers(HttpMethod.POST, "/user/api/v1/bank-users/register").permitAll()

            //ALLOW HEALTH ENDPOINTS ONLY (exact paths)
            .pathMatchers(HttpMethod.GET, "/actuator/health", "/user/actuator/health", "/fund-transfer/actuator/health",
                "/banking-core/actuator/health", "/utility-payment/actuator/health").permitAll()

            //STAFF ONLY: registration approval and customer directory
            .pathMatchers("/user/api/v1/bank-users", "/user/api/v1/bank-users/**",
                "/banking-core/api/v1/user", "/banking-core/api/v1/user/**")
            .hasAuthority(KeycloakJwtAuthenticationConverter.STAFF_AUTHORITY)

            //CUSTOMER APIS: account ownership is enforced by the services
            .pathMatchers("/fund-transfer/api/**", "/utility-payment/api/**",
                "/banking-core/api/v1/transaction/**", "/banking-core/api/v1/account/**").authenticated()

            .anyExchange().denyAll());

        http.csrf(ServerHttpSecurity.CsrfSpec::disable);

        http.oauth2ResourceServer(oAuth2ResourceServer -> oAuth2ResourceServer.jwt(jwt -> jwt
            .jwtDecoder(jwtDecoder())
            .jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(new KeycloakJwtAuthenticationConverter()))));

        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(),
            new AuthorizedPartyValidator(allowedClientIds), new NonServiceAccountValidator()));
        return decoder;
    }
}
