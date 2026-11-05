package com.javatodev.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.model.entity.UtilityAccountEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;
import com.javatodev.finance.repository.UserRepository;
import com.javatodev.finance.repository.UtilityAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Import(PaymentAuthorizationRegressionTest.TestJwtConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class PaymentAuthorizationRegressionTest {
    private static final String TRANSFER = "/api/v1/transaction/fund-transfer";
    private static final String PAYMENT = "/api/v1/transaction/util-payment";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BankAccountRepository accounts;
    @Autowired TransactionRepository transactions;
    @Autowired UserRepository users;
    @Autowired UtilityAccountRepository utilityAccounts;

    private UserEntity owner;
    private UserEntity other;
    private BankAccountEntity debit;
    private BankAccountEntity credit;
    private Long providerId;

    @BeforeEach
    void seed() {
        transactions.deleteAllInBatch();
        accounts.deleteAll();
        utilityAccounts.deleteAll();
        users.deleteAll();
        owner = user("owner@example.test");
        other = user("other@example.test");
        debit = account("1001", owner, "100.00", "100.00", AccountStatus.ACTIVE);
        credit = account("2002", other, "20.00", "20.00", AccountStatus.ACTIVE);
        UtilityAccountEntity provider = new UtilityAccountEntity();
        provider.setNumber("P1");
        provider.setProviderName("Water");
        providerId = utilityAccounts.saveAndFlush(provider).getId();
    }

    @ParameterizedTest
    @MethodSource("deniedMoneyCalls")
    void anonymousReadonlyAndServiceTokensCannotMoveMoney(String endpoint, String token) throws Exception {
        BigDecimal debitBefore = debit.getActualBalance();
        BigDecimal creditBefore = credit.getActualBalance();
        MvcResult result = submit(endpoint, request(endpoint), token);
        assertAll(
            () -> assertRejected(result, "money endpoint " + endpoint + " token " + token),
            () -> assertEquals(debitBefore, accounts.findByNumber("1001").orElseThrow().getActualBalance()),
            () -> assertEquals(creditBefore, accounts.findByNumber("2002").orElseThrow().getActualBalance()),
            () -> assertEquals(0, transactions.count(), "transaction row count observed " + transactions.count())
        );
    }

    static Stream<Arguments> deniedMoneyCalls() {
        return Stream.of(TRANSFER, PAYMENT)
            .flatMap(endpoint -> java.util.Arrays.asList(null, "readonly", "service").stream()
                .map(token -> Arguments.of(endpoint, token)));
    }

    @Test
    void customerCannotDebitAnotherUsersAccountAtCore() throws Exception {
        MvcResult result = submit(TRANSFER, request(TRANSFER), "customer-other");
        assertAll(
            () -> assertRejected(result, "non-owner core fund transfer"),
            () -> assertEquals(new BigDecimal("100.00"), accounts.findByNumber("1001").orElseThrow().getActualBalance()),
            () -> assertEquals(0, transactions.count())
        );
    }

    @Test
    void utilityPaymentCustomerCannotDebitAnotherUsersAccountAtCore() throws Exception {
        MvcResult result = submit(PAYMENT, request(PAYMENT), "customer-other");
        assertAll(
            () -> assertRejected(result, "non-owner core utility payment"),
            () -> assertEquals(new BigDecimal("100.00"), accounts.findByNumber("1001").orElseThrow().getActualBalance()),
            () -> assertEquals(0, transactions.count())
        );
    }

    @ParameterizedTest
    @MethodSource("moneyEndpoints")
    void pendingUserCannotTransferWithoutCustomerRole(String endpoint) throws Exception {
        MvcResult result = submit(endpoint, request(endpoint), "pending");
        assertAll(
            () -> assertRejected(result, "pending user " + endpoint),
            () -> assertEquals(new BigDecimal("100.00"), accounts.findByNumber("1001").orElseThrow().getActualBalance()),
            () -> assertEquals(0, transactions.count())
        );
    }

    static Stream<String> moneyEndpoints() {
        return Stream.of(TRANSFER, PAYMENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-10", "0", "0.001"})
    void fundTransferRejectsInvalidAmounts(String amount) throws Exception {
        String body = "{\"fromAccount\":\"1001\",\"toAccount\":\"2002\",\"amount\":" + amount + "}";
        MvcResult result = submit(TRANSFER, body, "owner");
        assertAll(
            () -> assertBadRequest(result, "core transfer amount " + amount),
            this::assertNoMovement
        );
    }

    @Test
    void fundTransferRejectsNullAmount() throws Exception {
        MvcResult result = submit(TRANSFER, "{\"fromAccount\":\"1001\",\"toAccount\":\"2002\",\"amount\":null}", "owner");
        String body = result.getResponse().getContentAsString();
        assertAll(
            () -> assertBadRequest(result, "core transfer null amount"),
            () -> assertFalse(body.contains("NullPointerException"),
                "null amount was rejected by an incidental NPE; observed body=" + body),
            this::assertNoMovement
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"-10", "0", "0.001", "null"})
    void utilityPaymentRejectsInvalidAmounts(String amount) throws Exception {
        String body = "{\"providerId\":" + providerId + ",\"account\":\"1001\",\"referenceNumber\":\"ref\",\"amount\":" + amount + "}";
        MvcResult result = submit(PAYMENT, body, "owner");
        String responseBody = result.getResponse().getContentAsString();
        assertAll(
            () -> assertBadRequest(result, "core utility amount " + amount),
            () -> {
                if ("null".equals(amount)) {
                    assertFalse(responseBody.contains("NullPointerException"),
                        "null utility amount was rejected by an incidental NPE; observed body=" + responseBody);
                }
            },
            this::assertNoMovement
        );
    }

    @ParameterizedTest
    @MethodSource("moneyEndpoints")
    void nonActiveDebitAccountIsRejected(String endpoint) throws Exception {
        debit.setStatus(AccountStatus.BLOCKED);
        accounts.saveAndFlush(debit);
        MvcResult blocked = submit(endpoint, request(endpoint), "owner");
        assertAll(
            () -> assertBadRequest(blocked, "blocked debit account " + endpoint),
            this::assertNoMovement
        );
    }

    @ParameterizedTest
    @MethodSource("moneyEndpoints")
    void amountAboveAvailableBalanceIsRejected(String endpoint) throws Exception {
        debit.setAvailableBalance(new BigDecimal("5.00"));
        accounts.saveAndFlush(debit);
        MvcResult result = submit(endpoint, request(endpoint), "owner");
        assertAll(
            () -> assertBadRequest(result, "amount exceeds available balance " + endpoint),
            this::assertNoMovement
        );
    }

    @Test
    void inactiveCreditAccountIsRejected() throws Exception {
        credit.setStatus(AccountStatus.BLOCKED);
        accounts.saveAndFlush(credit);
        MvcResult result = submit(TRANSFER, "{\"fromAccount\":\"1001\",\"toAccount\":\"2002\",\"amount\":10}", "owner");
        assertAll(
            () -> assertBadRequest(result, "blocked credit account"),
            this::assertNoMovement
        );
    }

    @Test
    void positiveTransferReducesAvailableBalanceByAmount() throws Exception {
        MvcResult result = submit(TRANSFER, "{\"fromAccount\":\"1001\",\"toAccount\":\"2002\",\"amount\":25}", "owner");
        assertEquals(200, result.getResponse().getStatus(), "owner transfer observed HTTP " + result.getResponse().getStatus());
        assertEquals(new BigDecimal("75.00"), accounts.findByNumber("1001").orElseThrow().getAvailableBalance());
        assertEquals(new BigDecimal("45.00"), accounts.findByNumber("2002").orElseThrow().getAvailableBalance());
    }

    @Test
    void positiveUtilityPaymentDebitsOwnedAccount() throws Exception {
        MvcResult result = submit(PAYMENT,
            "{\"providerId\":" + providerId + ",\"account\":\"1001\",\"referenceNumber\":\"ref\",\"amount\":10}", "owner");
        assertEquals(200, result.getResponse().getStatus(),
            "owner utility payment observed HTTP " + result.getResponse().getStatus());
        assertEquals(new BigDecimal("90.00"), accounts.findByNumber("1001").orElseThrow().getActualBalance());
        assertEquals(new BigDecimal("90.00"), accounts.findByNumber("1001").orElseThrow().getAvailableBalance());
        assertEquals(1, transactions.count());
    }

    @Test
    void moneyRequestLogsDoNotContainAccountOrAmount(CapturedOutput output) throws Exception {
        submit(TRANSFER, "{\"fromAccount\":\"1001\",\"toAccount\":\"2002\",\"amount\":10}", "owner");
        assertFalse(output.getOut().contains("1001"), "transfer account number appeared in log output");
        assertFalse(output.getOut().contains("2002"), "recipient account number appeared in log output");
        assertFalse(output.getOut().contains("\"amount\":10"), "transfer amount appeared in log output");
    }

    @Test
    void accountRepositoryExposesPessimisticWriteLockLookup() {
        Method lockedLookup = null;
        for (Method method : BankAccountRepository.class.getMethods()) {
            if (method.getName().equals("findByNumberForUpdate")) {
                lockedLookup = method;
                break;
            }
        }
        assertNotNull(lockedLookup, "BankAccountRepository must declare findByNumberForUpdate");
        assertTrue(lockedLookup.isAnnotationPresent(org.springframework.data.jpa.repository.Lock.class),
            "findByNumberForUpdate must be annotated with @Lock(PESSIMISTIC_WRITE)");
        assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE,
            lockedLookup.getAnnotation(org.springframework.data.jpa.repository.Lock.class).value());
    }

    @Test
    void concurrentFullBalanceTransfersHaveOneWinnerAndConserveBalance() throws Exception {
        int count = 8;
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            List<Future<Integer>> responses = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                responses.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return submit(TRANSFER,
                        "{\"fromAccount\":\"1001\",\"toAccount\":\"2002\",\"amount\":100}", "owner")
                        .getResponse().getStatus();
                }));
            }
            ready.await();
            start.countDown();
            long successes = 0;
            for (Future<Integer> response : responses) {
                if (response.get() == 200) successes++;
            }
            BigDecimal total = accounts.findByNumber("1001").orElseThrow().getActualBalance()
                .add(accounts.findByNumber("2002").orElseThrow().getActualBalance());
            long observedSuccesses = successes;
            assertAll(
                () -> assertEquals(1, observedSuccesses,
                    "exactly one full-balance transfer should succeed; observed " + observedSuccesses),
                () -> assertEquals(new BigDecimal("120.00"), total, "balance total observed " + total),
                () -> assertTrue(accounts.findByNumber("1001").orElseThrow().getActualBalance().signum() >= 0)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @MethodSource("deniedUserReads")
    void coreUserEndpointsRequireServiceOrAdminForReads(String endpoint, String token) throws Exception {
        assertRejected(getUser(endpoint, token), "core user read " + endpoint + " token " + token);
    }

    static Stream<Arguments> deniedUserReads() {
        return Stream.of("/api/v1/user", "/api/v1/user/NIC-1")
            .flatMap(endpoint -> java.util.Arrays.asList(null, "customer", "readonly", "service").stream()
                .filter(token -> !("/api/v1/user/NIC-1".equals(endpoint) && "service".equals(token)))
                .filter(token -> !("/api/v1/user".equals(endpoint) && "admin".equals(token)))
                .map(token -> Arguments.of(endpoint, token)));
    }

    @Test
    void coreUserEndpointsAllowBankingServiceDetailAndBankAdminList() throws Exception {
        assertEquals(200, getUser("/api/v1/user/NIC-1", "service").getResponse().getStatus());
        assertEquals(200, getUser("/api/v1/user/NIC-1", "admin").getResponse().getStatus());
        assertEquals(200, getUser("/api/v1/user", "admin").getResponse().getStatus());
    }

    private MvcResult getUser(String endpoint, String token) throws Exception {
        var request = get(endpoint);
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request).andReturn();
    }

    private MvcResult submit(String endpoint, String body, String token) throws Exception {
        var request = post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request).andReturn();
    }

    private String request(String endpoint) {
        return TRANSFER.equals(endpoint)
            ? "{\"fromAccount\":\"1001\",\"toAccount\":\"2002\",\"amount\":10}"
            : "{\"providerId\":" + providerId + ",\"account\":\"1001\",\"referenceNumber\":\"ref\",\"amount\":10}";
    }

    private void assertRejected(MvcResult result, String label) throws Exception {
        int status = result.getResponse().getStatus();
        assertTrue(status == 401 || status == 403,
            label + " expected HTTP 401/403 but observed HTTP " + status + " body=" + result.getResponse().getContentAsString());
    }

    private void assertBadRequest(MvcResult result, String label) throws Exception {
        int status = result.getResponse().getStatus();
        assertEquals(400, status, label + " expected HTTP 400 but observed HTTP " + status);
    }

    private void assertNoMovement() {
        assertEquals(new BigDecimal("100.00"), accounts.findByNumber("1001").orElseThrow().getActualBalance(),
            "debit balance observed " + accounts.findByNumber("1001").orElseThrow().getActualBalance());
        assertEquals(new BigDecimal("20.00"), accounts.findByNumber("2002").orElseThrow().getActualBalance(),
            "credit balance observed " + accounts.findByNumber("2002").orElseThrow().getActualBalance());
        assertEquals(0, transactions.count(), "transaction row count observed " + transactions.count());
    }

    private UserEntity user(String email) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setIdentificationNumber("owner@example.test".equals(email) ? "NIC-1" : "NIC-" + email);
        return users.saveAndFlush(user);
    }

    private BankAccountEntity account(String number, UserEntity user, String actual, String available, AccountStatus status) {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber(number);
        account.setUser(user);
        account.setActualBalance(new BigDecimal(actual));
        account.setAvailableBalance(new BigDecimal(available));
        account.setStatus(status);
        return accounts.saveAndFlush(account);
    }

    @TestConfiguration
    static class TestJwtConfiguration {
        @Bean
        JwtDecoder testJwtDecoder() {
            return token -> {
                String email = "owner@example.test";
                List<String> roles = List.of("bank-customer");
                if ("readonly".equals(token)) roles = List.of("read-only");
                if ("service".equals(token)) roles = List.of("banking-service");
                if ("pending".equals(token)) roles = List.of();
                if ("customer-other".equals(token)) email = "other@example.test";
                if ("admin".equals(token)) roles = List.of("bank-admin");
                if ("customer".equals(token)) roles = List.of("bank-customer");
                Instant now = Instant.now();
                return Jwt.withTokenValue(token).header("alg", "none").subject("test-subject")
                    .claim("preferred_username", email)
                    .claim("realm_access", Map.of("roles", roles))
                    .issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
            };
        }
    }
}
