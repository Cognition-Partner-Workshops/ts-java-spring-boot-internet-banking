package com.javatodev.finance.service.rest;

import com.javatodev.finance.model.rest.response.UserResponse;
import com.javatodev.finance.configuration.feign.BankingCoreFeignConfiguration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "core-banking-service", configuration = BankingCoreFeignConfiguration.class)
public interface BankingCoreRestClient {

    @GetMapping("/api/v1/user/{identification}")
    UserResponse readUser(@PathVariable("identification") String identification);

}
