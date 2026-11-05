package com.javatodev.finance.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.javatodev.finance.model.AccountNumberMask;
import java.math.BigDecimal;

import lombok.Data;

@Data
public class Transaction {

    private Long id;
    private BigDecimal amount;
    private BankAccount bankAccount;
    @JsonSerialize(using = AccountNumberMask.ReferenceSerializer.class)
    private String referenceNumber;

}
