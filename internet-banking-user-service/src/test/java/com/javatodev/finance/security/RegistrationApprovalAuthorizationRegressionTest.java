package com.javatodev.finance.security;

import com.javatodev.finance.model.rest.response.UserResponse;
import com.javatodev.finance.service.KeycloakUserService;
import com.javatodev.finance.service.rest.BankingCoreRestClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for sfind-a9add981: approving a registration (enabling the Keycloak login) needs a staff role.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:user_service_security;NON_KEYWORDS=USER",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.cloud.config.enabled=false"
})
@AutoConfigureMockMvc
class RegistrationApprovalAuthorizationRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private KeycloakUserService keycloakUserService;

    @MockBean
    private BankingCoreRestClient bankingCoreRestClient;

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestJwt::jwkSetUri);
    }

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM user");
        jdbcTemplate.update("ALTER TABLE user ALTER COLUMN id RESTART WITH 1000");
        jdbcTemplate.update("INSERT INTO user (id, auth_id, identification, status, created_date, created_by, modified_date, modified_by, version) VALUES (1, 'kc-pending-user', 'ID-PENDING', 'PENDING', CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test', 0)");
        when(keycloakUserService.readUser(anyString())).thenReturn(new UserRepresentation());
        when(keycloakUserService.readUserByEmail(anyString())).thenReturn(List.of());
    }

    @Test
    void customerCannotApproveRegistration() throws Exception {
        mockMvc.perform(approve(TestJwt.customer("alice@example.com")))
            .andExpect(status().isForbidden());
        verify(keycloakUserService, never()).updateUser(any());
        assertEquals("PENDING", jdbcTemplate.queryForObject("SELECT status FROM user WHERE id = 1", String.class));
    }

    @Test
    void anonymousCallerCannotApproveRegistration() throws Exception {
        mockMvc.perform(approve(null))
            .andExpect(status().isUnauthorized());
        verify(keycloakUserService, never()).updateUser(any());
    }

    @Test
    void customerCannotListBankUsers() throws Exception {
        mockMvc.perform(get("/api/v1/bank-users").header("Authorization", "Bearer " + TestJwt.customer("alice@example.com")))
            .andExpect(status().isForbidden());
    }

    @Test
    void staffCanApproveRegistration() throws Exception {
        mockMvc.perform(approve(TestJwt.staff("ib_admin@javatodev.com")))
            .andExpect(status().isOk());
        verify(keycloakUserService).updateUser(any());
        assertEquals("APPROVED", jdbcTemplate.queryForObject("SELECT status FROM user WHERE id = 1", String.class));
    }

    @Test
    void registrationStaysPublic() throws Exception {
        int status = mockMvc.perform(post("/api/v1/bank-users/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@example.com\",\"identification\":\"ID-NEW\",\"password\":\"x\"}"))
            .andReturn().getResponse().getStatus();
        assertNotEquals(401, status);
        assertNotEquals(403, status);
    }

    /** sfind-66aba107: the public register route must not let the body pick which record is written. */
    @Test
    void registrationCannotOverwriteAnotherCustomersRecord() throws Exception {
        UserRepresentation created = new UserRepresentation();
        created.setId("kc-attacker-login");
        created.setEmail("new@example.com");
        when(keycloakUserService.readUserByEmail("new@example.com")).thenReturn(List.of(), List.of(created));
        when(keycloakUserService.createUser(any())).thenReturn(201);
        UserResponse coreUser = new UserResponse();
        coreUser.setId(5);
        coreUser.setEmail("new@example.com");
        coreUser.setIdentificationNumber("ID-NEW");
        when(bankingCoreRestClient.readUser("ID-NEW")).thenReturn(coreUser);

        mockMvc.perform(post("/api/v1/bank-users/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":1,\"version\":0,\"email\":\"new@example.com\",\"identification\":\"ID-NEW\",\"password\":\"x\",\"status\":\"APPROVED\"}"))
            .andExpect(status().isOk());

        assertEquals("kc-pending-user", jdbcTemplate.queryForObject("SELECT auth_id FROM user WHERE id = 1", String.class),
            "existing registration was relinked to another login");
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Integer.class));
        assertEquals("PENDING", jdbcTemplate.queryForObject("SELECT status FROM user WHERE auth_id = 'kc-attacker-login'", String.class));
    }

    private static MockHttpServletRequestBuilder approve(String token) {
        MockHttpServletRequestBuilder builder = patch("/api/v1/bank-users/update/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"APPROVED\"}");
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }
}
