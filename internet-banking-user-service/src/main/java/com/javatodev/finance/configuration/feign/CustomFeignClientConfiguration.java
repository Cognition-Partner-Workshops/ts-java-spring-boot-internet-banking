package com.javatodev.finance.configuration.feign;

import feign.codec.ErrorDecoder;
import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
public class CustomFeignClientConfiguration extends FeignClientProperties.FeignClientConfiguration {

    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomFeignErrorDecoder();
    }

    @Bean
    public RequestInterceptor serviceBearerTokenInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return template -> {
            template.removeHeader(HttpHeaders.AUTHORIZATION);
            template.header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenProvider.getAccessTokenString());
        };
    }

}
