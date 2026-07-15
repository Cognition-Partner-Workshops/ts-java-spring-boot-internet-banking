package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.StatementSummaryResponse;
import com.javatodev.finance.model.dto.response.TransactionTypeTotal;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

    public StatementSummaryResponse getStatementSummary(String accountNumber, LocalDate from, LocalDate to) {

        //ensures the account exists (throws EntityNotFoundException otherwise), consistent with other account reads
        accountService.readBankAccount(accountNumber);

        if (from == null || to == null || from.isAfter(to)) {
            throw new SimpleBankingGlobalException(GlobalErrorCode.INVALID_DATE_RANGE,
                "Invalid statement period: 'from' must not be after 'to'.");
        }

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(LocalTime.MAX);

        List<TransactionEntity> transactions = transactionRepository.findForStatement(accountNumber, start, end);

        //keyed by transaction type name for deterministic (alphabetical) ordering
        Map<String, TransactionType> types = new TreeMap<>();
        Map<String, BigDecimal> amountByType = new TreeMap<>();
        Map<String, Long> countByType = new TreeMap<>();

        for (TransactionEntity transaction : transactions) {
            String key = transaction.getTransactionType().name();
            types.put(key, transaction.getTransactionType());
            amountByType.merge(key, transaction.getAmount(), BigDecimal::add);
            countByType.merge(key, 1L, Long::sum);
        }

        List<TransactionTypeTotal> totals = new ArrayList<>();
        BigDecimal netTotal = BigDecimal.ZERO;
        for (Map.Entry<String, TransactionType> entry : types.entrySet()) {
            BigDecimal typeTotal = amountByType.get(entry.getKey());
            totals.add(TransactionTypeTotal.builder()
                .transactionType(entry.getValue())
                .totalAmount(typeTotal)
                .transactionCount(countByType.get(entry.getKey()))
                .build());
            netTotal = netTotal.add(typeTotal);
        }

        return StatementSummaryResponse.builder()
            .accountNumber(accountNumber)
            .from(from)
            .to(to)
            .netTotal(netTotal)
            .totals(totals)
            .build();

    }

}
