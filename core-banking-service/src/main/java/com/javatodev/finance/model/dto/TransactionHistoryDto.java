package com.javatodev.finance.model.dto;

import com.javatodev.finance.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionHistoryDto {

    private Long id;
    private BigDecimal amount;
    private TransactionType transactionType;
    private String referenceNumber;
    private LocalDateTime createdAt;

}
