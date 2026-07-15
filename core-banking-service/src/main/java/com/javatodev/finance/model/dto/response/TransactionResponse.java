package com.javatodev.finance.model.dto.response;

import com.javatodev.finance.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class TransactionResponse {

    private Long id;
    private String transactionId;
    private String referenceNumber;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String accountNumber;
    private LocalDateTime timestamp;

}
