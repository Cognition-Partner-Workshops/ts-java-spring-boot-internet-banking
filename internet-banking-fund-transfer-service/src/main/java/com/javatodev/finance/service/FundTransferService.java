package com.javatodev.finance.service;

import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.FundTransfer;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.AccountResponse;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.entity.FundTransferEntity;
import com.javatodev.finance.model.mapper.FundTransferMapper;
import com.javatodev.finance.model.repository.FundTransferRepository;
import com.javatodev.finance.service.rest.client.BankingCoreFeignClient;

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

@RequiredArgsConstructor
@Service
public class FundTransferService {

    private final FundTransferRepository fundTransferRepository;
    private final BankingCoreFeignClient bankingCoreFeignClient;

    private FundTransferMapper mapper = new FundTransferMapper();

    public FundTransferResponse fundTransfer(FundTransferRequest request) {
        JwtAuthenticationToken caller = requireCustomer();
        validateRequest(request);
        precheckDebitAccount(request.getFromAccount(), request.getAmount());
        FundTransferEntity entity = new FundTransferEntity();
        entity.setFromAccount(request.getFromAccount());
        entity.setToAccount(request.getToAccount());
        entity.setAmount(request.getAmount());
        entity.setOwnerId(caller.getToken().getSubject());
        entity.setCreatedBy(caller.getToken().getSubject());
        entity.setStatus(TransactionStatus.PENDING);
        FundTransferEntity optFundTransfer = fundTransferRepository.save(entity);

        FundTransferRequest coreRequest = new FundTransferRequest();
        coreRequest.setFromAccount(request.getFromAccount());
        coreRequest.setToAccount(request.getToAccount());
        coreRequest.setAmount(request.getAmount());
        FundTransferResponse fundTransferResponse;
        try {
            fundTransferResponse = bankingCoreFeignClient.fundTransfer(coreRequest);
        } catch (RuntimeException exception) {
            if (exception instanceof FeignException feignException) {
                if (feignException.status() >= 400 && feignException.status() < 500) {
                    optFundTransfer.setStatus(TransactionStatus.FAILED);
                    fundTransferRepository.save(optFundTransfer);
                }
                throw accessDeniedIfUnauthorized(feignException);
            }
            throw exception;
        }
        optFundTransfer.setTransactionReference(fundTransferResponse.getTransactionId());
        optFundTransfer.setStatus(TransactionStatus.SUCCESS);
        fundTransferRepository.save(optFundTransfer);

        fundTransferResponse.setMessage("Fund Transfer Successfully Completed");
        return fundTransferResponse;
    }

    public List<FundTransfer> readAllTransfers(Pageable pageable) {
        String ownerId = currentSubject();
        return mapper.convertToDtoList(fundTransferRepository.findAllByOwnerId(ownerId, pageable).getContent());
    }

    private void validateRequest(FundTransferRequest request) {
        if (request == null || request.getFromAccount() == null || request.getFromAccount().isBlank()
            || request.getToAccount() == null || request.getToAccount().isBlank()) {
            throw new IllegalArgumentException("Both account numbers are required");
        }
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amount must be positive and have no more than two decimal places");
        }
        if (request.getFromAccount().equals(request.getToAccount())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }
    }

    private void precheckDebitAccount(String accountNumber, BigDecimal amount) {
        AccountResponse account;
        try {
            account = bankingCoreFeignClient.readAccount(accountNumber);
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
