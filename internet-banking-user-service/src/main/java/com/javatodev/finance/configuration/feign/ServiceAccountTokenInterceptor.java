package com.javatodev.finance.configuration.feign;

import com.javatodev.finance.configuration.keycloak.KeycloakProperties;

import org.springframework.http.HttpHeaders;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;

/**
 * Authenticates user-service to core banking with its own Keycloak client-credentials token
 * (registration runs before the end user has a token).
 */
@RequiredArgsConstructor
public class ServiceAccountTokenInterceptor implements RequestInterceptor {

    private final KeycloakProperties keycloakProperties;

    @Override
    public void apply(RequestTemplate template) {
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + keycloakProperties.getInstance().tokenManager().getAccessTokenString());
    }
}
