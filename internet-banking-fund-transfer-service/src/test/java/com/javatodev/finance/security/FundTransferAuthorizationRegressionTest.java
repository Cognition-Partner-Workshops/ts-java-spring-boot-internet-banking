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
 * Regression tests for sfind-640f8ec8 (transfer from an account the caller does not own),
 * sfind-cb04485e (transfer history of every customer) and sfind-821ac1aa (negative / sub-cent amounts).
 */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.cloud.config.enabled=false"
})
@AutoConfigureMockMvc
class FundTransferAuthorizationRegressionTest {

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
        jdbcTemplate.update("DELETE FROM fund_transfer");
        StubCoreBanking.reset();
    }

    @Test
    void customerCannotTransferFromAccountTheyDoNotOwn() throws Exception {
        mockMvc.perform(transfer(TestJwt.customer(ALICE), "BOB-1", "ALICE-1", "100.00"))
            .andExpect(status().isForbidden());
        assertEquals(0, StubCoreBanking.calls("/api/v1/transaction/").size(), "transfer debiting BOB-1 was forwarded to core banking");
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fund_transfer", Integer.class));
    }

    @Test
    void transferRequiresAuthenticatedCaller() throws Exception {
        mockMvc.perform(transfer(null, "BOB-1", "ALICE-1", "100.00"))
            .andExpect(status().isUnauthorized());
        assertEquals(0, StubCoreBanking.calls("/api/v1/transaction/").size());
    }

    @Test
    void invalidAmountsAreRejectedBeforeReachingCoreBanking() throws Exception {
        mockMvc.perform(transfer(TestJwt.customer(ALICE), "ALICE-1", "BOB-1", "-500.00")).andExpect(status().isBadRequest());
        mockMvc.perform(transfer(TestJwt.customer(ALICE), "ALICE-1", "BOB-1", "0.005")).andExpect(status().isBadRequest());
        mockMvc.perform(transfer(TestJwt.customer(ALICE), "ALICE-1", "ALICE-1", "10.00")).andExpect(status().isBadRequest());
        assertEquals(0, StubCoreBanking.calls("/api/v1/transaction/").size());
    }

    @Test
    void ownerTransferIsForwardedWithCallerToken() throws Exception {
        String token = TestJwt.customer(ALICE);
        mockMvc.perform(transfer(token, "ALICE-1", "BOB-1", "100.00").header("X-Auth-Id", "spoofed"))
            .andExpect(status().isOk());
        assertEquals(1, StubCoreBanking.calls("/api/v1/transaction/fund-transfer").size());
        assertEquals("Bearer " + token, StubCoreBanking.calls("/api/v1/transaction/fund-transfer").get(0).authorization());
    }

    @Test
    void transferHistoryIsScopedToCaller() throws Exception {
        mockMvc.perform(transfer(TestJwt.customer(ALICE), "ALICE-1", "BOB-1", "10.00")).andExpect(status().isOk());
        mockMvc.perform(transfer(TestJwt.customer(BOB), "BOB-1", "ALICE-1", "20.00")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/transfer").header("Authorization", "Bearer " + TestJwt.customer(ALICE))
                .header("X-Auth-Id", TestJwt.subjectOf(BOB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].fromAccount").value("ALICE-1"));

        mockMvc.perform(get("/api/v1/transfer").header("Authorization", "Bearer " + TestJwt.staff("ib_admin@javatodev.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    private static MockHttpServletRequestBuilder transfer(String token, String from, String to, String amount) {
        MockHttpServletRequestBuilder builder = post("/api/v1/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fromAccount\":\"" + from + "\",\"toAccount\":\"" + to + "\",\"amount\":" + amount + ",\"authID\":\"spoofed\"}");
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }
}
