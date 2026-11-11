package com.javatodev.finance.controller;

import com.javatodev.finance.service.TransactionService;
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

class TransactionControllerValidationTest {

    private TransactionService transactionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        transactionService = mock(TransactionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TransactionController(transactionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsFundTransferWithZeroAmount() throws Exception {
        mockMvc.perform(post("/api/v1/transaction/fund-transfer").contentType(MediaType.APPLICATION_JSON).content("{\"fromAccount\":\"A1\",\"toAccount\":\"A2\",\"amount\":0}"))
                .andExpect(status().isBadRequest());
        verify(transactionService, never()).fundTransfer(any());
    }

    @Test
    void rejectsFundTransferWithoutAccounts() throws Exception {
        mockMvc.perform(post("/api/v1/transaction/fund-transfer").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10}"))
                .andExpect(status().isBadRequest());
        verify(transactionService, never()).fundTransfer(any());
    }

    @Test
    void rejectsUtilityPaymentWithNegativeAmount() throws Exception {
        mockMvc.perform(post("/api/v1/transaction/util-payment").contentType(MediaType.APPLICATION_JSON).content("{\"providerId\":1,\"amount\":-1,\"referenceNumber\":\"R1\",\"account\":\"A1\"}"))
                .andExpect(status().isBadRequest());
        verify(transactionService, never()).utilPayment(any());
    }

    @Test
    void rejectsUtilityPaymentWithoutAccount() throws Exception {
        mockMvc.perform(post("/api/v1/transaction/util-payment").contentType(MediaType.APPLICATION_JSON).content("{\"providerId\":1,\"amount\":10,\"referenceNumber\":\"R1\"}"))
                .andExpect(status().isBadRequest());
        verify(transactionService, never()).utilPayment(any());
    }

}
