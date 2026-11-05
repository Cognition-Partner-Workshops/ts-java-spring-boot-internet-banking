package com.javatodev.finance.exception;

import com.javatodev.finance.model.AccountNumberMask;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.context.request.WebRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Locale;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Bean
    public ErrorAttributes publicErrorAttributes() {
        return new DefaultErrorAttributes() {
            @Override
            public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
                Map<String, Object> attributes = super.getErrorAttributes(request, ErrorAttributeOptions.defaults());
                return Map.of("status", attributes.get("status"), "error", "Unable to complete request");
            }
        };
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                            HttpStatusCode status, WebRequest request) {
        return new ResponseEntity<>(Map.of("error", "Unable to complete request"), headers, status);
    }

    @ExceptionHandler(SimpleBankingGlobalException.class)
    protected ResponseEntity handleGlobalException(SimpleBankingGlobalException e, Locale locale) {
        return ResponseEntity
            .badRequest()
            .body(new ErrorResponse(AccountNumberMask.redact(e.getCode()), AccountNumberMask.redact(e.getMessage())));
    }

    @ExceptionHandler({Exception.class})
    protected ResponseEntity handleException(Exception e, Locale locale) {
        return ResponseEntity
            .badRequest()
            .body("Unable to complete request");
    }
}
