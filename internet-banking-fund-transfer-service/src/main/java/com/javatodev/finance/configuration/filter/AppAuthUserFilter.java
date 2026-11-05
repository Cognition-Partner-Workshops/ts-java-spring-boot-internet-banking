package com.javatodev.finance.configuration.filter;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes the authenticated caller (validated JWT subject) for auditing. Client-supplied identity
 * headers such as X-Auth-Id are ignored.
 */
@Slf4j
public class AppAuthUserFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userAuthId = authentication != null && authentication.isAuthenticated() ? authentication.getName() : null;
        log.info("Incoming Request From {}", userAuthId);
        if (userAuthId != null && !"anonymousUser".equals(userAuthId)) {
            ApiRequestContextHolder.getContext().setAuthId(userAuthId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            ApiRequestContextHolder.clearContext();
        }
    }
}
