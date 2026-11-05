package com.javatodev.finance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class EffectiveSecurityConfigurationTest {
    @Test
    void packagedConfigurationDefinesJwtJwkSetUri() throws Exception {
        var source = new YamlPropertySourceLoader().load("packaged", new FileSystemResource("src/main/resources/application.yml")).get(0);
        assertNotNull(source.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"),
            "packaged application.yml must define the effective JWT JWK set URI");
    }
}
