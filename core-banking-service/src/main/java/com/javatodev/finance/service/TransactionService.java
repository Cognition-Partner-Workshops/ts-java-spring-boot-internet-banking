package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.exception.InvalidTransactionException;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final AccountOwnershipService accountOwnershipService;

    public FundTransferResponse fundTransfer(FundTransferRequest fundTransferRequest) {

        validateAmount(fundTransferRequest.getAmount());
        if (fundTransferRequest.getFromAccount() == null
            || fundTransferRequest.getFromAccount().equals(fundTransferRequest.getToAccount())) {
            throw new InvalidTransactionException("Source and destination accounts must be different");
        }
        accountOwnershipService.requireCallerOwnsActiveAccount(fundTransferRequest.getFromAccount());

        BankAccount fromBankAccount = accountService.readBankAccount(fundTransferRequest.getFromAccount());
        BankAccount toBankAccount = accountService.readBankAccount(fundTransferRequest.getToAccount());

        //validating account balances
        validateBalance(fromBankAccount, fundTransferRequest.getAmount());

        String transactionId = internalFundTransfer(fromBankAccount, toBankAccount, fundTransferRequest.getAmount());
        return FundTransferResponse.builder().message("Transaction successfully completed").transactionId(transactionId).build();

    }

    public UtilityPaymentResponse utilPayment(UtilityPaymentRequest utilityPaymentRequest) {

        validateAmount(utilityPaymentRequest.getAmount());
        accountOwnershipService.requireCallerOwnsActiveAccount(utilityPaymentRequest.getAccount());

        String transactionId = UUID.randomUUID().toString();

        BankAccount fromBankAccount = accountService.readBankAccount(utilityPaymentRequest.getAccount());

        //validating account balances
        validateBalance(fromBankAccount, utilityPaymentRequest.getAmount());

        UtilityAccount utilityAccount = accountService.readUtilityAccount(utilityPaymentRequest.getProviderId());

        BankAccountEntity fromAccount = bankAccountRepository.findByNumberForUpdate(fromBankAccount.getNumber()).orElseThrow(EntityNotFoundException::new);
        validateBalance(fromAccount, utilityPaymentRequest.getAmount());

        //we can call third party API to process UTIL payment from payment provider from here.

        fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
        fromAccount.setAvailableBalance(fromAccount.getAvailableBalance().subtract(utilityPaymentRequest.getAmount()));

        transactionRepository.save(TransactionEntity.builder().transactionType(TransactionType.UTILITY_PAYMENT)
            .account(fromAccount)
            .transactionId(transactionId)
            .referenceNumber(utilityPaymentRequest.getReferenceNumber())
            .amount(utilityPaymentRequest.getAmount().negate()).build());

        return UtilityPaymentResponse.builder().message("Utility payment successfully completed")
            .transactionId(transactionId).build();

    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidTransactionException("Transaction amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidTransactionException("Transaction amount must not have more than two decimal places");
        }
    }

    private void validateBalance(BankAccount bankAccount, BigDecimal amount) {
        if (bankAccount.getActualBalance().compareTo(BigDecimal.ZERO) < 0 || bankAccount.getActualBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in the account " + bankAccount.getNumber(), GlobalErrorCode.INSUFFICIENT_FUNDS);
        }
    }

    private void validateBalance(BankAccountEntity account, BigDecimal amount) {
        if (account.getActualBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds in the account " + account.getNumber(), GlobalErrorCode.INSUFFICIENT_FUNDS);
        }
    }

    public String internalFundTransfer(BankAccount fromBankAccount, BankAccount toBankAccount, BigDecimal amount) {

        String transactionId = UUID.randomUUID().toString();

        // Lock both rows (in a stable order to avoid deadlocks) and re-check the balance under the lock.
        boolean fromFirst = fromBankAccount.getNumber().compareTo(toBankAccount.getNumber()) <= 0;
        BankAccountEntity firstLocked = bankAccountRepository.findByNumberForUpdate(fromFirst ? fromBankAccount.getNumber() : toBankAccount.getNumber()).orElseThrow(EntityNotFoundException::new);
        BankAccountEntity secondLocked = bankAccountRepository.findByNumberForUpdate(fromFirst ? toBankAccount.getNumber() : fromBankAccount.getNumber()).orElseThrow(EntityNotFoundException::new);
        BankAccountEntity fromBankAccountEntity = fromFirst ? firstLocked : secondLocked;
        BankAccountEntity toBankAccountEntity = fromFirst ? secondLocked : firstLocked;
        validateBalance(fromBankAccountEntity, amount);

        fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
        fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getAvailableBalance().subtract(amount));
        bankAccountRepository.save(fromBankAccountEntity);

        transactionRepository.save(TransactionEntity.builder().transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber(toBankAccountEntity.getNumber())
            .transactionId(transactionId)
            .account(fromBankAccountEntity).amount(amount.negate()).build());

        toBankAccountEntity.setActualBalance(toBankAccountEntity.getActualBalance().add(amount));
        toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getAvailableBalance().add(amount));
        bankAccountRepository.save(toBankAccountEntity);

        transactionRepository.save(TransactionEntity.builder().transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber(toBankAccountEntity.getNumber())
            .transactionId(transactionId)
            .account(toBankAccountEntity).amount(amount).build());

        return transactionId;

    }

}
