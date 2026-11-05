package com.javatodev.finance.service;

import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.UtilityPayment;
import com.javatodev.finance.model.entity.UtilityPaymentEntity;
import com.javatodev.finance.model.rest.response.AccountResponse;
import com.javatodev.finance.model.mapper.UtilityPaymentMapper;
import com.javatodev.finance.model.rest.request.UtilityPaymentRequest;
import com.javatodev.finance.model.rest.response.UtilityPaymentResponse;
import com.javatodev.finance.repository.UtilityPaymentRepository;
import com.javatodev.finance.service.rest.BankingCoreRestClient;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import feign.FeignException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UtilityPaymentService {
    private final UtilityPaymentRepository utilityPaymentRepository;
    private final BankingCoreRestClient bankingCoreRestClient;

    private UtilityPaymentMapper utilityPaymentMapper = new UtilityPaymentMapper();

    public UtilityPaymentResponse utilPayment(UtilityPaymentRequest paymentRequest) {
        JwtAuthenticationToken caller = requireCustomer();
        validateRequest(paymentRequest);
        precheckDebitAccount(paymentRequest.getAccount(), paymentRequest.getAmount());
        UtilityPaymentEntity entity = new UtilityPaymentEntity();
        entity.setProviderId(paymentRequest.getProviderId());
        entity.setAmount(paymentRequest.getAmount());
        entity.setReferenceNumber(paymentRequest.getReferenceNumber());
        entity.setAccount(paymentRequest.getAccount());
        entity.setOwnerId(caller.getToken().getSubject());
        entity.setCreatedBy(caller.getToken().getSubject());
        entity.setStatus(TransactionStatus.PENDING);
        UtilityPaymentEntity optUtilPayment = utilityPaymentRepository.save(entity);

        UtilityPaymentResponse utilityPaymentResponse;
        try {
            utilityPaymentResponse = bankingCoreRestClient.utilityPayment(paymentRequest);
        } catch (RuntimeException exception) {
            optUtilPayment.setStatus(TransactionStatus.FAILED);
            utilityPaymentRepository.save(optUtilPayment);
            if (exception instanceof FeignException feignException) {
                throw accessDeniedIfUnauthorized(feignException);
            }
            throw exception;
        }

        optUtilPayment.setStatus(TransactionStatus.SUCCESS);
        optUtilPayment.setTransactionId(utilityPaymentResponse.getTransactionId());
        utilityPaymentRepository.save(optUtilPayment);

        return UtilityPaymentResponse.builder().message("Utility Payment Successfully Processed").transactionId(utilityPaymentResponse.getTransactionId()).build();
    }

    public List<UtilityPayment> readPayments(Pageable pageable) {
        var allUtilPayments = utilityPaymentRepository.findAllByOwnerId(currentSubject(), pageable);
        return utilityPaymentMapper.convertToDtoList(allUtilPayments.getContent());
    }

    private void validateRequest(UtilityPaymentRequest request) {
        if (request == null || request.getAccount() == null || request.getAccount().isBlank()) {
            throw new IllegalArgumentException("Account number is required");
        }
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amount must be positive and have no more than two decimal places");
        }
        if (request.getProviderId() == null) {
            throw new IllegalArgumentException("Utility provider is required");
        }
    }

    private void precheckDebitAccount(String accountNumber, BigDecimal amount) {
        AccountResponse account;
        try {
            account = bankingCoreRestClient.readAccount(accountNumber);
        } catch (FeignException exception) {
            throw accessDeniedIfUnauthorized(exception);
        }
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new IllegalArgumentException("Debit account is not active");
        }
        if (account.getAvailableBalance() == null || account.getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient available balance");
        }
    }

    private JwtAuthenticationToken requireCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)
            || jwt.getToken().getSubject() == null
            || !hasRealmRole(jwt, "bank-customer")) {
            throw new AccessDeniedException("Customer authorization required");
        }
        return jwt;
    }

    private boolean hasRealmRole(JwtAuthenticationToken jwt, String role) {
        Object realmAccessClaim = jwt.getToken().getClaims().get("realm_access");
        if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
            return false;
        }
        Object rolesClaim = realmAccess.get("roles");
        return rolesClaim instanceof Collection<?> roles && roles.contains(role);
    }

    private String currentSubject() {
        return requireCustomer().getToken().getSubject();
    }

    private AccessDeniedException accessDeniedIfUnauthorized(FeignException exception) {
        if (exception.status() == 401 || exception.status() == 403) {
            return new AccessDeniedException("Core banking authorization denied", exception);
        }
        throw exception;
    }
}
