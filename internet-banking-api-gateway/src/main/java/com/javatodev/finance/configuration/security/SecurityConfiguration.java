package com.javatodev.finance.configuration.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        ServerHttpSecurity httpSecurity = http
            .authorizeExchange(exchanges -> {
                exchanges.pathMatchers("/user/api/v1/bank-users/register").permitAll();
                exchanges.pathMatchers("/actuator/**").permitAll()
                    .pathMatchers("/user/actuator/**").permitAll()
                    .pathMatchers("/fund-transfer/actuator/**").permitAll()
                    .pathMatchers("/banking-core/actuator/**").permitAll()
                    .pathMatchers("/utility-payment/actuator/**").permitAll()
                    .pathMatchers("/banking-core/api/v1/transaction/**").hasRole("bank-customer")
                    .pathMatchers("/banking-core/api/v1/user/*").hasAnyRole("banking-service", "bank-admin")
                    .pathMatchers("/banking-core/api/v1/user").hasRole("bank-admin")
                    .pathMatchers("/banking-core/api/v1/user/**").hasRole("bank-admin")
                    .pathMatchers("/banking-core/api/v1/account/bank-account/**")
                        .hasAnyRole("bank-customer", "bank-admin")
                    .pathMatchers("/banking-core/api/v1/account/util-account/**")
                        .hasAnyRole("bank-customer", "bank-admin")
                    .pathMatchers("/fund-transfer/api/v1/transfer/**").hasRole("bank-customer")
                    .pathMatchers("/utility-payment/api/v1/utility-payment/**").hasRole("bank-customer")
                    .pathMatchers("/user/api/v1/bank-users/**").hasRole("bank-admin")
                    .pathMatchers("/banking-core/**").hasRole("bank-admin")
                    .anyExchange().authenticated();
            });

        httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);

        httpSecurity.oauth2ResourceServer(oAuth2ResourceServer -> oAuth2ResourceServer
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return httpSecurity.build();

    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        return jwt -> Mono.just(new JwtAuthenticationToken(jwt, realmAuthorities(jwt)));
    }

    private Collection<GrantedAuthority> realmAuthorities(Jwt jwt) {
        Object realmAccessClaim = jwt.getClaims().get("realm_access");
        if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
            return Collections.emptyList();
        }
        Object rolesClaim = realmAccess.get("roles");
        if (!(rolesClaim instanceof Collection<?> roles)) {
            return Collections.emptyList();
        }
        return roles.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toList());
    }

}
