package com.javatodev.finance.model.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundTransferRequest {
    @NotBlank
    private String fromAccount;
    @NotBlank
    private String toAccount;
    @NotNull
    @Positive
    private BigDecimal amount;
}
