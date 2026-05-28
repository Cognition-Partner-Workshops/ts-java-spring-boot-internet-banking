package com.javatodev.finance.model.dto.response;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StatementResponse {

    private String accountNumber;
    private BigDecimal currentBalance;
    private int totalTransactions;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private List<TransactionHistoryDto> transactions;

}
