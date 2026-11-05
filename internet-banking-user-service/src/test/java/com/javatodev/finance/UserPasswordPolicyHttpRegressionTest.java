package com.javatodev.finance;

import com.javatodev.finance.service.KeycloakUserService;
import com.javatodev.finance.service.rest.BankingCoreRestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "logging.level.com.javatodev.finance.controller.UserController=OFF"
})
@AutoConfigureMockMvc
class UserPasswordPolicyHttpRegressionTest {
    @Autowired MockMvc mvc;
    @MockBean KeycloakUserService keycloakUserService;
    @MockBean BankingCoreRestClient bankingCoreRestClient;

    @Test
    void weakRegistrationPasswordReturns400BeforeCoreOrKeycloakCalls() throws Exception {
        int status = mvc.perform(post("/api/v1/bank-users/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"new@example.test\",\"identification\":\"NIC-1\",\"password\":\"short\"}"))
            .andReturn().getResponse().getStatus();
        assertEquals(400, status, "weak registration password expected HTTP 400 but observed " + status);
        verifyNoInteractions(keycloakUserService, bankingCoreRestClient);
    }
}
