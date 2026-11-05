package com.javatodev.finance;

import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.AccountType;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.UserRepository;
import com.javatodev.finance.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class PaymentConcurrencyPersistenceRegressionTest {
    private static final int SCENARIO_COUNT = 20;
    private static final int THREAD_COUNT = 8;
    private static final BigDecimal FULL_BALANCE = new BigDecimal("100.00");
    private static final String CUSTOMER_ROLE = "ROLE_bank-customer";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.37")
        .withDatabaseName("banking_core_service")
        .withUsername("banking_test")
        .withPassword(requiredEnvironmentValue("TEST_MYSQL_PASSWORD"));

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private BankAccountRepository accounts;

    @Autowired
    private UserRepository users;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Test
    void fullBalanceTransfersHaveOneWinnerAcrossTwentyPersistedScenarios() throws Exception {
        String runLabel = System.getenv().getOrDefault("CONCURRENCY_RUN_LABEL", "unspecified");
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<String> violations = new ArrayList<>();
        try {
            for (int scenario = 1; scenario <= SCENARIO_COUNT; scenario++) {
                ScenarioResult result = runScenario(executor, scenario, runLabel);
                System.out.printf(
                    "run=%s scenario=%02d winners=%d finalBalances=%s total=%s conserved=%s negatives=%d errors=%s%n",
                    runLabel, scenario, result.winners(), result.finalBalances(), result.total(),
                    result.conserved(), result.negativeBalances(), result.errors());
                if (result.winners() != 1 || !result.conserved() || result.negativeBalances() != 0
                    || !result.errors().isEmpty()) {
                    violations.add("scenario=" + scenario + " result=" + result);
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
        assertTrue(violations.isEmpty(), "concurrency invariant failures: " + violations);
    }

    private ScenarioResult runScenario(ExecutorService executor, int scenario, String runLabel)
        throws InterruptedException, ExecutionException, TimeoutException {
        String ownerEmail = "concurrency-" + runLabel + "-" + scenario + "@example.test";
        UserEntity owner = new UserEntity();
        owner.setEmail(ownerEmail);
        owner.setIdentificationNumber("CONCURRENCY-" + runLabel + "-" + scenario);
        owner = users.saveAndFlush(owner);

        String sourceNumber = "SRC-" + scenario;
        saveAccount(sourceNumber, owner, FULL_BALANCE);
        List<String> destinationNumbers = new ArrayList<>();
        for (int index = 0; index < THREAD_COUNT; index++) {
            String number = "DST-" + scenario + "-" + index;
            destinationNumbers.add(number);
            saveAccount(number, owner, BigDecimal.ZERO);
        }

        CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
        String preferredUsername = ownerEmail;
        List<Future<String>> attempts = new ArrayList<>();
        for (String destinationNumber : destinationNumbers) {
            attempts.add(executor.submit(() -> transferAfterBarrier(
                barrier, sourceNumber, destinationNumber, preferredUsername)));
        }

        int winners = 0;
        List<String> errors = new ArrayList<>();
        for (Future<String> attempt : attempts) {
            String outcome = attempt.get(60, TimeUnit.SECONDS);
            if ("WIN".equals(outcome)) {
                winners++;
            } else if (outcome.startsWith("ERROR:")) {
                errors.add(outcome);
            }
        }

        List<BigDecimal> balances = new ArrayList<>();
        balances.add(accounts.findByNumber(sourceNumber).orElseThrow().getActualBalance());
        for (String destinationNumber : destinationNumbers) {
            balances.add(accounts.findByNumber(destinationNumber).orElseThrow().getActualBalance());
        }
        BigDecimal total = balances.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int negatives = (int) balances.stream().filter(balance -> balance.signum() < 0).count();
        return new ScenarioResult(winners, balances, total,
            total.compareTo(FULL_BALANCE) == 0, negatives, errors);
    }

    private String transferAfterBarrier(
        CyclicBarrier barrier,
        String sourceNumber,
        String destinationNumber,
        String preferredUsername
    ) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(customerAuthentication(preferredUsername));
        SecurityContextHolder.setContext(context);
        try {
            barrier.await(60, TimeUnit.SECONDS);
            transactionService.fundTransfer(
                new FundTransferRequest(sourceNumber, destinationNumber, FULL_BALANCE));
            return "WIN";
        } catch (RuntimeException e) {
            return "REJECT:" + e.getClass().getSimpleName();
        } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
            Thread.currentThread().interrupt();
            return "ERROR:" + e.getClass().getSimpleName();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private JwtAuthenticationToken customerAuthentication(String preferredUsername) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue(UUID.randomUUID().toString())
            .header("alg", "none")
            .subject("concurrency-subject")
            .claim("preferred_username", preferredUsername)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600))
            .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(CUSTOMER_ROLE)));
    }

    private void saveAccount(String number, UserEntity owner, BigDecimal balance) {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber(number);
        account.setType(AccountType.SAVINGS_ACCOUNT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setActualBalance(balance);
        account.setAvailableBalance(balance);
        account.setUser(owner);
        accounts.saveAndFlush(account);
    }

    private static String requiredEnvironmentValue(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new ExceptionInInitializerError("Environment variable " + name + " is required");
        }
        return value;
    }

    private record ScenarioResult(
        int winners,
        List<BigDecimal> finalBalances,
        BigDecimal total,
        boolean conserved,
        int negativeBalances,
        List<String> errors
    ) {
    }
}
