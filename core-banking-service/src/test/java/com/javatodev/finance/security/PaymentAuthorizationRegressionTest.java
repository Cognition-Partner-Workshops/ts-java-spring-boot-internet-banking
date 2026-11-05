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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for the payment-authorization chain in core banking (sfind-ee4ab8ab, sfind-640f8ec8,
 * sfind-87d6d204, sfind-821ac1aa, sfind-ac7cde3a). Requests go through the real HTTP/security stack.
 */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false"
})
@AutoConfigureMockMvc
class PaymentAuthorizationRegressionTest {

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
        insertAccount(1, "ALICE-1", 1);
        insertAccount(2, "ALICE-2", 1);
        insertAccount(3, "BOB-1", 2);
        jdbcTemplate.update("INSERT INTO banking_core_utility_account (id, number, provider_name) VALUES (1, 'UTIL-1', 'POWER')");
    }

    @Test
    void customerCannotTransferFromAnotherCustomersAccount() throws Exception {
        mockMvc.perform(transfer(TestJwt.customer(ALICE), "BOB-1", "ALICE-1", "100.00"))
            .andExpect(status().isForbidden());
        assertBalance("BOB-1", "1000.00");
        assertBalance("ALICE-1", "1000.00");
    }

    @Test
    void customerCannotPayUtilityFromAnotherCustomersAccount() throws Exception {
        mockMvc.perform(utilityPayment(TestJwt.customer(ALICE), "BOB-1", "100.00"))
            .andExpect(status().isForbidden());
        assertBalance("BOB-1", "1000.00");
    }

    @Test
    void customerCannotReadAnotherCustomersAccount() throws Exception {
        mockMvc.perform(get("/api/v1/account/bank-account/BOB-1").header("Authorization", "Bearer " + TestJwt.customer(ALICE)))
            .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotListAllBankingUsers() throws Exception {
        mockMvc.perform(get("/api/v1/user").header("Authorization", "Bearer " + TestJwt.customer(ALICE)))
            .andExpect(status().isForbidden());
    }

    @Test
    void staffRoleDoesNotGrantMoneyMovementFromCustomerAccounts() throws Exception {
        mockMvc.perform(transfer(TestJwt.staff("ib_admin@javatodev.com"), "BOB-1", "ALICE-1", "100.00"))
            .andExpect(status().isForbidden());
        assertBalance("BOB-1", "1000.00");
    }

    @Test
    void serviceAccountTokenCannotMoveCustomerMoney() throws Exception {
        mockMvc.perform(transfer(TestJwt.serviceAccount(TestJwt.CUSTOMER_CLIENT), "BOB-1", "ALICE-1", "100.00"))
            .andExpect(status().isForbidden());
        assertBalance("BOB-1", "1000.00");
    }

    @Test
    void directCallWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(transfer(null, "BOB-1", "ALICE-1", "100.00"))
            .andExpect(status().isUnauthorized());
        assertBalance("BOB-1", "1000.00");
    }

    @Test
    void directCallWithForgedTokenIsRejected() throws Exception {
        mockMvc.perform(transfer(TestJwt.forged(ALICE), "BOB-1", "ALICE-1", "100.00"))
            .andExpect(status().isUnauthorized());
        assertBalance("BOB-1", "1000.00");
    }

    @Test
    void negativeTransferAmountIsRejected() throws Exception {
        mockMvc.perform(transfer(TestJwt.customer(ALICE), "ALICE-1", "BOB-1", "-500.00"))
            .andExpect(status().isBadRequest());
        assertBalance("ALICE-1", "1000.00");
        assertBalance("BOB-1", "1000.00");
    }

    @Test
    void negativeUtilityPaymentAmountIsRejected() throws Exception {
        mockMvc.perform(utilityPayment(TestJwt.customer(ALICE), "ALICE-1", "-500.00"))
            .andExpect(status().isBadRequest());
        assertBalance("ALICE-1", "1000.00");
    }

    @Test
    void zeroAndSubCentAmountsAreRejected() throws Exception {
        mockMvc.perform(transfer(TestJwt.customer(ALICE), "ALICE-1", "BOB-1", "0.00"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(transfer(TestJwt.customer(ALICE), "ALICE-1", "BOB-1", "0.005"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(utilityPayment(TestJwt.customer(ALICE), "ALICE-1", "0.001"))
            .andExpect(status().isBadRequest());
        assertBalance("ALICE-1", "1000.00");
    }

    @Test
    void sameAccountTransferIsRejected() throws Exception {
        mockMvc.perform(transfer(TestJwt.customer(ALICE), "ALICE-1", "ALICE-1", "10.00"))
            .andExpect(status().isBadRequest());
        assertBalance("ALICE-1", "1000.00");
    }

    @Test
    void ownerCanTransferAndPayFromOwnAccount() throws Exception {
        mockMvc.perform(transfer(TestJwt.customer(ALICE), "ALICE-1", "BOB-1", "100.50"))
            .andExpect(status().isOk());
        assertBalance("ALICE-1", "899.50");
        assertBalance("BOB-1", "1100.50");

        mockMvc.perform(utilityPayment(TestJwt.customer(ALICE), "ALICE-2", "25.00"))
            .andExpect(status().isOk());
        assertBalance("ALICE-2", "975.00");

        mockMvc.perform(get("/api/v1/account/bank-account/ALICE-1").header("Authorization", "Bearer " + TestJwt.customer(ALICE)))
            .andExpect(status().isOk());
    }

    @Test
    void internalServiceCanLookUpUserForRegistration() throws Exception {
        mockMvc.perform(get("/api/v1/user/ID-ALICE").header("Authorization", "Bearer " + TestJwt.serviceAccount(TestJwt.INTERNAL_CLIENT)))
            .andExpect(status().isOk());
    }

    private void insertAccount(long id, String number, long userId) {
        jdbcTemplate.update("INSERT INTO banking_core_account (id, number, type, status, available_balance, actual_balance, user_id) "
            + "VALUES (?, ?, 'SAVINGS_ACCOUNT', 'ACTIVE', 1000.00, 1000.00, ?)", id, number, userId);
    }

    private void assertBalance(String number, String expected) {
        BigDecimal actual = jdbcTemplate.queryForObject("SELECT actual_balance FROM banking_core_account WHERE number = ?", BigDecimal.class, number);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "actual balance of " + number + " was " + actual);
    }

    private static MockHttpServletRequestBuilder transfer(String token, String from, String to, String amount) {
        return withToken(post("/api/v1/transaction/fund-transfer"), token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fromAccount\":\"" + from + "\",\"toAccount\":\"" + to + "\",\"amount\":" + amount + "}");
    }

    private static MockHttpServletRequestBuilder utilityPayment(String token, String account, String amount) {
        return withToken(post("/api/v1/transaction/util-payment"), token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"providerId\":1,\"account\":\"" + account + "\",\"referenceNumber\":\"REF-1\",\"amount\":" + amount + "}");
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }
}
