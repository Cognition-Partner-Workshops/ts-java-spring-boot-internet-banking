package com.javatodev.finance.exception;

public class ValidationException extends SimpleBankingGlobalException {
    public ValidationException(String message) {
        super(message, GlobalErrorCode.VALIDATION_ERROR);
    }
}
