package com.javatodev.finance;

import com.javatodev.finance.model.dto.Status;
import com.javatodev.finance.model.dto.UserUpdateRequest;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.model.repository.UserRepository;
import com.javatodev.finance.service.KeycloakUserService;
import com.javatodev.finance.service.UserService;
import com.javatodev.finance.service.rest.BankingCoreRestClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceApprovalSafetyRegressionTest {
    private static final long USER_ID = 1L;
    private static final String AUTH_ID = "keycloak-customer-1";

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"DISABLED", "BLACKLIST", "PENDING"})
    void revokedStatusesDisableUserRemoveRoleAndLogout(Status status) {
        KeycloakUserService keycloakUsers = mock(KeycloakUserService.class);
        UserRepository repository = mock(UserRepository.class);
        BankingCoreRestClient core = mock(BankingCoreRestClient.class);
        UserEntity entity = approvedUser();
        UserRepresentation representation = userRepresentation(true);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(repository.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(repository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(keycloakUsers.readUser(AUTH_ID)).thenReturn(representation);
        UserService service = new UserService(keycloakUsers, repository, core);

        service.updateUser(USER_ID, update(status));

        List<String> invokedMethods = mockingDetails(keycloakUsers).getInvocations().stream()
            .map(invocation -> invocation.getMethod().getName())
            .toList();
        assertAll(
            () -> verify(keycloakUsers).updateUser(argThat(updated -> Boolean.FALSE.equals(updated.isEnabled()))),
            () -> verify(keycloakUsers, never()).grantBankCustomerRole(AUTH_ID),
            () -> assertTrue(invokedWith(keycloakUsers, "revokeBankCustomerRole", AUTH_ID),
                "expected revokeBankCustomerRole(" + AUTH_ID + ") but invoked " + invokedMethods),
            () -> assertTrue(invokedWith(keycloakUsers, "logoutUser", AUTH_ID),
                "expected logoutUser(" + AUTH_ID + ") but invoked " + invokedMethods)
        );
    }

    @Test
    void approvedStatusStillEnablesUserAndGrantsBankCustomerRole() {
        KeycloakUserService keycloakUsers = mock(KeycloakUserService.class);
        UserRepository repository = mock(UserRepository.class);
        BankingCoreRestClient core = mock(BankingCoreRestClient.class);
        UserEntity entity = approvedUser();
        entity.setStatus(Status.PENDING);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(repository.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(repository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(keycloakUsers.readUser(AUTH_ID)).thenReturn(userRepresentation(false));
        UserService service = new UserService(keycloakUsers, repository, core);

        service.updateUser(USER_ID, update(Status.APPROVED));

        verify(keycloakUsers).updateUser(argThat(updated ->
            Boolean.TRUE.equals(updated.isEnabled()) && Boolean.TRUE.equals(updated.isEmailVerified())));
        verify(keycloakUsers).grantBankCustomerRole(AUTH_ID);
    }

    @Test
    void failedApprovalFlushPreventsKeycloakMutations() {
        KeycloakUserService keycloakUsers = mock(KeycloakUserService.class);
        UserRepository repository = mock(UserRepository.class);
        BankingCoreRestClient core = mock(BankingCoreRestClient.class);
        UserEntity entity = approvedUser();
        entity.setStatus(Status.PENDING);
        IllegalStateException flushFailure = new IllegalStateException("database flush failed");
        when(repository.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(repository.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(repository.saveAndFlush(any(UserEntity.class))).thenThrow(flushFailure);
        UserService service = new UserService(keycloakUsers, repository, core);

        RuntimeException observed = null;
        try {
            service.updateUser(USER_ID, update(Status.APPROVED));
        } catch (RuntimeException exception) {
            observed = exception;
        }

        RuntimeException actual = observed;
        assertAll(
            () -> assertSame(flushFailure, actual,
                "expected saveAndFlush failure before Keycloak; observed " + actual),
            () -> verify(repository).saveAndFlush(any(UserEntity.class)),
            () -> verifyNoInteractions(keycloakUsers)
        );
    }

    @Test
    void keycloakGrantFailurePropagatesAfterDatabaseFlush() {
        KeycloakUserService keycloakUsers = mock(KeycloakUserService.class);
        UserRepository repository = mock(UserRepository.class);
        BankingCoreRestClient core = mock(BankingCoreRestClient.class);
        UserEntity entity = approvedUser();
        entity.setStatus(Status.PENDING);
        UserRepresentation representation = userRepresentation(false);
        RuntimeException grantFailure = new IllegalStateException("Keycloak grant failed");
        when(repository.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(keycloakUsers.readUser(AUTH_ID)).thenReturn(representation);
        doThrow(grantFailure).when(keycloakUsers).grantBankCustomerRole(AUTH_ID);
        UserService service = new UserService(keycloakUsers, repository, core);

        RuntimeException observed = assertThrows(RuntimeException.class,
            () -> service.updateUser(USER_ID, update(Status.APPROVED)));

        InOrder order = inOrder(repository, keycloakUsers);
        assertAll(
            () -> assertSame(grantFailure, observed),
            () -> order.verify(repository).saveAndFlush(any(UserEntity.class)),
            () -> order.verify(keycloakUsers).readUser(AUTH_ID),
            () -> order.verify(keycloakUsers).updateUser(any(UserRepresentation.class)),
            () -> order.verify(keycloakUsers).grantBankCustomerRole(AUTH_ID)
        );
    }

    @Test
    void repeatedApprovalRollbackDoesNotRevokeExistingCustomer() {
        KeycloakUserService keycloakUsers = mock(KeycloakUserService.class);
        UserRepository repository = mock(UserRepository.class);
        BankingCoreRestClient core = mock(BankingCoreRestClient.class);
        UserEntity entity = approvedUser();
        RuntimeException grantFailure = new IllegalStateException("Keycloak grant failed");
        when(repository.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(keycloakUsers.readUser(AUTH_ID)).thenReturn(userRepresentation(true));
        when(keycloakUsers.hasBankCustomerRole(AUTH_ID)).thenReturn(true);
        doThrow(grantFailure).when(keycloakUsers).grantBankCustomerRole(AUTH_ID);
        UserService service = new UserService(keycloakUsers, repository, core);

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertSame(grantFailure, assertThrows(RuntimeException.class,
                () -> service.updateUser(USER_ID, update(Status.APPROVED))));
            TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(keycloakUsers, never()).revokeBankCustomerRole(AUTH_ID);
            verify(keycloakUsers, never()).updateUser(argThat(updated -> Boolean.FALSE.equals(updated.isEnabled())));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void failedReapprovalOfKeycloakDisabledCustomerStaysDisabled() {
        KeycloakUserService keycloakUsers = mock(KeycloakUserService.class);
        UserRepository repository = mock(UserRepository.class);
        BankingCoreRestClient core = mock(BankingCoreRestClient.class);
        UserEntity entity = approvedUser();
        RuntimeException grantFailure = new IllegalStateException("Keycloak grant failed");
        when(repository.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(keycloakUsers.readUser(AUTH_ID)).thenReturn(userRepresentation(false));
        when(keycloakUsers.hasBankCustomerRole(AUTH_ID)).thenReturn(true);
        doThrow(grantFailure).when(keycloakUsers).grantBankCustomerRole(AUTH_ID);
        List<Boolean> enabledStates = new ArrayList<>();
        doAnswer(call -> {
            enabledStates.add(((UserRepresentation) call.getArgument(0)).isEnabled());
            return null;
        }).when(keycloakUsers).updateUser(any(UserRepresentation.class));
        UserService service = new UserService(keycloakUsers, repository, core);

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertSame(grantFailure, assertThrows(RuntimeException.class,
                () -> service.updateUser(USER_ID, update(Status.APPROVED))));
            TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            assertFalse(enabledStates.isEmpty(), "expected an enable update before the grant failure");
            assertEquals(Boolean.FALSE, enabledStates.get(enabledStates.size() - 1),
                "expected rollback to restore the disabled state; observed " + enabledStates);
            verify(keycloakUsers, never()).revokeBankCustomerRole(AUTH_ID);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void successfulApprovalCommitDoesNotRunCompensation() {
        KeycloakUserService keycloakUsers = mock(KeycloakUserService.class);
        UserRepository repository = mock(UserRepository.class);
        BankingCoreRestClient core = mock(BankingCoreRestClient.class);
        UserEntity entity = approvedUser();
        entity.setStatus(Status.PENDING);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(keycloakUsers.readUser(AUTH_ID)).thenReturn(userRepresentation(false));
        when(keycloakUsers.hasBankCustomerRole(AUTH_ID)).thenReturn(false);
        UserService service = new UserService(keycloakUsers, repository, core);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateUser(USER_ID, update(Status.APPROVED));
            TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

            verify(keycloakUsers, never()).revokeBankCustomerRole(AUTH_ID);
            verify(keycloakUsers, never()).updateUser(argThat(updated -> Boolean.FALSE.equals(updated.isEnabled())));
            verify(keycloakUsers).grantBankCustomerRole(AUTH_ID);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void pendingApprovalRollbackStillCompensatesKeycloak() {
        KeycloakUserService keycloakUsers = mock(KeycloakUserService.class);
        UserRepository repository = mock(UserRepository.class);
        BankingCoreRestClient core = mock(BankingCoreRestClient.class);
        UserEntity entity = approvedUser();
        entity.setStatus(Status.PENDING);
        RuntimeException grantFailure = new IllegalStateException("Keycloak grant failed");
        when(repository.findById(USER_ID)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(keycloakUsers.readUser(AUTH_ID)).thenReturn(userRepresentation(false));
        when(keycloakUsers.hasBankCustomerRole(AUTH_ID)).thenReturn(false);
        List<Boolean> enabledStates = new ArrayList<>();
        doAnswer(call -> {
            enabledStates.add(((UserRepresentation) call.getArgument(0)).isEnabled());
            return null;
        }).when(keycloakUsers).updateUser(any(UserRepresentation.class));
        doThrow(grantFailure).when(keycloakUsers).grantBankCustomerRole(AUTH_ID);
        UserService service = new UserService(keycloakUsers, repository, core);

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertSame(grantFailure, assertThrows(RuntimeException.class,
                () -> service.updateUser(USER_ID, update(Status.APPROVED))));
            TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(keycloakUsers).revokeBankCustomerRole(AUTH_ID);
            assertEquals(List.of(true, false), enabledStates,
                "expected enable then rollback-disable calls; observed " + enabledStates);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void userUpdateParticipatesInSpringTransactions() throws Exception {
        Method updateUser = UserService.class.getMethod("updateUser", Long.class, UserUpdateRequest.class);

        assertNotNull(updateUser.getAnnotation(org.springframework.transaction.annotation.Transactional.class),
            "updateUser must be annotated with Spring @Transactional");
    }

    private UserEntity approvedUser() {
        UserEntity entity = new UserEntity();
        entity.setId(USER_ID);
        entity.setAuthId(AUTH_ID);
        entity.setStatus(Status.APPROVED);
        return entity;
    }

    private UserRepresentation userRepresentation(boolean enabled) {
        UserRepresentation representation = new UserRepresentation();
        representation.setId(AUTH_ID);
        representation.setEmail("customer@example.test");
        representation.setEnabled(enabled);
        return representation;
    }

    private UserUpdateRequest update(Status status) {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setStatus(status);
        return request;
    }

    private boolean invokedWith(KeycloakUserService keycloakUsers, String methodName, String argument) {
        return mockingDetails(keycloakUsers).getInvocations().stream().anyMatch(invocation ->
            invocation.getMethod().getName().equals(methodName)
                && invocation.getArguments().length == 1
                && argument.equals(invocation.getArguments()[0]));
    }
}
