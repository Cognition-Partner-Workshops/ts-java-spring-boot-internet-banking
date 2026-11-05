package com.javatodev.finance.configuration.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects Keycloak client-credentials (service account) tokens at the edge; only end users call the public API.
 */
public class NonServiceAccountValidator implements OAuth2TokenValidator<Jwt> {

    private static final String SERVICE_ACCOUNT_PREFIX = "service-account-";
    private static final OAuth2Error SERVICE_ACCOUNT = new OAuth2Error("invalid_token", "Service account tokens are not accepted", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null || username.startsWith(SERVICE_ACCOUNT_PREFIX)) {
            return OAuth2TokenValidatorResult.failure(SERVICE_ACCOUNT);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
