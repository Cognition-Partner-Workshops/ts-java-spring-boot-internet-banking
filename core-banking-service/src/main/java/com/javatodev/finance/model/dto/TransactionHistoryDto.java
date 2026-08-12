package com.javatodev.finance.model.dto;

import com.javatodev.finance.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class TransactionHistoryDto {

    private Long id;
    private BigDecimal amount;
    private TransactionType type;
    private String referenceNumber;
    private LocalDateTime timestamp;

}
