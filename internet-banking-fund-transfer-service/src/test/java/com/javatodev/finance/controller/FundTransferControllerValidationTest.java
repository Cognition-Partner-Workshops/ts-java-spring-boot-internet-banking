package com.javatodev.finance.controller;

import com.javatodev.finance.service.FundTransferService;
import com.javatodev.finance.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FundTransferControllerValidationTest {

    private FundTransferService fundTransferService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        fundTransferService = mock(FundTransferService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FundTransferController(fundTransferService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsMissingAccounts() throws Exception {
        mockMvc.perform(post("/api/v1/transfer").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10}"))
                .andExpect(status().isBadRequest());
        verify(fundTransferService, never()).fundTransfer(any());
    }

    @Test
    void rejectsNegativeAmount() throws Exception {
        mockMvc.perform(post("/api/v1/transfer").contentType(MediaType.APPLICATION_JSON).content("{\"fromAccount\":\"A1\",\"toAccount\":\"A2\",\"amount\":-5}"))
                .andExpect(status().isBadRequest());
        verify(fundTransferService, never()).fundTransfer(any());
    }

    @Test
    void rejectsMissingAmount() throws Exception {
        mockMvc.perform(post("/api/v1/transfer").contentType(MediaType.APPLICATION_JSON).content("{\"fromAccount\":\"A1\",\"toAccount\":\"A2\"}"))
                .andExpect(status().isBadRequest());
        verify(fundTransferService, never()).fundTransfer(any());
    }

}
