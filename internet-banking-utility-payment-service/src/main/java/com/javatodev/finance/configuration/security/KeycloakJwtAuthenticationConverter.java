package com.javatodev.finance.configuration.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maps Keycloak realm roles to {@code ROLE_<role>} and the token's client ({@code azp}) to {@code CLIENT_<clientId>}.
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String STAFF_ROLE = "bank_admin";
    public static final String STAFF_AUTHORITY = "ROLE_" + STAFF_ROLE;
    public static final String CLIENT_AUTHORITY_PREFIX = "CLIENT_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
        String authorizedParty = jwt.getClaimAsString("azp");
        if (authorizedParty != null) {
            authorities.add(new SimpleGrantedAuthority(CLIENT_AUTHORITY_PREFIX + authorizedParty));
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
