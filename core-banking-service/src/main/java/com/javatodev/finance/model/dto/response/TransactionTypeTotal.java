package com.javatodev.finance.model.dto.response;

import com.javatodev.finance.model.TransactionType;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class TransactionTypeTotal {

    private TransactionType transactionType;
    private BigDecimal totalAmount;
    private long transactionCount;

}
