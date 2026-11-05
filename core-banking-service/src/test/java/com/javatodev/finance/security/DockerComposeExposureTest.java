package com.javatodev.finance.security;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * sfind-ac7cde3a: backend services must only be reachable through the gateway, so the Compose file
 * must not publish their ports on the host.
 */
class DockerComposeExposureTest {

    private static final List<String> BACKEND_SERVICES = List.of(
        "internet-banking-user-service",
        "internet-banking-fund-transfer-service",
        "internet-banking-utility-payment-service",
        "core-banking-service");

    @Test
    @SuppressWarnings("unchecked")
    void backendServicesDoNotPublishHostPorts() throws Exception {
        try (InputStream in = Files.newInputStream(Path.of("..", "docker-compose", "docker-compose.yml"))) {
            Map<String, Object> compose = new Yaml().load(in);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            for (String service : BACKEND_SERVICES) {
                Map<String, Object> definition = (Map<String, Object>) services.get(service);
                assertNotNull(definition, service + " is missing from docker-compose.yml");
                assertFalse(definition.containsKey("ports"), service + " publishes host ports " + definition.get("ports"));
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void serviceRegistryIsNotPublishedOnTheHost() throws Exception {
        try (InputStream in = Files.newInputStream(Path.of("..", "docker-compose", "docker-compose.yml"))) {
            Map<String, Object> services = (Map<String, Object>) ((Map<String, Object>) new Yaml().load(in)).get("services");
            Map<String, Object> registry = (Map<String, Object>) services.get("internet-banking-service-registry");
            assertNotNull(registry, "internet-banking-service-registry is missing from docker-compose.yml");
            assertFalse(registry.containsKey("ports"), "unauthenticated Eureka registry publishes host ports " + registry.get("ports"));
        }
    }
}
