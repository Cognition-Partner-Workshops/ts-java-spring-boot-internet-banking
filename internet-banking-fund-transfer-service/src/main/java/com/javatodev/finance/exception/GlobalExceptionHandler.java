package com.javatodev.finance.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Locale;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(SimpleBankingGlobalException.class)
    protected ResponseEntity handleGlobalException(SimpleBankingGlobalException e, Locale locale) {
        return ResponseEntity
            .badRequest()
            .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({Exception.class})
    protected ResponseEntity handleException(Exception e, Locale locale) {
        log.error("Unhandled exception while processing request", e);
        return ResponseEntity
            .badRequest()
            .body(new ErrorResponse("INTERNAL_ERROR", "Unable to process the request"));
    }
}
