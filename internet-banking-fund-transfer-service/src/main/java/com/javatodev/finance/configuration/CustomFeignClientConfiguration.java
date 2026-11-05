package com.javatodev.finance.configuration;

import com.javatodev.finance.configuration.security.BearerTokenRelayInterceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Logger;
import feign.RequestInterceptor;

@Configuration
public class CustomFeignClientConfiguration {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    RequestInterceptor bearerTokenRelayInterceptor() {
        return new BearerTokenRelayInterceptor();
    }

}
