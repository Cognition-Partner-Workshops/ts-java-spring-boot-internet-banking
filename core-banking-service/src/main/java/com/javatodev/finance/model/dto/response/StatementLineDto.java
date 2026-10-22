package com.javatodev.finance.model.dto.response;

import com.javatodev.finance.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Typed, stable projection of a single transaction for statement exports.
 * Deliberately decoupled from {@code TransactionEntity} so the export contract
 * does not leak the JPA entity graph. Field (component) order is the serialized
 * order and the CSV column semantics.
 */
public record StatementLineDto(
    LocalDateTime timestamp,
    String transactionId,
    TransactionType transactionType,
    BigDecimal amount,
    String referenceNumber,
    String accountNumber
) {
}
