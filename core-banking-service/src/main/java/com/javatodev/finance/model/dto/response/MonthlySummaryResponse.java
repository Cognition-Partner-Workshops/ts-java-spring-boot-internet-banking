package com.javatodev.finance.model.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MonthlySummaryResponse {

    private String accountNumber;
    private int month;
    private int year;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private BigDecimal netChange;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private long transactionCount;

}
