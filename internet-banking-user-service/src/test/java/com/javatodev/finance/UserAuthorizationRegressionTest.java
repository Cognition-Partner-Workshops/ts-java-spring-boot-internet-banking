package com.javatodev.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.configuration.keycloak.KeycloakManager;
import com.javatodev.finance.configuration.feign.CustomFeignClientConfiguration;
import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.model.dto.Status;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.dto.UserUpdateRequest;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.model.repository.UserRepository;
import com.javatodev.finance.model.rest.response.UserResponse;
import com.javatodev.finance.service.KeycloakUserService;
import com.javatodev.finance.service.UserService;
import com.javatodev.finance.service.rest.BankingCoreRestClient;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.lang.reflect.Proxy;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Import(UserAuthorizationRegressionTest.TestJwtConfiguration.class)
class UserAuthorizationRegressionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @org.springframework.boot.test.mock.mockito.MockBean UserService userService;
    @org.springframework.boot.test.mock.mockito.MockBean KeycloakUserService keycloakUserService;

    @ParameterizedTest
    @MethodSource("nonAdminOperations")
    void customerAndReadOnlyCannotAdministerUsers(String method, String endpoint, String token) throws Exception {
        var request = "PATCH".equals(method) ? patch(endpoint) : get(endpoint);
        if ("PATCH".equals(method)) {
            request.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}");
        }
        MvcResult result = mvc.perform(request.header("Authorization", "Bearer " + token)).andReturn();
        assertAll(
            () -> assertDenied(result, method + " " + endpoint + " token " + token),
            () -> verifyNoInteractions(userService, keycloakUserService)
        );
    }

    static Stream<Arguments> nonAdminOperations() {
        return java.util.stream.Stream.of("customer", "readonly").flatMap(token ->
            java.util.stream.Stream.of(
                Arguments.of("PATCH", "/api/v1/bank-users/update/1", token),
                Arguments.of("GET", "/api/v1/bank-users", token),
                Arguments.of("GET", "/api/v1/bank-users/1", token)
            ));
    }

    @Test
    void registrationRemainsPublicAndAdminCanApprove() throws Exception {
        MvcResult registration = mvc.perform(post("/api/v1/bank-users/register")
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.test\"}")).andReturn();
        int registrationStatus = registration.getResponse().getStatus();
        assertFalse(registrationStatus == 401 || registrationStatus == 403,
            "registration expected public access but observed HTTP " + registrationStatus);

        mvc.perform(patch("/api/v1/bank-users/update/1")
            .header("Authorization", "Bearer admin")
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}")).andReturn();
        verify(userService).updateUser(eq(1L), argThat(update -> update.getStatus() == Status.APPROVED));
    }

    @Test
    void weakPasswordIsRejectedBeforeCoreOrKeycloakCalls() {
        KeycloakUserService keycloakUsers = mock(KeycloakUserService.class);
        UserRepository repository = mock(UserRepository.class);
        BankingCoreRestClient core = mock(BankingCoreRestClient.class);
        UserService service = new UserService(keycloakUsers, repository, core);
        User request = new User();
        request.setEmail("customer@example.test");
        request.setIdentification("NIC-1");
        request.setPassword("Weak1");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.createUser(request));

        assertTrue(thrown instanceof IllegalArgumentException,
            "weak password expected a validation error but observed " + thrown.getClass().getName());
        verifyNoInteractions(keycloakUsers, core);
    }

    @Test
    void mismatchedEmailAndMissingIdentificationUseOneUniformFailure() {
        KeycloakUserService keycloakUsers = mock(KeycloakUserService.class);
        UserRepository repository = mock(UserRepository.class);
        BankingCoreRestClient core = mock(BankingCoreRestClient.class);
        UserService service = new UserService(keycloakUsers, repository, core);
        when(keycloakUsers.readUserByEmail(anyString())).thenReturn(List.of());
        UserResponse mismatch = new UserResponse();
        mismatch.setId(1);
        mismatch.setEmail("different@example.test");
        when(core.readUser("NIC-MISMATCH")).thenReturn(mismatch);
        when(core.readUser("NIC-MISSING")).thenReturn(new UserResponse());

        SimpleBankingGlobalException mismatchError = assertThrows(SimpleBankingGlobalException.class,
            () -> service.createUser(registration("NIC-MISMATCH")));
        SimpleBankingGlobalException missingError = assertThrows(SimpleBankingGlobalException.class,
            () -> service.createUser(registration("NIC-MISSING")));

        assertEquals("USER-SERVICE-1003", mismatchError.getCode(),
            "mismatch error code observed " + mismatchError.getCode());
        assertEquals(mismatchError.getCode(), missingError.getCode());
        assertEquals(mismatchError.getMessage(), missingError.getMessage());
    }

    @Test
    void registrationResponseDoesNotEchoKeycloakAuthId() {
        KeycloakUserService keycloakUsers = mock(KeycloakUserService.class);
        UserRepository repository = mock(UserRepository.class);
        BankingCoreRestClient core = mock(BankingCoreRestClient.class);
        UserService service = new UserService(keycloakUsers, repository, core);
        when(keycloakUsers.readUserByEmail(anyString())).thenReturn(List.of(), List.of(userRepresentation("kc-sensitive-id")));
        when(keycloakUsers.createUser(any())).thenReturn(201);
        UserResponse coreUser = new UserResponse();
        coreUser.setId(1);
        coreUser.setEmail("customer@example.test");
        coreUser.setIdentificationNumber("NIC-1");
        when(core.readUser("NIC-1")).thenReturn(coreUser);
        when(repository.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));

        User registered = service.createUser(registration("NIC-1"));

        assertNull(registered.getAuthId(), "registration response contained authId " + registered.getAuthId());
        assertFalse(objectMapper.valueToTree(registered).has("authId"), "serialized registration response contained authId");
        assertEquals(Status.PENDING, registered.getStatus());
        assertEquals("customer@example.test", registered.getEmail());
        assertEquals("NIC-1", registered.getIdentification());
    }

    @Test
    void adminApprovalGrantsBankCustomerRealmRole() {
        KeycloakManager manager = mock(KeycloakManager.class);
        UserRepository repository = mock(UserRepository.class);
        BankingCoreRestClient core = mock(BankingCoreRestClient.class);
        RealmResource realm = mock(RealmResource.class);
        UsersResource users = mock(UsersResource.class);
        UserResource user = mock(UserResource.class);
        RoleMappingResource roleMappings = mock(RoleMappingResource.class);
        RoleScopeResource realmRoles = mock(RoleScopeResource.class);
        RolesResource roles = mock(RolesResource.class);
        RoleResource role = mock(RoleResource.class);
        UserEntity entity = new UserEntity();
        entity.setAuthId("keycloak-user-id");
        UserRepresentation representation = userRepresentation("keycloak-user-id");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(manager.getKeyCloakInstanceWithRealm()).thenReturn(realm);
        when(realm.users()).thenReturn(users);
        when(users.get("keycloak-user-id")).thenReturn(user);
        when(user.toRepresentation()).thenReturn(representation);
        when(user.roles()).thenReturn(roleMappings);
        when(roleMappings.realmLevel()).thenReturn(realmRoles);
        when(realm.roles()).thenReturn(roles);
        when(roles.get("bank-customer")).thenReturn(role);
        RoleRepresentation bankCustomer = new RoleRepresentation();
        bankCustomer.setName("bank-customer");
        when(role.toRepresentation()).thenReturn(bankCustomer);
        KeycloakUserService keycloakUsers = new KeycloakUserService(manager);
        UserService service = new UserService(keycloakUsers, repository, core);
        UserUpdateRequest update = new UserUpdateRequest();
        update.setStatus(Status.APPROVED);

        service.updateUser(1L, update);

        verify(realmRoles).add(argThat(list -> list.size() == 1 && "bank-customer".equals(list.get(0).getName())));
        verify(user).update(argThat(updated -> updated.isEnabled() && updated.isEmailVerified()));
    }

    @Test
    void registrationCoreFeignInterceptorCarriesServiceBearerToken() throws Exception {
        Class<?> providerType;
        try {
            providerType = Class.forName("com.javatodev.finance.configuration.feign.ServiceTokenProvider");
        } catch (ClassNotFoundException missing) {
            fail("injectable ServiceTokenProvider is missing");
            return;
        }
        Object provider = Proxy.newProxyInstance(providerType.getClassLoader(), new Class<?>[]{providerType},
            (proxy, method, args) -> method.getReturnType() == String.class ? "stub-service-token" : null);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            RootBeanDefinition definition = new RootBeanDefinition(providerType);
            definition.setInstanceSupplier(() -> provider);
            context.registerBeanDefinition("serviceTokenProvider", definition);
            context.register(CustomFeignClientConfiguration.class);
            context.refresh();
            RequestTemplate template = new RequestTemplate();
            context.getBeansOfType(RequestInterceptor.class).values().forEach(interceptor -> interceptor.apply(template));
            assertTrue(template.headers().getOrDefault("Authorization", java.util.Set.of()).contains("Bearer stub-service-token"),
                "registration core Authorization header observed " + template.headers().get("Authorization"));
        }
    }

    private void assertDenied(MvcResult result, String label) throws Exception {
        int status = result.getResponse().getStatus();
        assertTrue(status == 401 || status == 403,
            label + " expected HTTP 401/403 but observed HTTP " + status + " body=" + result.getResponse().getContentAsString());
    }

    private User registration(String identification) {
        User user = new User();
        user.setEmail("customer@example.test");
        user.setIdentification(identification);
        user.setPassword("StrongPassword1");
        return user;
    }

    private UserRepresentation userRepresentation(String id) {
        UserRepresentation representation = new UserRepresentation();
        representation.setId(id);
        representation.setEmail("customer@example.test");
        return representation;
    }

    @TestConfiguration
    static class TestJwtConfiguration {
        @Bean
        JwtDecoder testJwtDecoder() {
            return token -> {
                List<String> roles = switch (token) {
                    case "readonly" -> List.of("read-only");
                    case "admin" -> List.of("bank-admin");
                    default -> List.of("bank-customer");
                };
                Instant now = Instant.now();
                return Jwt.withTokenValue(token).header("alg", "none").subject("user-sub")
                    .claim("preferred_username", "customer@example.test")
                    .claim("realm_access", Map.of("roles", roles))
                    .issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
            };
        }
    }
}
