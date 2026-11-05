package com.javatodev.finance.configuration.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The caller identity taken from the validated bearer token, never from client-supplied headers or body fields.
 */
public final class AuthenticatedCaller {

    private AuthenticatedCaller() {
    }

    public static String subject() {
        return jwtAuthentication().getToken().getSubject();
    }

    public static boolean isStaff() {
        return jwtAuthentication().getAuthorities().stream()
            .anyMatch(authority -> KeycloakJwtAuthenticationConverter.STAFF_AUTHORITY.equals(authority.getAuthority()));
    }

    private static JwtAuthenticationToken jwtAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication;
        }
        throw new AccessDeniedException("No authenticated caller");
    }
}
