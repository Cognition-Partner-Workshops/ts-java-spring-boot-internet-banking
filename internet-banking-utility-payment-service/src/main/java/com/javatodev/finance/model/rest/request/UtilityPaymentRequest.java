package com.javatodev.finance.model.rest.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class UtilityPaymentRequest {
    @NotNull
    private Long providerId;
    @NotNull
    @Positive
    @Digits(integer = 18, fraction = 2)
    private BigDecimal amount;
    private String referenceNumber;
    @NotBlank
    private String account;
}
