package com.javatodev.finance.configuration.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:http://localhost:8080/realms/javatodev-internet-banking/protocol/openid-connect/certs}")
    private String jwkSetUri;

    @Value("${app.security.allowed-client-ids:javatodev-internet-banking-api-client,javatodev-internet-banking-kc-api-client}")
    private List<String> allowedClientIds;

    @Value("${app.security.internal-client-id:javatodev-internet-banking-kc-api-client}")
    private String internalClientId;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/user/*")
                .hasAnyAuthority(KeycloakJwtAuthenticationConverter.STAFF_AUTHORITY, KeycloakJwtAuthenticationConverter.CLIENT_AUTHORITY_PREFIX + internalClientId)
                .requestMatchers("/api/v1/user", "/api/v1/user/**").hasAuthority(KeycloakJwtAuthenticationConverter.STAFF_AUTHORITY)
                .requestMatchers("/api/v1/transaction/**", "/api/v1/account/**").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(jwtDecoder())
                .jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), new AuthorizedPartyValidator(allowedClientIds)));
        return decoder;
    }

}
