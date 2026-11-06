package com.javatodev.finance.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedExceptionReturnsGenericBodyWithoutExceptionDetails() {
        ResponseEntity response = handler.handleException(
            new IllegalStateException("jdbc:mysql://db-host/secret_schema"), Locale.ENGLISH);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse body = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("INTERNAL_ERROR", body.getCode());
        assertEquals("Unable to process the request", body.getMessage());
        assertFalse(body.getMessage().contains("IllegalStateException"));
        assertFalse(body.getMessage().contains("secret_schema"));
    }
}
