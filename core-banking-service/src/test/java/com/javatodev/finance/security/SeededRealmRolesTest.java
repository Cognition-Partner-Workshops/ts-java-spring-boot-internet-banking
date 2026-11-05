package com.javatodev.finance.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The staff approval role must be provisioned out of band. No user, group or default role shipped in the
 * realm import (whose seeded credentials are committed) may carry bank_admin.
 */
class SeededRealmRolesTest {

    private static final String STAFF_ROLE = "bank_admin";

    @Test
    void shippedRealmGrantsTheStaffRoleToNobody() throws Exception {
        JsonNode realm = new ObjectMapper().readTree(Path.of("..", "docker-compose", "keycloak", "realm-export.json").toFile());
        List<String> holders = new ArrayList<>();
        for (JsonNode user : realm.path("users")) {
            if (contains(user.path("realmRoles"), STAFF_ROLE)) {
                holders.add("user " + user.path("username").asText());
            }
        }
        for (JsonNode group : realm.path("groups")) {
            if (contains(group.path("realmRoles"), STAFF_ROLE)) {
                holders.add("group " + group.path("name").asText());
            }
        }
        for (JsonNode role : realm.path("roles").path("realm")) {
            if (contains(role.path("composites").path("realm"), STAFF_ROLE)) {
                holders.add("composite role " + role.path("name").asText());
            }
        }
        assertTrue(contains(realm.path("defaultRoles"), STAFF_ROLE) == false, "bank_admin is a realm default role");
        assertEquals(List.of(), holders, "seeded principals that receive the staff approval role");
    }

    private static boolean contains(JsonNode array, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.asText())) {
                return true;
            }
        }
        return false;
    }
}
