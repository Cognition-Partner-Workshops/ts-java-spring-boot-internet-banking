package com.javatodev.finance.configuration.keycloak;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServiceAccessTokenProvider {

    private final KeycloakProperties keycloakProperties;

    public String getAccessToken() {
        return keycloakProperties.getInstance().tokenManager().getAccessTokenString();
    }
}
