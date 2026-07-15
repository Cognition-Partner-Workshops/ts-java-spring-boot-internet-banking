package com.javatodev.finance.exception;

public class InvalidRequestException extends SimpleBankingGlobalException {
    public InvalidRequestException(String message) {
        super(message, GlobalErrorCode.INVALID_REQUEST);
    }
}
