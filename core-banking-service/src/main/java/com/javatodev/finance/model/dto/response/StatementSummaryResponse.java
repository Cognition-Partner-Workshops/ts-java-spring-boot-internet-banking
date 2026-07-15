package com.javatodev.finance.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class StatementSummaryResponse {

    private String accountNumber;
    private LocalDate from;
    private LocalDate to;
    private BigDecimal netTotal;
    private List<TransactionTypeTotal> totals;

}
