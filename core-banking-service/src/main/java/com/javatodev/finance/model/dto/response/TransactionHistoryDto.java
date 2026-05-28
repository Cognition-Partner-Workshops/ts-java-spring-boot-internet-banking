package com.javatodev.finance.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionHistoryDto {

    private Long transactionId;
    private LocalDateTime date;
    private String description;
    private BigDecimal amount;
    private String type;
    private BigDecimal runningBalance;
    private String referenceNumber;

}
