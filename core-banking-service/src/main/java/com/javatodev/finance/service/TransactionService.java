package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.TransactionHistoryDto;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
            .amount(utilityPaymentRequest.getAmount().negate()).build());

        return UtilityPaymentResponse.builder().message("Utility payment successfully completed")
            .transactionId(transactionId).build();

    }

    public Page<TransactionHistoryDto> getTransactionHistory(String accountNumber, LocalDate fromDate, LocalDate toDate,
                                                             TransactionType transactionType, Pageable pageable) {

        List<TransactionHistoryDto> filtered = transactionRepository
            .findByAccount_NumberOrderByCreatedAtDesc(accountNumber).stream()
            .filter(transaction -> transactionType == null || transactionType.equals(transaction.getTransactionType()))
            .filter(transaction -> withinRange(transaction.getCreatedAt(), fromDate, toDate))
            .map(this::toTransactionHistoryDto)
            .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        List<TransactionHistoryDto> content = start >= filtered.size()
            ? Collections.emptyList()
            : filtered.subList(start, Math.min(start + pageable.getPageSize(), filtered.size()));

        return new PageImpl<>(content, pageable, filtered.size());
    }

    private boolean withinRange(LocalDateTime createdAt, LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && (createdAt == null || createdAt.isBefore(fromDate.atStartOfDay()))) {
            return false;
        }
        if (toDate != null && (createdAt == null || createdAt.isAfter(toDate.atTime(LocalTime.MAX)))) {
            return false;
        }
        return true;
    }

    private TransactionHistoryDto toTransactionHistoryDto(TransactionEntity transaction) {
        return TransactionHistoryDto.builder()
            .id(transaction.getId())
            .amount(transaction.getAmount())
            .transactionType(transaction.getTransactionType())
            .referenceNumber(transaction.getReferenceNumber())
            .createdAt(transaction.getCreatedAt())
            .build();
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
            .account(fromBankAccountEntity).amount(amount.negate()).build());

        toBankAccountEntity.setActualBalance(toBankAccountEntity.getActualBalance().add(amount));
        toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount));
        bankAccountRepository.save(toBankAccountEntity);

        transactionRepository.save(TransactionEntity.builder().transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber(toBankAccountEntity.getNumber())
            .transactionId(transactionId)
            .account(toBankAccountEntity).amount(amount).build());

        return transactionId;

    }

}
