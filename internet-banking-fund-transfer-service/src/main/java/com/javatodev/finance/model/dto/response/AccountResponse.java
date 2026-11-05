package com.javatodev.finance.model.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.javatodev.finance.model.AccountNumberMask;
import java.math.BigDecimal;

import lombok.Data;

@Data
public class AccountResponse {
    @JsonSerialize(using = AccountNumberMask.class)
    private String number;
    private BigDecimal actualBalance;
    private Long id;
    private String type;
    private String status;
    private BigDecimal availableBalance;
}
