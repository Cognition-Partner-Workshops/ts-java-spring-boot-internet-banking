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

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for sfind-87d6d204 (utility payment from an account the caller does not own),
 * sfind-364ecc21 (payment history of every customer) and sfind-821ac1aa (negative / sub-cent amounts).
 */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.cloud.config.enabled=false"
})
@AutoConfigureMockMvc
class UtilityPaymentAuthorizationRegressionTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestJwt::jwkSetUri);
        registry.add("spring.cloud.openfeign.client.config.core-banking-service.url", StubCoreBanking::url);
    }

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM utility_payment");
        StubCoreBanking.reset();
    }

    @Test
    void customerCannotPayFromAccountTheyDoNotOwn() throws Exception {
        mockMvc.perform(payment(TestJwt.customer(ALICE), "BOB-1", "100.00"))
            .andExpect(status().isForbidden());
        assertEquals(0, StubCoreBanking.calls("/api/v1/transaction/").size(), "payment debiting BOB-1 was forwarded to core banking");
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM utility_payment", Integer.class));
    }

    @Test
    void paymentRequiresAuthenticatedCaller() throws Exception {
        mockMvc.perform(payment(null, "BOB-1", "100.00"))
            .andExpect(status().isUnauthorized());
        assertEquals(0, StubCoreBanking.calls("/api/v1/transaction/").size());
    }

    @Test
    void invalidAmountsAreRejectedBeforeReachingCoreBanking() throws Exception {
        mockMvc.perform(payment(TestJwt.customer(ALICE), "ALICE-1", "-500.00")).andExpect(status().isBadRequest());
        mockMvc.perform(payment(TestJwt.customer(ALICE), "ALICE-1", "0.001")).andExpect(status().isBadRequest());
        mockMvc.perform(payment(TestJwt.customer(ALICE), "ALICE-1", "0")).andExpect(status().isBadRequest());
        assertEquals(0, StubCoreBanking.calls("/api/v1/transaction/").size());
    }

    @Test
    void ownerPaymentIsForwardedWithCallerToken() throws Exception {
        String token = TestJwt.customer(ALICE);
        mockMvc.perform(payment(token, "ALICE-1", "25.00"))
            .andExpect(status().isOk());
        assertEquals(1, StubCoreBanking.calls("/api/v1/transaction/util-payment").size());
        assertEquals("Bearer " + token, StubCoreBanking.calls("/api/v1/transaction/util-payment").get(0).authorization());
    }

    @Test
    void paymentHistoryIsScopedToCaller() throws Exception {
        mockMvc.perform(payment(TestJwt.customer(ALICE), "ALICE-1", "10.00")).andExpect(status().isOk());
        mockMvc.perform(payment(TestJwt.customer(BOB), "BOB-1", "20.00")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/utility-payment").header("Authorization", "Bearer " + TestJwt.customer(ALICE))
                .header("X-Auth-Id", TestJwt.subjectOf(BOB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].account").value("ALICE-1"));

        mockMvc.perform(get("/api/v1/utility-payment").header("Authorization", "Bearer " + TestJwt.staff("ib_admin@javatodev.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    private static MockHttpServletRequestBuilder payment(String token, String account, String amount) {
        MockHttpServletRequestBuilder builder = post("/api/v1/utility-payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"providerId\":1,\"account\":\"" + account + "\",\"referenceNumber\":\"REF-1\",\"amount\":" + amount + "}");
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }
}
