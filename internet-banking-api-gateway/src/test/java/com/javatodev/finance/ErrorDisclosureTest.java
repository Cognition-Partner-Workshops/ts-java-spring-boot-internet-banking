package com.javatodev.finance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ErrorDisclosureTest {
    @Autowired ErrorAttributes errors;

    @Test
    void gatewayErrorsDoNotExposeAccountPathsOrExceptionDetails() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/banking-core/account/001234567890"));
        errors.storeErrorInformation(new IllegalStateException("account 001234567890"), exchange);
        var request = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());
        var attributes = errors.getErrorAttributes(request,
            ErrorAttributeOptions.of(ErrorAttributeOptions.Include.MESSAGE, ErrorAttributeOptions.Include.STACK_TRACE));
        assertFalse(attributes.toString().contains("001234567890"), attributes.toString());
        assertEquals(500, attributes.get("status"));
    }
}
