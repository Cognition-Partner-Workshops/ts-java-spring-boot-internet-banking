package com.javatodev.finance.exception;

import java.time.LocalDate;

public class InvalidStatementPeriodException extends SimpleBankingGlobalException {
    public InvalidStatementPeriodException(LocalDate from, LocalDate to) {
        super("Invalid statement period: 'from' (" + from + ") must not be after 'to' (" + to + ").",
            GlobalErrorCode.INVALID_STATEMENT_PERIOD);
    }
}
