package com.javatodev.finance.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.javatodev.finance.model.AccountNumberMask;
import lombok.Data;

@Data
public class UtilityAccount {
    private Long id;
    @JsonSerialize(using = AccountNumberMask.class)
    private String number;
    private String providerName;
}
