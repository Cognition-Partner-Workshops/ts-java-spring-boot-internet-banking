package com.javatodev.finance.controller;

import com.javatodev.finance.model.StatementFormat;
import com.javatodev.finance.model.dto.response.StatementExportResult;
import com.javatodev.finance.service.AccountService;
import com.javatodev.finance.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Account Controller", description = "APIs for managing accounts")
@RestController
@RequestMapping(value = "/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    @Operation(summary = "Get Bank Account by Account Number", description = "Retrieve bank account details by account number")
    @GetMapping("/bank-account/{account_number}")
    public ResponseEntity getBankAccount(@PathVariable("account_number") String accountNumber) {
        log.info("Reading account by ID {}", accountNumber);
        return ResponseEntity.ok(accountService.readBankAccount(accountNumber));
    }

    @Operation(summary = "Get Utility Account by Account Name", description = "Retrieve utility account details by account name")
    @GetMapping("/util-account/{account_name}")
    public ResponseEntity getUtilityAccount(@PathVariable("account_name") String providerName) {
        log.info("Reading utitlity account by ID {}", providerName);
        return ResponseEntity.ok(accountService.readUtilityAccount(providerName));
    }

    @Operation(summary = "Export Account Statement",
        description = "Export an account's transaction history as JSON or CSV, most-recent-first, "
            + "with optional inclusive from/to date filtering. This export is not paginated.")
    @GetMapping("/{accountNumber}/statement/export")
    public ResponseEntity<?> exportStatement(
        @PathVariable("accountNumber") String accountNumber,
        @RequestParam(name = "format", defaultValue = "JSON") String format,
        @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        log.info("Exporting statement for account {} as {} (from={}, to={})", accountNumber, format, from, to);
        StatementExportResult result = transactionService.exportStatement(accountNumber, format, from, to);

        String filename = "statement-" + accountNumber + "." + result.format().name().toLowerCase(Locale.ROOT);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        if (result.format() == StatementFormat.CSV) {
            return response.contentType(MediaType.parseMediaType("text/csv")).body(result.csvBody());
        }
        return response.contentType(MediaType.APPLICATION_JSON).body(result.data());
    }

}
