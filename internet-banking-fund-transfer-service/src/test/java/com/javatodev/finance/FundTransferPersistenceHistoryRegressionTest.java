package com.javatodev.finance;

import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.AccountResponse;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.entity.FundTransferEntity;
import com.javatodev.finance.model.repository.FundTransferRepository;
import com.javatodev.finance.service.FundTransferService;
import com.javatodev.finance.service.rest.client.BankingCoreFeignClient;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false"
})
@Import(FundTransferService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FundTransferPersistenceHistoryRegressionTest {
    private static final String OWNER_ID = "customer-subject-1";

    @Autowired
    private FundTransferService service;

    @Autowired
    private FundTransferRepository repository;

    @MockBean
    private BankingCoreFeignClient core;

    @BeforeEach
    void setUp() {
        reset(core);
        repository.deleteAll();
        replaceServiceRepository(repository);
        authenticate();
        AccountResponse account = new AccountResponse();
        account.setStatus("ACTIVE");
        account.setAvailableBalance(new BigDecimal("100.00"));
        when(core.readAccount("DEBIT-1")).thenReturn(account);
    }

    @AfterEach
    void clearSecurityContext() {
        replaceServiceRepository(repository);
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulCoreCallLeavesCommittedPendingRowWhenSuccessSaveFails() {
        when(core.fundTransfer(any())).thenReturn(successResponse());
        IllegalStateException saveFailure = new IllegalStateException("success history save failed");
        AtomicInteger saves = new AtomicInteger();
        FundTransferRepository failingRepository = mock(FundTransferRepository.class,
            AdditionalAnswers.delegatesTo(repository));
        doAnswer(invocation -> {
            if (saves.incrementAndGet() == 2) {
                throw saveFailure;
            }
            return repository.save(invocation.getArgument(0));
        }).when(failingRepository).save(any(FundTransferEntity.class));
        replaceServiceRepository(failingRepository);

        IllegalStateException observed = assertThrows(IllegalStateException.class,
            () -> service.fundTransfer(request()));
        List<FundTransferEntity> rows = repository.findAll();

        assertAll(
            () -> assertSame(saveFailure, observed),
            () -> assertEquals(2, saves.get(), "save invocation count observed " + saves.get()),
            () -> verify(core).fundTransfer(any()),
            () -> assertEquals(1, rows.size(),
                "expected committed PENDING history row after success-save failure; observed " + rows.size()),
            () -> assertEquals(TransactionStatus.PENDING, rows.isEmpty() ? null : rows.get(0).getStatus(),
                "persisted status observed " + (rows.isEmpty() ? null : rows.get(0).getStatus())),
            () -> assertEquals(OWNER_ID, rows.isEmpty() ? null : rows.get(0).getOwnerId(),
                "persisted ownerId observed " + (rows.isEmpty() ? null : rows.get(0).getOwnerId()))
        );
    }

    @Test
    void coreCallFailurePersistsFailedHistory() {
        FeignException coreFailure = badRequest();
        when(core.fundTransfer(any())).thenThrow(coreFailure);

        FeignException observed = assertThrows(FeignException.class,
            () -> service.fundTransfer(request()));
        List<FundTransferEntity> rows = repository.findAll();

        assertAll(
            () -> assertSame(coreFailure, observed),
            () -> assertEquals(1, rows.size(),
                "expected FAILED history row after core failure; observed " + rows.size()),
            () -> assertEquals(TransactionStatus.FAILED, rows.isEmpty() ? null : rows.get(0).getStatus(),
                "persisted status observed " + (rows.isEmpty() ? null : rows.get(0).getStatus())),
            () -> assertEquals(OWNER_ID, rows.isEmpty() ? null : rows.get(0).getOwnerId(),
                "persisted ownerId observed " + (rows.isEmpty() ? null : rows.get(0).getOwnerId()))
        );
    }

    @Test
    void coreTimeoutLeavesCommittedPendingHistory() {
        RetryableException timeout = new RetryableException(-1, "core transfer timed out",
            Request.HttpMethod.POST, new SocketTimeoutException("core transfer timed out"), (Long) null,
            feignRequest());
        when(core.fundTransfer(any())).thenThrow(timeout);

        RetryableException observed = assertThrows(RetryableException.class,
            () -> service.fundTransfer(request()));
        List<FundTransferEntity> rows = repository.findAll();

        assertAll(
            () -> assertSame(timeout, observed),
            () -> assertEquals(1, rows.size(),
                "expected committed history row after core timeout; observed " + rows.size()),
            () -> assertEquals(TransactionStatus.PENDING, rows.isEmpty() ? null : rows.get(0).getStatus(),
                "persisted status observed " + (rows.isEmpty() ? null : rows.get(0).getStatus())),
            () -> assertEquals(OWNER_ID, rows.isEmpty() ? null : rows.get(0).getOwnerId(),
                "persisted ownerId observed " + (rows.isEmpty() ? null : rows.get(0).getOwnerId()))
        );
    }

    private FeignException badRequest() {
        Response response = Response.builder().status(400).reason("Bad Request")
            .request(feignRequest()).headers(Map.of()).build();
        return FeignException.errorStatus("fundTransfer", response);
    }

    private Request feignRequest() {
        return Request.create(Request.HttpMethod.POST, "http://core/api/v1/transaction/fund-transfer",
            Map.of(), null, null, null);
    }

    private void authenticate() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(OWNER_ID)
            .claim("realm_access", Map.of("roles", List.of("bank-customer")))
            .issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private FundTransferRequest request() {
        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount("DEBIT-1");
        request.setToAccount("CREDIT-2");
        request.setAmount(new BigDecimal("10.00"));
        return request;
    }

    private FundTransferResponse successResponse() {
        FundTransferResponse response = new FundTransferResponse();
        response.setTransactionId("transaction-1");
        return response;
    }

    private void replaceServiceRepository(FundTransferRepository replacement) {
        Object target = AopUtils.isAopProxy(service) ? AopTestUtils.getTargetObject(service) : service;
        ReflectionTestUtils.setField(target, "fundTransferRepository", replacement);
    }
}
