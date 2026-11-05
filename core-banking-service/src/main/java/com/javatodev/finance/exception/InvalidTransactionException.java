package com.javatodev.finance.exception;

public class InvalidTransactionException extends SimpleBankingGlobalException {
    public InvalidTransactionException(String message) {
        super(GlobalErrorCode.INVALID_TRANSACTION, message);
    }
}
