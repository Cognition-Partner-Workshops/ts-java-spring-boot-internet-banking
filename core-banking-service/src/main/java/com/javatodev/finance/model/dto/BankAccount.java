package com.javatodev.finance.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.javatodev.finance.model.AccountNumberMask;
import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.AccountType;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class BankAccount {

    private Long id;
    @JsonSerialize(using = AccountNumberMask.class)
    private String number;
    private AccountType type;
    private AccountStatus status;
    private BigDecimal availableBalance;
    private BigDecimal actualBalance;
    private User user;

}
