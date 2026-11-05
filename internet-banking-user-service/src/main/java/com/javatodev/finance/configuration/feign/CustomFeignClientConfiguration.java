package com.javatodev.finance.configuration.feign;

import com.javatodev.finance.configuration.keycloak.KeycloakProperties;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomFeignClientConfiguration extends FeignClientProperties.FeignClientConfiguration {

    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomFeignErrorDecoder();
    }

    @Bean
    public RequestInterceptor serviceAccountTokenInterceptor(KeycloakProperties keycloakProperties) {
        return new ServiceAccountTokenInterceptor(keycloakProperties);
    }

}
