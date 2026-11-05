package com.javatodev.finance.model.rest.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.javatodev.finance.model.AccountNumberMask;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AccountResponse {
    @JsonSerialize(using = AccountNumberMask.class)
    private String number;
    private BigDecimal actualBalance;
    private Integer id;
    private String type;
    private String status;
    private BigDecimal availableBalance;
}
