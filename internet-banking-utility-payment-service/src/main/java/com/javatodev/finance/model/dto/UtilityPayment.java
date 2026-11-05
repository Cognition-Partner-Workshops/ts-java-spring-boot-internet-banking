package com.javatodev.finance.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.javatodev.finance.model.AccountNumberMask;
import com.javatodev.finance.model.TransactionStatus;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class UtilityPayment extends AuditAware {
    private Long providerId;
    private BigDecimal amount;
    @JsonSerialize(using = AccountNumberMask.ReferenceSerializer.class)
    private String referenceNumber;
    @JsonSerialize(using = AccountNumberMask.class)
    private String account;
    private TransactionStatus status;
}
