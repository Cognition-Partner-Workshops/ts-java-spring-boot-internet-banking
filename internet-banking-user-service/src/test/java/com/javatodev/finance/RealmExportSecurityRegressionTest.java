package com.javatodev.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class RealmExportSecurityRegressionTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[A-Z0-9_]+}");

    @Test
    void realmExportUsesExternalSecretsAndSecureAuthenticationSettings() throws Exception {
        JsonNode realm = new ObjectMapper().readTree(Path.of("../docker-compose/keycloak/realm-export.json").toFile());
        for (JsonNode client : realm.path("clients")) {
            if ("javatodev-internet-banking-api-client".equals(client.path("clientId").asText())
                || "javatodev-internet-banking-kc-api-client".equals(client.path("clientId").asText())) {
                JsonNode secret = client.get("secret");
                assertTrue(secret == null || PLACEHOLDER.matcher(secret.asText()).matches(),
                    "literal secret remains for " + client.path("clientId").asText());
            }
        }
        for (JsonNode user : realm.path("users")) {
            assertFalse(user.has("credentials"), "static credential remains for user " + user.path("username").asText());
        }
        assertTrue(realm.path("bruteForceProtected").asBoolean(), "bruteForceProtected observed false");
        assertTrue(realm.path("failureFactor").asInt() <= 5, "failureFactor observed " + realm.path("failureFactor").asInt());
        assertTrue(realm.path("passwordPolicy").asText().contains("length(12)"), "passwordPolicy observed without length(12)");

        assertFalse(directAccessGrants(realm, "javatodev-internet-banking-kc-api-client"),
            "service client directAccessGrantsEnabled observed true");
        assertFalse(directAccessGrants(realm, "admin-cli"), "admin-cli directAccessGrantsEnabled observed true");
        assertTrue(directAccessGrants(realm, "javatodev-internet-banking-api-client"),
            "customer client password grant is not enabled");
        assertRealmRole(realm, "bank-customer");
        assertRealmRole(realm, "bank-admin");
        assertRealmRole(realm, "banking-service");
        assertTrue(hasUserWithRealmRole(realm, "bank-admin"), "no realm user has bank-admin role");
        JsonNode serviceAccount = user(realm, "service-account-javatodev-internet-banking-kc-api-client");
        assertTrue(contains(serviceAccount.path("realmRoles"), "banking-service"),
            "service account lacks banking-service role");
        assertTrue(contains(serviceAccount.path("clientRoles").path("realm-management"), "view-realm"),
            "service account lacks realm-management view-realm");
    }

    @Test
    void clientSecretPropertyPrefersEnvironmentVariableOverExternalConfig() throws Exception {
        Field clientSecret = com.javatodev.finance.configuration.keycloak.KeycloakProperties.class.getDeclaredField("clientSecret");
        Value value = clientSecret.getAnnotation(Value.class);
        assertNotNull(value, "clientSecret is not configured with @Value");
        assertEquals("${KC_API_CLIENT_SECRET:${app.config.keycloak.client-secret}}", value.value(),
            "clientSecret property expression observed " + value.value());
    }

    private boolean directAccessGrants(JsonNode realm, String clientId) {
        for (JsonNode client : realm.path("clients")) {
            if (clientId.equals(client.path("clientId").asText())) {
                return client.path("directAccessGrantsEnabled").asBoolean();
            }
        }
        fail("missing client " + clientId);
        return true;
    }

    private void assertRealmRole(JsonNode realm, String roleName) {
        assertTrue(contains(realm.path("roles").path("realm"), roleName), "realm role missing " + roleName);
    }

    private boolean hasUserWithRealmRole(JsonNode realm, String roleName) {
        for (JsonNode user : realm.path("users")) {
            if (contains(user.path("realmRoles"), roleName)) return true;
        }
        return false;
    }

    private boolean contains(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (expected.equals(value.isTextual() ? value.asText() : value.path("name").asText())) return true;
        }
        return false;
    }

    private JsonNode user(JsonNode realm, String username) {
        for (JsonNode user : realm.path("users")) {
            if (username.equals(user.path("username").asText())) return user;
        }
        fail("missing realm user " + username);
        return realm;
    }
}
