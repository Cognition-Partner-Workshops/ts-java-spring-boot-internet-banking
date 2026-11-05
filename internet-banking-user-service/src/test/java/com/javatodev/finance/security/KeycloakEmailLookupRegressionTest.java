package com.javatodev.finance.security;

import com.javatodev.finance.configuration.keycloak.KeycloakManager;
import com.javatodev.finance.service.KeycloakUserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for sfind-e3fbf61c: the "email already registered" check and the authId link after
 * registration used Keycloak's prefix search, so another person's login could match.
 */
class KeycloakEmailLookupRegressionTest {

    private UsersResource users;
    private KeycloakUserService keycloakUserService;

    @BeforeEach
    void setUp() {
        users = mock(UsersResource.class);
        RealmResource realm = mock(RealmResource.class);
        KeycloakManager manager = mock(KeycloakManager.class);
        when(manager.getKeyCloakInstanceWithRealm()).thenReturn(realm);
        when(realm.users()).thenReturn(users);
        keycloakUserService = new KeycloakUserService(manager);
    }

    @Test
    void prefixMatchOnAnotherUserIsNotTreatedAsTheSameEmail() {
        UserRepresentation other = user("kc-other", "bob@example.com.attacker.test");
        lenient().when(users.search(anyString())).thenReturn(List.of(other));
        lenient().when(users.searchByEmail(anyString(), eq(true))).thenReturn(List.of());

        assertEquals(List.of(), keycloakUserService.readUserByEmail("bob@example.com"));
    }

    @Test
    void exactEmailMatchIsReturned() {
        UserRepresentation bob = user("kc-bob", "Bob@Example.com");
        lenient().when(users.search(anyString())).thenReturn(List.of(bob));
        lenient().when(users.searchByEmail(anyString(), eq(true))).thenReturn(List.of(bob));

        assertEquals(List.of(bob), keycloakUserService.readUserByEmail("bob@example.com"));
    }

    private static UserRepresentation user(String id, String email) {
        UserRepresentation user = new UserRepresentation();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(email);
        return user;
    }
}
