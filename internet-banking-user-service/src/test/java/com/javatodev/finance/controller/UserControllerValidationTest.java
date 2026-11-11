package com.javatodev.finance.controller;

import com.javatodev.finance.service.UserService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerValidationTest {

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsRegistrationWithInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/bank-users/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\",\"identification\":\"ID1\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest());
        verify(userService, never()).createUser(any());
    }

    @Test
    void rejectsRegistrationWithoutIdentificationOrPassword() throws Exception {
        mockMvc.perform(post("/api/v1/bank-users/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isBadRequest());
        verify(userService, never()).createUser(any());
    }

    @Test
    void rejectsUpdateWithoutStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/bank-users/update/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(userService, never()).updateUser(any(), any());
    }

}
