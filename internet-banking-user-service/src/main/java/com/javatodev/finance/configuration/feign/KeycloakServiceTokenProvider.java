package com.javatodev.finance.configuration.feign;

import com.javatodev.finance.configuration.keycloak.KeycloakProperties;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KeycloakServiceTokenProvider implements ServiceTokenProvider {

    private final KeycloakProperties keycloakProperties;

    @Override
    public String getAccessTokenString() {
        return keycloakProperties.getInstance().tokenManager().getAccessTokenString();
    }
}
