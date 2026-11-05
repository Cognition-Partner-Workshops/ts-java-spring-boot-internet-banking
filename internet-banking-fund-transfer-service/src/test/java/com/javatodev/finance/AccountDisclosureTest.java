package com.javatodev.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.controller.FundTransferController;
import com.javatodev.finance.exception.GlobalExceptionHandler;
import com.javatodev.finance.model.dto.FundTransfer;
import com.javatodev.finance.model.dto.response.AccountResponse;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.service.FundTransferService;
import com.javatodev.finance.service.rest.client.BankingCoreFeignClient;
import feign.Feign;
import feign.Response;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class AccountDisclosureTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void historyEndpointMasksAccountsWithoutChangingInternalValues() throws Exception {
        FundTransfer record = new FundTransfer();
        record.setFromAccount("001234567890"); record.setToAccount("009876543210");
        FundTransferService service = mock(FundTransferService.class);
        when(service.readAllTransfers(any(Pageable.class))).thenReturn(List.of(record));
        var mvc = MockMvcBuilders.standaloneSetup(new FundTransferController(service))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
        String body = mvc.perform(get("/api/v1/transfer")).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("001234567890"), body);
        assertFalse(body.contains("009876543210"), body);
        assertEquals("****7890", json.readTree(body).get(0).get("fromAccount").asText());
        assertEquals("001234567890", record.getFromAccount());
    }

    @Test
    void feignAccountResponseAcceptsMaskedCoreNumbersAndKeepsMetadata() throws Exception {
        AccountResponse account = json.readValue("{\"number\":\"****7890\",\"id\":1,\"actualBalance\":100}", AccountResponse.class);
        assertEquals("****7890", account.getNumber());
        assertEquals(1L, account.getId());
        assertEquals(100, account.getActualBalance().intValue());
    }

    @Test
    void actualFeignCodecKeepsFullLookupAndTransferInputsWhileDecodingMaskedNumbers() throws Exception {
        var client = Feign.builder().contract(new SpringMvcContract())
            .decoder(new SpringDecoder(() -> new HttpMessageConverters()))
            .encoder(new SpringEncoder(() -> new HttpMessageConverters()))
            .client((request, options) -> {
                if (request.httpMethod() == feign.Request.HttpMethod.GET) {
                    assertTrue(request.url().endsWith("/bank-account/001234567890"), request.url());
                    return Response.builder().request(request).status(200)
                        .headers(Map.of("Content-Type", List.of("application/json")))
                        .body("{\"number\":\"****7890\",\"actualBalance\":100}", StandardCharsets.UTF_8).build();
                }
                String body = new String(request.body(), StandardCharsets.UTF_8);
                assertTrue(body.contains("001234567890"), body);
                assertFalse(body.contains("****"), body);
                return Response.builder().request(request).status(204).headers(Map.of()).build();
            }).target(BankingCoreFeignClient.class, "http://fs09-core");
        assertEquals("****7890", client.readAccount("001234567890").getNumber());
        var request = new ObjectMapper().readValue("{\"fromAccount\":\"001234567890\",\"toAccount\":\"009876543210\",\"amount\":1}", FundTransferRequest.class);
        client.fundTransfer(request);
    }

    @Test
    void customerAndFeignRequestBodiesKeepFullNumbers() throws Exception {
        String body = "{\"fromAccount\":\"001234567890\",\"toAccount\":\"009876543210\",\"amount\":1}";
        FundTransferRequest request = json.readValue(body, FundTransferRequest.class);
        assertEquals("001234567890", request.getFromAccount());
        assertEquals("001234567890", json.readTree(json.writeValueAsString(request)).get("fromAccount").asText());
    }

    @Test
    void emptyHistoryAndUnexpectedErrorsAreSafe() throws Exception {
        FundTransferService service = mock(FundTransferService.class);
        when(service.readAllTransfers(any(Pageable.class))).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new FundTransferController(service))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        assertEquals("[]", mvc.perform(get("/api/v1/transfer")).andReturn().getResponse().getContentAsString());
        when(service.readAllTransfers(any(Pageable.class))).thenThrow(new IllegalStateException("001234567890"));
        String body = mvc.perform(get("/api/v1/transfer")).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("001234567890"), body);
    }
}
