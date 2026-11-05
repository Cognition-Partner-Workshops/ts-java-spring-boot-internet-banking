package com.javatodev.finance.exception;

public class AccountAccessDeniedException extends SimpleBankingGlobalException {
    public AccountAccessDeniedException(String message) {
        super(GlobalErrorCode.ACCOUNT_ACCESS_DENIED, message);
    }
}
