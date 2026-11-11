package com.javatodev.finance.model.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Data;

@Data
public class UtilityPaymentRequest {

    @NotNull

    @Positive

    private Long providerId;
    @NotNull
    @Positive
    private BigDecimal amount;
    @NotBlank
    private String referenceNumber;
    @NotBlank
    private String account;

}
