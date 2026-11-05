package com.javatodev.finance.service;

import com.javatodev.finance.exception.SimpleBankingGlobalException;

import java.math.BigDecimal;

final class TransactionAmountValidator {

    private TransactionAmountValidator() {
    }

    static void requireValidAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new SimpleBankingGlobalException("INVALID_TRANSACTION", "Transaction amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new SimpleBankingGlobalException("INVALID_TRANSACTION", "Transaction amount must not have more than two decimal places");
        }
    }
}
