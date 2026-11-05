package com.javatodev.finance.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.javatodev.finance.model.AccountNumberMask;
import java.math.BigDecimal;

import lombok.Data;

@Data
public class FundTransfer extends AuditAware {
    private Long id;
    private String transactionReference;
    private String status;
    @JsonSerialize(using = AccountNumberMask.class)
    private String fromAccount;
    @JsonSerialize(using = AccountNumberMask.class)
    private String toAccount;
    private BigDecimal amount;
}
