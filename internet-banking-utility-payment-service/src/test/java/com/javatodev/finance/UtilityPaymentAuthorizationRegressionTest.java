package com.javatodev.finance;

import com.javatodev.finance.model.dto.UtilityPayment;
import com.javatodev.finance.model.entity.UtilityPaymentEntity;
import com.javatodev.finance.model.rest.request.UtilityPaymentRequest;
import com.javatodev.finance.model.rest.response.AccountResponse;
import com.javatodev.finance.model.rest.response.UtilityPaymentResponse;
import com.javatodev.finance.repository.UtilityPaymentRepository;
import com.javatodev.finance.service.UtilityPaymentService;
import com.javatodev.finance.service.rest.BankingCoreRestClient;
import feign.FeignException;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.mockito.Answers;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class UtilityPaymentAuthorizationRegressionTest {
    private UtilityPaymentRepository repository;
    private BankingCoreRestClient core;
    private UtilityPaymentService service;
    private List<UtilityPaymentEntity> historyRows = List.of();

    @BeforeEach
    void setUp() {
        repository = mock(UtilityPaymentRepository.class, invocation -> {
            if (invocation.getMethod().getName().equals("findAllByOwnerId")) {
                return new PageImpl<>(historyRows);
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        core = mock(BankingCoreRestClient.class);
        when(repository.save(any(UtilityPaymentEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(repository.count()).thenReturn(0L);
        service = new UtilityPaymentService(repository, core);
        authenticate("subject-owner", "customer@example.test", List.of("bank-customer"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonOwnerCorePrecheckDeniesBeforePersistenceAndPaymentCall() {
        when(core.readAccount("DEBIT-1")).thenThrow(forbidden());
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        try {
            service.utilPayment(request("DEBIT-1", "10"));
        } catch (Throwable failure) {
            thrown.set(failure);
        }
        assertAll(
            () -> assertTrue(thrown.get() instanceof AccessDeniedException,
                "expected edge 403 denial but observed " + (thrown.get() == null ? "no exception" : thrown.get().getClass().getName())),
            () -> assertEquals(0, repository.count(), "persisted row count observed " + repository.count()),
            () -> verify(repository, never()).save(any(UtilityPaymentEntity.class)),
            () -> verify(core, never()).utilityPayment(any())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"-10", "0", "0.001", "null"})
    void invalidAmountsAreRejectedBeforePersistenceOrCoreCall(String amount) {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        try {
            service.utilPayment(request("DEBIT-1", "null".equals(amount) ? null : amount));
        } catch (Throwable failure) {
            thrown.set(failure);
        }
        assertTrue(thrown.get() instanceof IllegalArgumentException,
            "amount " + amount + " expected validation failure but observed " + thrown.get());
        verify(repository, never()).save(any(UtilityPaymentEntity.class));
        verify(core, never()).utilityPayment(any());
    }

    @Test
    void pendingCustomerWithoutCustomerRoleCannotPay() {
        authenticate("pending-sub", "pending@example.test", List.of());
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        try {
            service.utilPayment(request("DEBIT-1", "10"));
        } catch (Throwable failure) {
            thrown.set(failure);
        }
        assertAll(
            () -> assertTrue(thrown.get() instanceof AccessDeniedException,
                "pending customer expected 403 but observed " + thrown.get()),
            () -> assertEquals(0, repository.count(), "persisted row count observed " + repository.count()),
            () -> verify(repository, never()).save(any(UtilityPaymentEntity.class)),
            () -> verify(core, never()).utilityPayment(any())
        );
    }

    @Test
    void auditOwnerComesFromJwtSubjectNotForgedRequestIdentity() {
        AccountResponse account = new AccountResponse();
        account.setStatus("ACTIVE");
        account.setAvailableBalance(new BigDecimal("100"));
        when(core.readAccount("DEBIT-1")).thenReturn(account);
        when(core.utilityPayment(any())).thenReturn(UtilityPaymentResponse.builder().transactionId("txn-1").build());
        AtomicReference<UtilityPaymentEntity> persisted = new AtomicReference<>();
        when(repository.save(any(UtilityPaymentEntity.class))).thenAnswer(call -> {
            UtilityPaymentEntity entity = call.getArgument(0);
            persisted.set(entity);
            return entity;
        });
        UtilityPaymentRequest request = request("DEBIT-1", "10");

        service.utilPayment(request);

        assertEquals("subject-owner", readOwnerId(persisted.get()),
            "created owner id observed " + readOwnerId(persisted.get()));
    }

    @Test
    void historyContainsOnlyTheCurrentJwtSubjectRecords() {
        UtilityPaymentEntity own = new UtilityPaymentEntity();
        UtilityPaymentEntity other = new UtilityPaymentEntity();
        setOwnerId(own, "subject-owner");
        setOwnerId(other, "other-subject");
        historyRows = List.of(own);
        when(repository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(own, other)));
        when(repository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(new PageImpl<>(List.of(own, other)));

        List<UtilityPayment> history = service.readPayments(PageRequest.of(0, 10));

        assertEquals(1, history.size(), "history rows observed " + history.size());
    }

    @Test
    void successfulOwnerPaymentPersistsAndCallsCore() {
        AccountResponse account = new AccountResponse();
        account.setStatus("ACTIVE");
        account.setAvailableBalance(new BigDecimal("100"));
        when(core.readAccount(anyString())).thenReturn(account);
        when(core.utilityPayment(any())).thenReturn(UtilityPaymentResponse.builder().transactionId("txn-1").build());
        assertEquals("Utility Payment Successfully Processed", service.utilPayment(request("DEBIT-1", "10")).getMessage());
        verify(core).utilityPayment(any());
        verify(repository, times(2)).save(any(UtilityPaymentEntity.class));
    }

    @Test
    void feignInterceptorForwardsCallerBearerToken() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(com.javatodev.finance.configuration.CustomFeignClientConfiguration.class)) {
            RequestTemplate template = new RequestTemplate();
            context.getBeansOfType(RequestInterceptor.class).values().forEach(interceptor -> interceptor.apply(template));
            assertTrue(template.headers().getOrDefault("Authorization", java.util.Set.of()).contains("Bearer test-token"),
                "Authorization header observed " + template.headers().get("Authorization"));
        }
    }

    @Test
    void paymentLogsDoNotContainAccountNumberOrAmount(CapturedOutput output) {
        AccountResponse account = new AccountResponse();
        account.setStatus("ACTIVE");
        account.setAvailableBalance(new BigDecimal("100"));
        when(core.readAccount(anyString())).thenReturn(account);
        when(core.utilityPayment(any())).thenReturn(UtilityPaymentResponse.builder().transactionId("txn-1").build());
        UtilityPaymentRequest request = request("SENSITIVE-UTILITY-ACCOUNT-704", "9.32");
        service.utilPayment(request);
        assertFalse(output.getOut().contains("SENSITIVE-UTILITY-ACCOUNT-704"), "utility account number appeared in logs");
        assertFalse(output.getOut().contains("9.32"), "utility amount appeared in logs");
    }

    private FeignException forbidden() {
        Request request = Request.create(Request.HttpMethod.GET, "http://core", Map.of(), null, null, null);
        Response response = Response.builder().status(403).reason("Forbidden").request(request).headers(Map.of()).build();
        return FeignException.errorStatus("readAccount", response);
    }

    private UtilityPaymentRequest request(String account, String amount) {
        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setAccount(account);
        request.setProviderId(1L);
        request.setReferenceNumber("reference");
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

    private String readOwnerId(UtilityPaymentEntity entity) {
        try {
            Field field = entity.getClass().getDeclaredField("ownerId");
            field.setAccessible(true);
            return (String) field.get(entity);
        } catch (ReflectiveOperationException ignored) {
            return entity.getCreatedBy();
        }
    }

    private void setOwnerId(UtilityPaymentEntity entity, String ownerId) {
        try {
            Field field = entity.getClass().getDeclaredField("ownerId");
            field.setAccessible(true);
            field.set(entity, ownerId);
        } catch (ReflectiveOperationException ignored) {
            entity.setCreatedBy(ownerId);
        }
    }
}
