package com.javatodev.finance;

import com.javatodev.finance.model.dto.response.AccountResponse;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.entity.FundTransferEntity;
import com.javatodev.finance.model.repository.FundTransferRepository;
import com.javatodev.finance.service.rest.client.BankingCoreFeignClient;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false"})
@AutoConfigureMockMvc
@Import(FundTransferHttpRegressionTest.TestJwtConfiguration.class)
class FundTransferHttpRegressionTest {
    @Autowired MockMvc mvc;
    @MockBean FundTransferRepository repository;
    @MockBean BankingCoreFeignClient core;

    @BeforeEach
    void setUp() {
        reset(repository, core);
        when(repository.count()).thenReturn(0L);
        when(repository.save(any(FundTransferEntity.class))).thenAnswer(call -> call.getArgument(0));
        AccountResponse account = new AccountResponse();
        account.setNumber(1001L);
        account.setStatus("ACTIVE");
        account.setAvailableBalance(new BigDecimal("100"));
        when(core.readAccount(anyString())).thenReturn(account);
        FundTransferResponse response = new FundTransferResponse();
        response.setTransactionId("txn-1");
        when(core.fundTransfer(any())).thenReturn(response);
    }

    @Test
    void nonOwnerIsDeniedBeforeSavingOrCallingMoneyEndpoint() throws Exception {
        when(core.readAccount("DEBIT-1")).thenThrow(forbidden());
        MvcResult result = post("/api/v1/transfer", "{\"fromAccount\":\"DEBIT-1\",\"toAccount\":\"CREDIT-2\",\"amount\":10}");
        assertAll(
            () -> assertDenied(result, "fund-transfer non-owner precheck"),
            () -> assertEquals(0, repository.count(), "fund-transfer persisted row count observed " + repository.count()),
            () -> verify(repository, never()).save(any(FundTransferEntity.class)),
            () -> verify(core, never()).fundTransfer(any())
        );
    }

    @Test
    void pendingUserCannotCallMoneyEndpointAndReadOnlyCannotReadHistory() throws Exception {
        MvcResult pending = post("/api/v1/transfer",
            "{\"fromAccount\":\"DEBIT-1\",\"toAccount\":\"CREDIT-2\",\"amount\":10}", "pending", null, null);
        assertDenied(pending, "pending fund-transfer");
        MvcResult history = mvc.perform(get("/api/v1/transfer").header("Authorization", "Bearer readonly")).andReturn();
        assertDenied(history, "read-only transfer history");
        verify(repository, never()).save(any(FundTransferEntity.class));
        verify(core, never()).fundTransfer(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-10", "0", "0.001", "null"})
    void invalidAmountsReturn400WithoutPersistenceOrCoreMoneyCall(String amount) throws Exception {
        MvcResult result = post("/api/v1/transfer",
            "{\"fromAccount\":\"DEBIT-1\",\"toAccount\":\"CREDIT-2\",\"amount\":" + amount + "}");
        int status = result.getResponse().getStatus();
        assertEquals(400, status, "fund-transfer amount " + amount + " expected HTTP 400 but observed " + status);
        verify(repository, never()).save(any(FundTransferEntity.class));
        verify(core, never()).fundTransfer(any());
    }

    @Test
    void ownerTransferSucceedsAndIgnoresForgedAuthId() throws Exception {
        MvcResult result = post("/api/v1/transfer",
            "{\"fromAccount\":\"DEBIT-1\",\"toAccount\":\"CREDIT-2\",\"amount\":10,\"authID\":\"attacker\"}",
            "X-Auth-Id", "attacker");
        assertEquals(200, result.getResponse().getStatus(), "owner transfer observed HTTP " + result.getResponse().getStatus());
        verify(core).fundTransfer(any());
        verify(repository, times(2)).save(any(FundTransferEntity.class));
        verify(repository, times(2)).save(argThat(entity -> "jwt-subject".equals(ownerId(entity))));
    }

    private MvcResult post(String path, String body) throws Exception {
        return post(path, body, "customer", null, null);
    }

    private MvcResult post(String path, String body, String header, String value) throws Exception {
        return post(path, body, "customer", header, value);
    }

    private MvcResult post(String path, String body, String token, String header, String value) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(body);
        if (header != null) request.header(header, value);
        return mvc.perform(request).andReturn();
    }

    private void assertDenied(MvcResult result, String label) throws Exception {
        int status = result.getResponse().getStatus();
        assertEquals(403, status, label + " expected HTTP 403 but observed HTTP " + status
            + " body=" + result.getResponse().getContentAsString());
    }

    private String ownerId(FundTransferEntity entity) {
        try {
            var field = entity.getClass().getDeclaredField("ownerId");
            field.setAccessible(true);
            return (String) field.get(entity);
        } catch (ReflectiveOperationException ignored) {
            return entity.getCreatedBy();
        }
    }

    private FeignException forbidden() {
        Request request = Request.create(Request.HttpMethod.GET, "http://core", Map.of(), null, null, null);
        Response response = Response.builder().status(403).reason("Forbidden").request(request).headers(Map.of()).build();
        return FeignException.errorStatus("readAccount", response);
    }

    @TestConfiguration
    static class TestJwtConfiguration {
        @Bean
        JwtDecoder testJwtDecoder() {
            return token -> {
                Instant now = Instant.now();
                List<String> roles = switch (token) {
                    case "pending", "readonly" -> List.of();
                    default -> List.of("bank-customer");
                };
                return Jwt.withTokenValue(token).header("alg", "none").subject("jwt-subject")
                    .claim("preferred_username", "customer@example.test")
                    .claim("realm_access", Map.of("roles", roles))
                    .issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
            };
        }
    }
}
