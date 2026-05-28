package com.javatodev.finance.controller;

import com.javatodev.finance.model.dto.response.MonthlySummaryResponse;
import com.javatodev.finance.model.dto.response.StatementResponse;
import com.javatodev.finance.service.AccountStatementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Account Statement Controller", description = "APIs for account statements and transaction history")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/account")
public class AccountStatementController {

    private final AccountStatementService accountStatementService;

    @Operation(summary = "Get Account Statement",
        description = "Returns a paginated list of transactions for an account with optional date range and type filtering")
    @GetMapping("/{account_number}/statement")
    public ResponseEntity<StatementResponse> getAccountStatement(
        @PathVariable("account_number") String accountNumber,
        @Parameter(description = "Start date (yyyy-MM-dd)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @Parameter(description = "End date (yyyy-MM-dd)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        @Parameter(description = "Transaction type filter: FUND_TRANSFER, UTILITY_PAYMENT, or ALL")
        @RequestParam(required = false, defaultValue = "ALL") String type,
        @Parameter(description = "Page number (0-based)")
        @RequestParam(required = false, defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Page size")
        @RequestParam(required = false, defaultValue = "20") @Min(1) @Max(100) int size) {

        log.info("Fetching account statement for account {} with filters: fromDate={}, toDate={}, type={}, page={}, size={}",
            accountNumber, fromDate, toDate, type, page, size);

        StatementResponse response = accountStatementService.getAccountStatement(
            accountNumber, fromDate, toDate, type, page, size);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get Monthly Summary",
        description = "Returns monthly aggregated totals including credits, debits, net change, and balances")
    @GetMapping("/{account_number}/summary")
    public ResponseEntity<MonthlySummaryResponse> getMonthlySummary(
        @PathVariable("account_number") String accountNumber,
        @Parameter(description = "Month (1-12)", required = true)
        @RequestParam @Min(1) @Max(12) int month,
        @Parameter(description = "Year (e.g. 2025)", required = true)
        @RequestParam @Min(2000) int year) {

        log.info("Fetching monthly summary for account {} for {}/{}", accountNumber, month, year);

        MonthlySummaryResponse response = accountStatementService.getMonthlySummary(accountNumber, month, year);

        return ResponseEntity.ok(response);
    }

}
