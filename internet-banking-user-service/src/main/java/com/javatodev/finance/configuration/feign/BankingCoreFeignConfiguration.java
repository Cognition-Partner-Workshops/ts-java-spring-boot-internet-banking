package com.javatodev.finance.configuration.feign;

import com.javatodev.finance.configuration.keycloak.ServiceAccessTokenProvider;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class BankingCoreFeignConfiguration {

    @Bean
    public RequestInterceptor bankingCoreAuthorizationInterceptor(ServiceAccessTokenProvider tokenProvider) {
        return template -> template.header("Authorization", "Bearer " + tokenProvider.getAccessToken());
    }
}
