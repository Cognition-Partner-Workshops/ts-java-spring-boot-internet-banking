package com.javatodev.finance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Locale;

import feign.FeignException;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(SimpleBankingGlobalException.class)
    protected ResponseEntity handleGlobalException(SimpleBankingGlobalException e, Locale locale) {
        return ResponseEntity
            .badRequest()
            .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({FeignException.Forbidden.class, AccessDeniedException.class})
    protected ResponseEntity handleForbidden(Exception e, Locale locale) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("ACCOUNT_ACCESS_DENIED", "Caller is not allowed to use this account"));
    }

    @ExceptionHandler(FeignException.Unauthorized.class)
    protected ResponseEntity handleUnauthorized(Exception e, Locale locale) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("UNAUTHORIZED", "Caller is not authenticated"));
    }

    @ExceptionHandler({Exception.class})
    protected ResponseEntity handleException(Exception e, Locale locale) {
        return ResponseEntity
            .badRequest()
            .body("Exception occur inside API " + e);
    }
}
