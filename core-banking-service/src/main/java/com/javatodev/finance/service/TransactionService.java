package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
        JwtAuthenticationToken caller = requireCustomer();
        validateAccountNumber(fundTransferRequest.getFromAccount());
        validateAccountNumber(fundTransferRequest.getToAccount());
        validateAmount(fundTransferRequest.getAmount());
        if (fundTransferRequest.getFromAccount().equals(fundTransferRequest.getToAccount())) {
            throw invalidRequest("Source and destination accounts must be different");
        }

        Map<String, BankAccountEntity> accounts = lockAccounts(
            fundTransferRequest.getFromAccount(), fundTransferRequest.getToAccount());
        BankAccountEntity debitAccount = accounts.get(fundTransferRequest.getFromAccount());
        BankAccountEntity creditAccount = accounts.get(fundTransferRequest.getToAccount());
        validateOwner(debitAccount, caller);
        validateActive(debitAccount);
        validateActive(creditAccount);
        validateAvailableBalance(debitAccount, fundTransferRequest.getAmount());

        String transactionId = persistFundTransfer(debitAccount, creditAccount, fundTransferRequest.getAmount());
        return FundTransferResponse.builder()
            .message("Transaction successfully completed")
            .transactionId(transactionId)
            .build();
    }

    public UtilityPaymentResponse utilPayment(UtilityPaymentRequest utilityPaymentRequest) {
        JwtAuthenticationToken caller = requireCustomer();
        validateAccountNumber(utilityPaymentRequest.getAccount());
        validateAmount(utilityPaymentRequest.getAmount());

        BankAccountEntity debitAccount = bankAccountRepository.findByNumberForUpdate(utilityPaymentRequest.getAccount())
            .orElseThrow(EntityNotFoundException::new);
        validateOwner(debitAccount, caller);
        validateActive(debitAccount);
        validateAvailableBalance(debitAccount, utilityPaymentRequest.getAmount());
        UtilityAccount utilityAccount = accountService.readUtilityAccount(utilityPaymentRequest.getProviderId());

        String transactionId = UUID.randomUUID().toString();
        debitAccount.setActualBalance(debitAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
        debitAccount.setAvailableBalance(debitAccount.getAvailableBalance().subtract(utilityPaymentRequest.getAmount()));
        bankAccountRepository.save(debitAccount);
        transactionRepository.save(TransactionEntity.builder()
            .transactionType(TransactionType.UTILITY_PAYMENT)
            .account(debitAccount)
            .transactionId(transactionId)
            .referenceNumber(utilityPaymentRequest.getReferenceNumber())
            .amount(utilityPaymentRequest.getAmount().negate())
            .build());

        return UtilityPaymentResponse.builder()
            .message("Utility payment successfully completed")
            .transactionId(transactionId)
            .build();
    }

    public String internalFundTransfer(
        com.javatodev.finance.model.dto.BankAccount fromBankAccount,
        com.javatodev.finance.model.dto.BankAccount toBankAccount,
        BigDecimal amount
    ) {
        JwtAuthenticationToken caller = requireCustomer();
        validateAccountNumber(fromBankAccount.getNumber());
        validateAccountNumber(toBankAccount.getNumber());
        validateAmount(amount);
        if (fromBankAccount.getNumber().equals(toBankAccount.getNumber())) {
            throw invalidRequest("Source and destination accounts must be different");
        }

        Map<String, BankAccountEntity> accounts = lockAccounts(fromBankAccount.getNumber(), toBankAccount.getNumber());
        BankAccountEntity debitAccount = accounts.get(fromBankAccount.getNumber());
        BankAccountEntity creditAccount = accounts.get(toBankAccount.getNumber());
        validateOwner(debitAccount, caller);
        validateActive(debitAccount);
        validateActive(creditAccount);
        validateAvailableBalance(debitAccount, amount);
        return persistFundTransfer(debitAccount, creditAccount, amount);
    }

    private String persistFundTransfer(BankAccountEntity debitAccount, BankAccountEntity creditAccount, BigDecimal amount) {
        String transactionId = UUID.randomUUID().toString();
        debitAccount.setActualBalance(debitAccount.getActualBalance().subtract(amount));
        debitAccount.setAvailableBalance(debitAccount.getAvailableBalance().subtract(amount));
        creditAccount.setActualBalance(creditAccount.getActualBalance().add(amount));
        creditAccount.setAvailableBalance(creditAccount.getAvailableBalance().add(amount));
        bankAccountRepository.save(debitAccount);
        bankAccountRepository.save(creditAccount);

        transactionRepository.save(TransactionEntity.builder()
            .transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber(creditAccount.getNumber())
            .transactionId(transactionId)
            .account(debitAccount)
            .amount(amount.negate())
            .build());
        transactionRepository.save(TransactionEntity.builder()
            .transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber(debitAccount.getNumber())
            .transactionId(transactionId)
            .account(creditAccount)
            .amount(amount)
            .build());
        return transactionId;
    }

    private Map<String, BankAccountEntity> lockAccounts(String... accountNumbers) {
        Map<String, BankAccountEntity> lockedAccounts = new HashMap<>();
        Arrays.stream(accountNumbers)
            .distinct()
            .sorted()
            .forEach(number -> lockedAccounts.put(number,
                bankAccountRepository.findByNumberForUpdate(number).orElseThrow(EntityNotFoundException::new)));
        return lockedAccounts;
    }

    private JwtAuthenticationToken requireCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)
            || jwt.getToken().getClaimAsString("preferred_username") == null
            || jwt.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .noneMatch("ROLE_bank-customer"::equals)) {
            throw new AccessDeniedException("Payment authorization required");
        }
        return jwt;
    }

    private void validateOwner(BankAccountEntity account, JwtAuthenticationToken caller) {
        String username = caller.getToken().getClaimAsString("preferred_username");
        if (account.getUser() == null || account.getUser().getEmail() == null
            || !account.getUser().getEmail().equalsIgnoreCase(username)) {
            throw new AccessDeniedException("Payment account ownership required");
        }
    }

    private void validateAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw invalidRequest("Account number is required");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw invalidRequest("Amount must be positive and have no more than two decimal places");
        }
    }

    private void validateActive(BankAccountEntity account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw invalidRequest("Account " + account.getNumber() + " is not active");
        }
    }

    private void validateAvailableBalance(BankAccountEntity account, BigDecimal amount) {
        if (account.getAvailableBalance() == null || account.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                "Insufficient available balance in account " + account.getNumber(),
                GlobalErrorCode.INSUFFICIENT_FUNDS);
        }
        if (account.getActualBalance() == null) {
            throw invalidRequest("Account balance is unavailable");
        }
    }

    private SimpleBankingGlobalException invalidRequest(String message) {
        return new SimpleBankingGlobalException(GlobalErrorCode.INVALID_REQUEST, message);
    }
}
