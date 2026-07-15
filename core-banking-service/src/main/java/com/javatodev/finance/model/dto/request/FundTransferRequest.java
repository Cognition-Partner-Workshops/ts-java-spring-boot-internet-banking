package com.javatodev.finance.model.dto.request;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundTransferRequest {
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
}
