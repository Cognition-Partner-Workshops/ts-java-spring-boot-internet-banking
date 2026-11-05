package com.javatodev.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.controller.UserController;
import com.javatodev.finance.exception.GlobalExceptionHandler;
import com.javatodev.finance.model.rest.response.AccountResponse;
import com.javatodev.finance.model.rest.response.UserResponse;
import com.javatodev.finance.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class AccountDisclosureTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void nestedAccountResponsesMaskOnlyOutputNotDecodedInput() throws Exception {
        AccountResponse account = json.readValue("{\"number\":\"001234567890\"}", AccountResponse.class);
        UserResponse user = new UserResponse();
        user.setBankAccounts(List.of(account));
        assertEquals("****7890", json.readTree(json.writeValueAsString(user)).at("/bankAccounts/0/number").asText());
        assertEquals("001234567890", account.getNumber());
    }

    @Test
    void nullAndEmptyAccountsRemainSafe() throws Exception {
        AccountResponse account = new AccountResponse();
        assertTrue(json.readTree(json.writeValueAsString(account)).get("number").isNull());
        account.setNumber("");
        assertEquals("", json.readTree(json.writeValueAsString(account)).get("number").asText());
    }

    @Test
    void feignErrorsDoNotExposeAccountNumbersOrUrls() throws Exception {
        UserService service = mock(UserService.class);
        when(service.readUser(1L)).thenThrow(new IllegalStateException("GET /bank-account/001234567890"));
        var mvc = MockMvcBuilders.standaloneSetup(new UserController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        String body = mvc.perform(get("/api/v1/bank-users/1")).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("001234567890"), body);
    }
}
