package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.response.MonthlySummaryResponse;
import com.javatodev.finance.model.dto.response.StatementResponse;
import com.javatodev.finance.model.dto.response.TransactionHistoryDto;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountStatementService {

    private final TransactionRepository transactionRepository;
    private final BankAccountRepository bankAccountRepository;

    public StatementResponse getAccountStatement(String accountNumber, LocalDate fromDate, LocalDate toDate,
                                                  String type, int page, int size) {

        BankAccountEntity account = bankAccountRepository.findByNumber(accountNumber)
            .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountNumber));

        TransactionType transactionType = resolveTransactionType(type);
        LocalDateTime fromDateTime = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime toDateTime = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        Page<TransactionEntity> transactionPage = transactionRepository.findByAccountAndFilters(
            accountNumber, fromDateTime, toDateTime, transactionType, PageRequest.of(page, size));

        BigDecimal currentBalance = account.getAvailableBalance();
        List<TransactionHistoryDto> transactions = new ArrayList<>();

        for (TransactionEntity entity : transactionPage.getContent()) {
            BigDecimal sumAfter = transactionRepository.sumAllTransactionsAfterDate(
                accountNumber, entity.getCreatedAt());
            BigDecimal runningBalance = currentBalance.subtract(sumAfter);
            transactions.add(mapToDto(entity, runningBalance));
        }

        return StatementResponse.builder()
            .accountNumber(accountNumber)
            .currentBalance(account.getAvailableBalance())
            .totalTransactions(transactionPage.getNumberOfElements())
            .page(page)
            .size(size)
            .totalElements(transactionPage.getTotalElements())
            .totalPages(transactionPage.getTotalPages())
            .transactions(transactions)
            .build();
    }

    public MonthlySummaryResponse getMonthlySummary(String accountNumber, int month, int year) {

        BankAccountEntity account = bankAccountRepository.findByNumber(accountNumber)
            .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountNumber));

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime startDate = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endDate = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<TransactionEntity> transactions = transactionRepository.findByAccountAndDateRange(
            accountNumber, startDate, endDate);

        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalDebits = BigDecimal.ZERO;

        for (TransactionEntity tx : transactions) {
            if (tx.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                totalCredits = totalCredits.add(tx.getAmount());
            } else {
                totalDebits = totalDebits.add(tx.getAmount().abs());
            }
        }

        BigDecimal closingBalance = account.getAvailableBalance();
        BigDecimal netChange = totalCredits.subtract(totalDebits);

        BigDecimal openingBalance = computeOpeningBalance(accountNumber, startDate, closingBalance);

        return MonthlySummaryResponse.builder()
            .accountNumber(accountNumber)
            .month(month)
            .year(year)
            .totalCredits(totalCredits)
            .totalDebits(totalDebits)
            .netChange(netChange)
            .openingBalance(openingBalance)
            .closingBalance(openingBalance.add(netChange))
            .transactionCount(transactions.size())
            .build();
    }

    private TransactionType resolveTransactionType(String type) {
        if (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type)) {
            return null;
        }
        return TransactionType.valueOf(type.toUpperCase());
    }

    private BigDecimal computeOpeningBalance(String accountNumber, LocalDateTime monthStart,
                                              BigDecimal currentBalance) {
        List<TransactionEntity> allFromMonthStart = transactionRepository.findByAccountAndDateRange(
            accountNumber, monthStart, LocalDateTime.now());

        BigDecimal totalSinceMonthStart = BigDecimal.ZERO;
        for (TransactionEntity tx : allFromMonthStart) {
            totalSinceMonthStart = totalSinceMonthStart.add(tx.getAmount());
        }

        return currentBalance.subtract(totalSinceMonthStart);
    }

    private TransactionHistoryDto mapToDto(TransactionEntity entity, BigDecimal runningBalance) {
        String txType = entity.getAmount().compareTo(BigDecimal.ZERO) >= 0 ? "CREDIT" : "DEBIT";

        return TransactionHistoryDto.builder()
            .transactionId(entity.getId())
            .date(entity.getCreatedAt())
            .description(entity.getDescription() != null ? entity.getDescription()
                : entity.getTransactionType().name())
            .amount(entity.getAmount().abs())
            .type(txType)
            .runningBalance(runningBalance)
            .referenceNumber(entity.getReferenceNumber())
            .build();
    }

}
