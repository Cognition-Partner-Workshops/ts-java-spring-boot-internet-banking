package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.response.MonthlySummaryResponse;
import com.javatodev.finance.model.dto.response.StatementResponse;
import com.javatodev.finance.model.dto.response.TransactionHistoryDto;
import com.javatodev.finance.service.AccountStatementService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountStatementController.class)
class AccountStatementControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountStatementService accountStatementService;

    @Test
    void getAccountStatement_returnsOk() throws Exception {
        TransactionHistoryDto txDto = TransactionHistoryDto.builder()
            .transactionId(1L)
            .date(LocalDateTime.of(2025, 5, 15, 10, 30))
            .description("Fund transfer")
            .amount(BigDecimal.valueOf(500))
            .type("DEBIT")
            .runningBalance(BigDecimal.valueOf(99500))
            .referenceNumber("100015003001")
            .build();

        StatementResponse response = StatementResponse.builder()
            .accountNumber("100015003000")
            .currentBalance(BigDecimal.valueOf(99500))
            .totalTransactions(1)
            .page(0)
            .size(20)
            .totalElements(1)
            .totalPages(1)
            .transactions(List.of(txDto))
            .build();

        when(accountStatementService.getAccountStatement(
            eq("100015003000"), isNull(), isNull(), eq("ALL"), eq(0), eq(20)))
            .thenReturn(response);

        mockMvc.perform(get("/api/v1/account/100015003000/statement"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountNumber", is("100015003000")))
            .andExpect(jsonPath("$.totalTransactions", is(1)))
            .andExpect(jsonPath("$.transactions", hasSize(1)))
            .andExpect(jsonPath("$.transactions[0].transactionId", is(1)))
            .andExpect(jsonPath("$.transactions[0].type", is("DEBIT")));
    }

    @Test
    void getAccountStatement_withDateFilters() throws Exception {
        StatementResponse response = StatementResponse.builder()
            .accountNumber("100015003000")
            .currentBalance(BigDecimal.valueOf(95000))
            .totalTransactions(0)
            .page(0)
            .size(20)
            .totalElements(0)
            .totalPages(0)
            .transactions(Collections.emptyList())
            .build();

        when(accountStatementService.getAccountStatement(
            eq("100015003000"),
            eq(LocalDate.of(2025, 5, 1)),
            eq(LocalDate.of(2025, 5, 31)),
            eq("FUND_TRANSFER"),
            eq(0), eq(10)))
            .thenReturn(response);

        mockMvc.perform(get("/api/v1/account/100015003000/statement")
                .param("fromDate", "2025-05-01")
                .param("toDate", "2025-05-31")
                .param("type", "FUND_TRANSFER")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountNumber", is("100015003000")))
            .andExpect(jsonPath("$.totalTransactions", is(0)));
    }

    @Test
    void getAccountStatement_emptyResult() throws Exception {
        StatementResponse response = StatementResponse.builder()
            .accountNumber("100015003000")
            .currentBalance(BigDecimal.valueOf(100000))
            .totalTransactions(0)
            .page(0)
            .size(20)
            .totalElements(0)
            .totalPages(0)
            .transactions(Collections.emptyList())
            .build();

        when(accountStatementService.getAccountStatement(
            eq("100015003000"), isNull(), isNull(), eq("ALL"), eq(0), eq(20)))
            .thenReturn(response);

        mockMvc.perform(get("/api/v1/account/100015003000/statement"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalTransactions", is(0)))
            .andExpect(jsonPath("$.transactions", hasSize(0)));
    }

    @Test
    void getMonthlySummary_returnsOk() throws Exception {
        MonthlySummaryResponse response = MonthlySummaryResponse.builder()
            .accountNumber("100015003000")
            .month(5)
            .year(2025)
            .totalCredits(BigDecimal.valueOf(5000))
            .totalDebits(BigDecimal.valueOf(2000))
            .netChange(BigDecimal.valueOf(3000))
            .openingBalance(BigDecimal.valueOf(92000))
            .closingBalance(BigDecimal.valueOf(95000))
            .transactionCount(3)
            .build();

        when(accountStatementService.getMonthlySummary("100015003000", 5, 2025))
            .thenReturn(response);

        mockMvc.perform(get("/api/v1/account/100015003000/summary")
                .param("month", "5")
                .param("year", "2025"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountNumber", is("100015003000")))
            .andExpect(jsonPath("$.month", is(5)))
            .andExpect(jsonPath("$.year", is(2025)))
            .andExpect(jsonPath("$.totalCredits", is(5000)))
            .andExpect(jsonPath("$.totalDebits", is(2000)))
            .andExpect(jsonPath("$.netChange", is(3000)))
            .andExpect(jsonPath("$.transactionCount", is(3)));
    }

    @Test
    void getMonthlySummary_noTransactions() throws Exception {
        MonthlySummaryResponse response = MonthlySummaryResponse.builder()
            .accountNumber("100015003000")
            .month(1)
            .year(2025)
            .totalCredits(BigDecimal.ZERO)
            .totalDebits(BigDecimal.ZERO)
            .netChange(BigDecimal.ZERO)
            .openingBalance(BigDecimal.valueOf(100000))
            .closingBalance(BigDecimal.valueOf(100000))
            .transactionCount(0)
            .build();

        when(accountStatementService.getMonthlySummary("100015003000", 1, 2025))
            .thenReturn(response);

        mockMvc.perform(get("/api/v1/account/100015003000/summary")
                .param("month", "1")
                .param("year", "2025"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionCount", is(0)))
            .andExpect(jsonPath("$.netChange", is(0)));
    }

}
