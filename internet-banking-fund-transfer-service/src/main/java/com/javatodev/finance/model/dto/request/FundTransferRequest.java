package com.javatodev.finance.model.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class FundTransferRequest {
    @NotBlank
    private String fromAccount;
    @NotBlank
    private String toAccount;
    @NotNull
    @Positive
    @Digits(integer = 18, fraction = 2)
    private BigDecimal amount;
    private String authID;
}
