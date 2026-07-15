package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.exception.InvalidStatementPeriodException;
import com.javatodev.finance.exception.UnsupportedExportFormatException;
import com.javatodev.finance.model.StatementFormat;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.StatementExportDto;
import com.javatodev.finance.model.dto.response.StatementExportResult;
import com.javatodev.finance.model.dto.response.StatementLineDto;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class TransactionService {

    private final AccountService accountService;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;

    public FundTransferResponse fundTransfer(FundTransferRequest fundTransferRequest) {

        BankAccount fromBankAccount = accountService.readBankAccount(fundTransferRequest.getFromAccount());
        BankAccount toBankAccount = accountService.readBankAccount(fundTransferRequest.getToAccount());

        //validating account balances
        validateBalance(fromBankAccount, fundTransferRequest.getAmount());

        String transactionId = internalFundTransfer(fromBankAccount, toBankAccount, fundTransferRequest.getAmount());
        return FundTransferResponse.builder().message("Transaction successfully completed").transactionId(transactionId).build();

    }

    public UtilityPaymentResponse utilPayment(UtilityPaymentRequest utilityPaymentRequest) {

        String transactionId = UUID.randomUUID().toString();

        BankAccount fromBankAccount = accountService.readBankAccount(utilityPaymentRequest.getAccount());

        //validating account balances
        validateBalance(fromBankAccount, utilityPaymentRequest.getAmount());

        UtilityAccount utilityAccount = accountService.readUtilityAccount(utilityPaymentRequest.getProviderId());

        BankAccountEntity fromAccount = bankAccountRepository.findByNumber(fromBankAccount.getNumber()).get();

        //we can call third party API to process UTIL payment from payment provider from here.

        fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
        fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));

        transactionRepository.save(TransactionEntity.builder().transactionType(TransactionType.UTILITY_PAYMENT)
            .account(fromAccount)
            .transactionId(transactionId)
            .referenceNumber(utilityPaymentRequest.getReferenceNumber())
            .timestamp(LocalDateTime.now())
            .amount(utilityPaymentRequest.getAmount().negate()).build());

        return UtilityPaymentResponse.builder().message("Utility payment successfully completed")
            .transactionId(transactionId).build();

    }

    private void validateBalance(BankAccount bankAccount, BigDecimal amount) {
        if (bankAccount.getActualBalance().compareTo(BigDecimal.ZERO) < 0 || bankAccount.getActualBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in the account " + bankAccount.getNumber(), GlobalErrorCode.INSUFFICIENT_FUNDS);
        }
    }

    public String internalFundTransfer(BankAccount fromBankAccount, BankAccount toBankAccount, BigDecimal amount) {

        String transactionId = UUID.randomUUID().toString();

        BankAccountEntity fromBankAccountEntity = bankAccountRepository.findByNumber(fromBankAccount.getNumber()).orElseThrow(EntityNotFoundException::new);
        BankAccountEntity toBankAccountEntity = bankAccountRepository.findByNumber(toBankAccount.getNumber()).orElseThrow(EntityNotFoundException::new);

        fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
        fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
        bankAccountRepository.save(fromBankAccountEntity);

        transactionRepository.save(TransactionEntity.builder().transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber(toBankAccountEntity.getNumber())
            .transactionId(transactionId)
            .timestamp(LocalDateTime.now())
            .account(fromBankAccountEntity).amount(amount.negate()).build());

        toBankAccountEntity.setActualBalance(toBankAccountEntity.getActualBalance().add(amount));
        toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount));
        bankAccountRepository.save(toBankAccountEntity);

        transactionRepository.save(TransactionEntity.builder().transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber(toBankAccountEntity.getNumber())
            .transactionId(transactionId)
            .timestamp(LocalDateTime.now())
            .account(toBankAccountEntity).amount(amount).build());

        return transactionId;

    }

    /**
     * Deterministic most-recent-first ordering: timestamp descending, with id
     * descending as a tie-breaker (a single fund transfer writes two rows that
     * may share a timestamp, so a tie-breaker is required for a stable order).
     */
    private static final Comparator<TransactionEntity> STATEMENT_ORDER =
        Comparator.comparing(TransactionEntity::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TransactionEntity::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    /**
     * Builds a statement export (CSV or JSON) of an account's transaction history,
     * ordered most-recent-first and optionally filtered by an inclusive date range.
     */
    public StatementExportResult exportStatement(String accountNumber, String format, LocalDate from, LocalDate to) {

        StatementFormat statementFormat = parseFormat(format);
        validatePeriod(from, to);

        // Validates the account exists (throws EntityNotFoundException otherwise).
        accountService.readBankAccount(accountNumber);

        List<StatementLineDto> lines = transactionRepository
            .findByAccount_NumberOrderByTimestampDescIdDesc(accountNumber).stream()
            .filter(transaction -> withinRange(transaction.getTimestamp(), from, to))
            .sorted(STATEMENT_ORDER)
            .map(this::toStatementLine)
            .toList();

        StatementExportDto data = new StatementExportDto(accountNumber, from, to, lines.size(), lines);

        if (statementFormat == StatementFormat.CSV) {
            return new StatementExportResult(statementFormat, data, StatementCsvRenderer.render(data));
        }
        return new StatementExportResult(statementFormat, data, null);
    }

    private StatementFormat parseFormat(String format) {
        if (format == null || format.isBlank()) {
            return StatementFormat.JSON;
        }
        try {
            return StatementFormat.valueOf(format.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnsupportedExportFormatException(format);
        }
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidStatementPeriodException(from, to);
        }
    }

    /**
     * Inclusive on both ends at day granularity. A transaction with a null
     * timestamp is excluded whenever any bound is supplied.
     */
    private boolean withinRange(LocalDateTime timestamp, LocalDate from, LocalDate to) {
        LocalDate date = timestamp == null ? null : timestamp.toLocalDate();
        if (from != null && (date == null || date.isBefore(from))) {
            return false;
        }
        if (to != null && (date == null || date.isAfter(to))) {
            return false;
        }
        return true;
    }

    private StatementLineDto toStatementLine(TransactionEntity transaction) {
        return new StatementLineDto(
            transaction.getTimestamp(),
            transaction.getTransactionId(),
            transaction.getTransactionType(),
            transaction.getAmount(),
            transaction.getReferenceNumber(),
            transaction.getAccount() != null ? transaction.getAccount().getNumber() : null);
    }

}
