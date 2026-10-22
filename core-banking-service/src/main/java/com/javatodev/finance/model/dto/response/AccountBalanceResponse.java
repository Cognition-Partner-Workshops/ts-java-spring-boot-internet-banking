package com.javatodev.finance.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class AccountBalanceResponse {

    private String accountNumber;
    private LocalDateTime asOf;
    private BigDecimal balance;

}
