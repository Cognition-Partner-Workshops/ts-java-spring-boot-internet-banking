package com.javatodev.finance.configuration.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Accepts only tokens issued to one of the configured Keycloak clients (the {@code azp} claim).
 */
public class AuthorizedPartyValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_CLIENT = new OAuth2Error("invalid_token", "Token was not issued to an allowed client", null);

    private final List<String> allowedClientIds;

    public AuthorizedPartyValidator(List<String> allowedClientIds) {
        this.allowedClientIds = List.copyOf(allowedClientIds);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String authorizedParty = jwt.getClaimAsString("azp");
        if (authorizedParty != null && allowedClientIds.contains(authorizedParty)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_CLIENT);
    }
}
