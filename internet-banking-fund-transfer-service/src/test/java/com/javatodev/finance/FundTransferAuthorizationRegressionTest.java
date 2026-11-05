package com.javatodev.finance;

import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.AccountResponse;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.entity.FundTransferEntity;
import com.javatodev.finance.model.repository.FundTransferRepository;
import com.javatodev.finance.service.FundTransferService;
import com.javatodev.finance.service.rest.client.BankingCoreFeignClient;
import feign.FeignException;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import com.javatodev.finance.configuration.CustomFeignClientConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.Answers;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({SpringExtension.class, OutputCaptureExtension.class})
class FundTransferAuthorizationRegressionTest {
    private FundTransferRepository repository;
    private BankingCoreFeignClient core;
    private FundTransferService service;
    private List<FundTransferEntity> historyRows = List.of();

    @BeforeEach
    void setUp() {
        repository = mock(FundTransferRepository.class, invocation -> {
            if (invocation.getMethod().getName().equals("findAllByOwnerId")) {
                return new PageImpl<>(historyRows);
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        core = mock(BankingCoreFeignClient.class);
        when(repository.save(any(FundTransferEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(repository.count()).thenReturn(0L);
        service = new FundTransferService(repository, core);
        authenticate("subject-owner", "customer@example.test", List.of("bank-customer"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonOwnerCorePrecheckDeniesBeforePersistenceAndMoneyCall() {
        when(core.readAccount("DEBIT-1")).thenThrow(forbidden("readAccount"));
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        try {
            service.fundTransfer(request("DEBIT-1", "CREDIT-2", "10"));
        } catch (Throwable failure) {
            thrown.set(failure);
        }

        assertAll(
            () -> assertTrue(thrown.get() instanceof AccessDeniedException,
                "expected edge 403 denial but observed " + (thrown.get() == null ? "no exception" : thrown.get().getClass().getName())),
            () -> assertEquals(0, repository.count(), "persisted row count observed " + repository.count()),
            () -> verify(repository, never()).save(any(FundTransferEntity.class)),
            () -> verify(core, never()).fundTransfer(any())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"-10", "0", "0.001", "null"})
    void invalidAmountsAreRejectedBeforePersistenceOrCoreCall(String amount) {
        FundTransferRequest request = request("DEBIT-1", "CREDIT-2", "null".equals(amount) ? null : amount);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        try {
            service.fundTransfer(request);
        } catch (Throwable failure) {
            thrown.set(failure);
        }
        assertTrue(thrown.get() instanceof IllegalArgumentException,
            "amount " + amount + " expected validation failure but observed " + thrown.get());
        verify(repository, never()).save(any(FundTransferEntity.class));
        verify(core, never()).fundTransfer(any());
    }

    @Test
    void pendingCustomerWithoutCustomerRoleCannotTransfer() {
        authenticate("pending-sub", "pending@example.test", List.of());
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        try {
            service.fundTransfer(request("DEBIT-1", "CREDIT-2", "10"));
        } catch (Throwable failure) {
            thrown.set(failure);
        }
        assertAll(
            () -> assertTrue(thrown.get() instanceof AccessDeniedException,
                "pending customer expected 403 but observed " + thrown.get()),
            () -> assertEquals(0, repository.count(), "persisted row count observed " + repository.count()),
            () -> verify(repository, never()).save(any(FundTransferEntity.class)),
            () -> verify(core, never()).fundTransfer(any())
        );
    }

    @Test
    void auditOwnerComesFromJwtSubjectNotForgedRequestIdentity() {
        AccountResponse account = new AccountResponse();
        account.setStatus("ACTIVE");
        account.setAvailableBalance(new BigDecimal("100"));
        when(core.readAccount("DEBIT-1")).thenReturn(account);
        when(core.fundTransfer(any())).thenReturn(successResponse());
        AtomicReference<FundTransferEntity> persisted = new AtomicReference<>();
        when(repository.save(any(FundTransferEntity.class))).thenAnswer(call -> {
            FundTransferEntity entity = call.getArgument(0);
            persisted.set(entity);
            return entity;
        });
        FundTransferRequest request = request("DEBIT-1", "CREDIT-2", "10");
        request.setAuthID("forged-header-value");

        service.fundTransfer(request);

        assertEquals("subject-owner", readOwnerId(persisted.get()),
            "created owner id observed " + readOwnerId(persisted.get()));
    }

    @Test
    void historyContainsOnlyTheCurrentJwtSubjectRecords() {
        FundTransferEntity own = new FundTransferEntity();
        FundTransferEntity other = new FundTransferEntity();
        setOwnerId(own, "subject-owner");
        setOwnerId(other, "other-subject");
        historyRows = List.of(own);
        when(repository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(own, other)));
        when(repository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(new PageImpl<>(List.of(own, other)));

        List<?> history = service.readAllTransfers(PageRequest.of(0, 10));

        assertEquals(1, history.size(), "history rows observed " + history.size());
    }

    @Test
    void successfulOwnerTransferPersistsAndCallsCore() {
        AccountResponse account = new AccountResponse();
        account.setStatus("ACTIVE");
        account.setAvailableBalance(new BigDecimal("100"));
        when(core.readAccount(anyString())).thenReturn(account);
        when(core.fundTransfer(any())).thenReturn(successResponse());

        assertEquals("Fund Transfer Successfully Completed", service.fundTransfer(request("DEBIT-1", "CREDIT-2", "10")).getMessage());
        verify(core).fundTransfer(any());
        verify(repository, times(2)).save(any(FundTransferEntity.class));
    }

    @Test
    void feignLoggerIsNotFull() throws Exception {
        var method = CustomFeignClientConfiguration.class.getDeclaredMethod("feignLoggerLevel");
        method.setAccessible(true);
        Logger.Level level = (Logger.Level) method.invoke(new CustomFeignClientConfiguration());
        assertNotEquals(Logger.Level.FULL, level, "Feign logger level observed FULL");
    }

    @Test
    void feignInterceptorForwardsCallerBearerToken() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CustomFeignClientConfiguration.class)) {
            RequestTemplate template = new RequestTemplate();
            context.getBeansOfType(RequestInterceptor.class).values().forEach(interceptor -> interceptor.apply(template));
            assertTrue(template.headers().getOrDefault("Authorization", java.util.Set.of()).contains("Bearer test-token"),
                "Authorization header observed " + template.headers().get("Authorization"));
        }
    }

    @Test
    void transferLogsDoNotContainAccountNumberOrAmount(CapturedOutput output) {
        AccountResponse account = new AccountResponse();
        account.setStatus("ACTIVE");
        account.setAvailableBalance(new BigDecimal("100"));
        when(core.readAccount(anyString())).thenReturn(account);
        when(core.fundTransfer(any())).thenReturn(successResponse());
        service.fundTransfer(request("SENSITIVE-ACCOUNT-704", "SENSITIVE-ACCOUNT-705", "9.32"));
        assertFalse(output.getOut().contains("SENSITIVE-ACCOUNT-704"), "debit account number appeared in logs");
        assertFalse(output.getOut().contains("SENSITIVE-ACCOUNT-705"), "credit account number appeared in logs");
        assertFalse(output.getOut().contains("9.32"), "transfer amount appeared in logs");
    }

    private FeignException forbidden(String key) {
        Request request = Request.create(Request.HttpMethod.GET, "http://core", Map.of(), null, null, null);
        Response response = Response.builder().status(403).reason("Forbidden").request(request).headers(Map.of()).build();
        return FeignException.errorStatus(key, response);
    }

    private FundTransferRequest request(String from, String to, String amount) {
        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount(from);
        request.setToAccount(to);
        request.setAmount(amount == null ? null : new BigDecimal(amount));
        return request;
    }

    private void authenticate(String subject, String username, List<String> roles) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(subject)
            .claim("preferred_username", username).claim("realm_access", Map.of("roles", roles))
            .issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private FundTransferResponse successResponse() {
        FundTransferResponse response = new FundTransferResponse();
        response.setTransactionId("txn-1");
        return response;
    }

    private String readOwnerId(FundTransferEntity entity) {
        try {
            Field field = entity.getClass().getDeclaredField("ownerId");
            field.setAccessible(true);
            return (String) field.get(entity);
        } catch (ReflectiveOperationException ignored) {
            return entity.getCreatedBy();
        }
    }

    private void setOwnerId(FundTransferEntity entity, String ownerId) {
        try {
            Field field = entity.getClass().getDeclaredField("ownerId");
            field.setAccessible(true);
            field.set(entity, ownerId);
        } catch (ReflectiveOperationException ignored) {
            entity.setCreatedBy(ownerId);
        }
    }
}
