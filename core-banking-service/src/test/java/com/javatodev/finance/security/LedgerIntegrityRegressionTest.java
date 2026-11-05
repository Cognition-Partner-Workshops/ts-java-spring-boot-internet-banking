package com.javatodev.finance.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for sfind-8e5baef9 (unlocked check-then-act lets concurrent transfers overspend a balance)
 * and sfind-45acd3f5 (available balance applies the amount twice).
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_ledger_integrity;LOCK_TIMEOUT=20000",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false"
})
@AutoConfigureMockMvc
class LedgerIntegrityRegressionTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestJwt::jwkSetUri);
    }

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM banking_core_transaction");
        jdbcTemplate.update("DELETE FROM banking_core_account");
        jdbcTemplate.update("DELETE FROM banking_core_utility_account");
        jdbcTemplate.update("DELETE FROM banking_core_user");
        jdbcTemplate.update("INSERT INTO banking_core_user (id, email, first_name, last_name, identification_number) VALUES (1, ?, 'Alice', 'A', 'ID-ALICE')", ALICE);
        jdbcTemplate.update("INSERT INTO banking_core_user (id, email, first_name, last_name, identification_number) VALUES (2, ?, 'Bob', 'B', 'ID-BOB')", BOB);
        jdbcTemplate.update("INSERT INTO banking_core_account (id, number, type, status, available_balance, actual_balance, user_id) VALUES (1, 'ALICE-1', 'SAVINGS_ACCOUNT', 'ACTIVE', 1000.00, 1000.00, 1)");
        jdbcTemplate.update("INSERT INTO banking_core_account (id, number, type, status, available_balance, actual_balance, user_id) VALUES (2, 'BOB-1', 'SAVINGS_ACCOUNT', 'ACTIVE', 1000.00, 1000.00, 2)");
        jdbcTemplate.update("INSERT INTO banking_core_utility_account (id, number, provider_name) VALUES (1, 'UTIL-1', 'POWER')");
    }

    @Test
    void concurrentTransfersCannotSpendTheSameBalanceTwice() throws Exception {
        int attempts = 8;
        String token = TestJwt.customer(ALICE);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/v1/transaction/fund-transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccount\":\"ALICE-1\",\"toAccount\":\"BOB-1\",\"amount\":1000.00}"))
                    .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Integer> result : results) {
            if (result.get(60, TimeUnit.SECONDS) == 200) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertEquals(1, succeeded, "transfers of the full 1000.00 balance that succeeded concurrently");
        assertEquals(0, BigDecimal.ZERO.compareTo(balance("actual_balance", "ALICE-1")), "ALICE-1 actual balance");
        assertEquals(0, new BigDecimal("2000.00").compareTo(balance("actual_balance", "BOB-1")), "BOB-1 actual balance");
    }

    @Test
    void availableBalanceMovesByTheTransferAmountOnce() throws Exception {
        mockMvc.perform(post("/api/v1/transaction/fund-transfer")
            .header("Authorization", "Bearer " + TestJwt.customer(ALICE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fromAccount\":\"ALICE-1\",\"toAccount\":\"BOB-1\",\"amount\":100.00}"))
            .andExpect(status().isOk());

        assertEquals(0, new BigDecimal("900.00").compareTo(balance("available_balance", "ALICE-1")), "ALICE-1 available balance was " + balance("available_balance", "ALICE-1"));
        assertEquals(0, new BigDecimal("1100.00").compareTo(balance("available_balance", "BOB-1")), "BOB-1 available balance was " + balance("available_balance", "BOB-1"));
    }

    @Test
    void availableBalanceMovesByTheUtilityPaymentAmountOnce() throws Exception {
        mockMvc.perform(post("/api/v1/transaction/util-payment")
            .header("Authorization", "Bearer " + TestJwt.customer(BOB))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"providerId\":1,\"account\":\"BOB-1\",\"referenceNumber\":\"REF-1\",\"amount\":50.00}"))
            .andExpect(status().isOk());

        assertEquals(0, new BigDecimal("950.00").compareTo(balance("available_balance", "BOB-1")), "BOB-1 available balance was " + balance("available_balance", "BOB-1"));
    }

    private BigDecimal balance(String column, String number) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM banking_core_account WHERE number = ?", BigDecimal.class, number);
    }
}
