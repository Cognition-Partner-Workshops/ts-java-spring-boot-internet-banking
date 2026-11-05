package com.javatodev.finance.service;

import com.javatodev.finance.exception.AccountAccessDeniedException;
import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.repository.BankAccountRepository;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Customer ownership check for money movement. The caller is the Keycloak user of the validated
 * bearer token; their {@code preferred_username} (the registration email, not editable in the realm)
 * must match the email of the core banking user that owns the account. Staff roles do not bypass it.
 */
@Service
@RequiredArgsConstructor
public class AccountOwnershipService {

    private final BankAccountRepository bankAccountRepository;

    public BankAccountEntity requireCallerOwnsAccount(String accountNumber) {
        String callerUsername = currentCallerUsername();
        BankAccountEntity account = accountNumber == null ? null : bankAccountRepository.findByNumber(accountNumber).orElse(null);
        if (callerUsername == null || account == null || account.getUser() == null
            || account.getUser().getEmail() == null || !account.getUser().getEmail().equalsIgnoreCase(callerUsername)) {
            throw new AccountAccessDeniedException("Caller is not allowed to use account " + accountNumber);
        }
        return account;
    }

    public BankAccountEntity requireCallerOwnsActiveAccount(String accountNumber) {
        BankAccountEntity account = requireCallerOwnsAccount(accountNumber);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountAccessDeniedException("Account " + accountNumber + " is not active");
        }
        return account;
    }

    private String currentCallerUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getClaimAsString("preferred_username");
        }
        return null;
    }
}
