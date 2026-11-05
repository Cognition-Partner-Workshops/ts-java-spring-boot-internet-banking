package com.javatodev.finance;

import com.javatodev.finance.configuration.security.SecurityConfiguration;
import com.javatodev.finance.controller.AccountController;
import com.javatodev.finance.controller.UserController;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.service.AccountService;
import com.javatodev.finance.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = {UserController.class, AccountController.class})
@Import(SecurityConfiguration.class)
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private AccountService accountService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymousUserLookupIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/user/123456789"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gatewayIdentityHeaderAloneIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/user/123456789").header("X-Auth-Id", "some-user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousUserListIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousAccountLookupRemainsPermitted() throws Exception {
        mockMvc.perform(get("/api/v1/account/bank-account/123"))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserLookupIsPermitted() throws Exception {
        User user = new User();
        user.setIdentificationNumber("123456789");
        when(userService.readUser("123456789")).thenReturn(user);
        mockMvc.perform(get("/api/v1/user/123456789").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificationNumber").value("123456789"));
    }

    @Test
    void authenticatedEmptyUserListIsPermitted() throws Exception {
        when(userService.readUsers(any(Pageable.class))).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/user").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void invalidBearerTokenIsUnauthorized() throws Exception {
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid token"));
        mockMvc.perform(get("/api/v1/user/123456789").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }
}
