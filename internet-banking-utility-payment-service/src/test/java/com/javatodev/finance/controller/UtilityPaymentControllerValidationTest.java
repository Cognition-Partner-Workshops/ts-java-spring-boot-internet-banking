package com.javatodev.finance.controller;

import com.javatodev.finance.service.UtilityPaymentService;
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

class UtilityPaymentControllerValidationTest {

    private UtilityPaymentService utilityPaymentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        utilityPaymentService = mock(UtilityPaymentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UtilityPaymentController(utilityPaymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsNegativeAmount() throws Exception {
        mockMvc.perform(post("/api/v1/utility-payment").contentType(MediaType.APPLICATION_JSON).content("{\"providerId\":1,\"amount\":-1,\"referenceNumber\":\"R1\",\"account\":\"A1\"}"))
                .andExpect(status().isBadRequest());
        verify(utilityPaymentService, never()).utilPayment(any());
    }

    @Test
    void rejectsMissingAccountAndReference() throws Exception {
        mockMvc.perform(post("/api/v1/utility-payment").contentType(MediaType.APPLICATION_JSON).content("{\"providerId\":1,\"amount\":10}"))
                .andExpect(status().isBadRequest());
        verify(utilityPaymentService, never()).utilPayment(any());
    }

    @Test
    void rejectsMissingProvider() throws Exception {
        mockMvc.perform(post("/api/v1/utility-payment").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10,\"referenceNumber\":\"R1\",\"account\":\"A1\"}"))
                .andExpect(status().isBadRequest());
        verify(utilityPaymentService, never()).utilPayment(any());
    }

}
