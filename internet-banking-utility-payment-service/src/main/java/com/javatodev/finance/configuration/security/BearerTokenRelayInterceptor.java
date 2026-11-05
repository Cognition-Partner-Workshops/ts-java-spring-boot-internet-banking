package com.javatodev.finance.configuration.security;

import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Forwards the caller's bearer token to core banking so it can enforce account ownership itself.
 */
public class BearerTokenRelayInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.startsWith("Bearer ")) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
    }
}
