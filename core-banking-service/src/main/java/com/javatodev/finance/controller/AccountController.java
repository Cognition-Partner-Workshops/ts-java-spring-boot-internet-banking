package com.javatodev.finance.controller;

import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.service.AccountService;
import com.javatodev.finance.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

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

    @Operation(summary = "Get Account Transaction History", description = "Retrieve an account's transactions most-recent-first with optional inclusive date-range and type filtering, paginated")
    @GetMapping("/{account_number}/transactions")
    public ResponseEntity getTransactionHistory(
        @PathVariable("account_number") String accountNumber,
        @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(value = "type", required = false) TransactionType type,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size) {
        log.info("Reading transaction history for account {}", accountNumber);
        return ResponseEntity.ok(transactionService.getTransactionHistory(accountNumber, from, to, type, page, size));
    }

}
