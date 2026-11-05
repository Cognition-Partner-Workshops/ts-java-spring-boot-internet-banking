package com.javatodev.finance.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test-only RS256 token issuer backed by an in-memory key pair, with a local JWKS endpoint.
 * Uses only JDK and Jackson classes so the regression tests also compile against the unfixed source.
 */
public final class TestJwt {

    public static final String CUSTOMER_CLIENT = "javatodev-internet-banking-api-client";
    public static final String INTERNAL_CLIENT = "javatodev-internet-banking-kc-api-client";
    public static final String STAFF_ROLE = "bank_admin";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final KeyPair TRUSTED = generate();
    private static final KeyPair UNTRUSTED = generate();
    private static HttpServer jwksServer;

    private TestJwt() {
    }

    public static synchronized String jwkSetUri() {
        if (jwksServer == null) {
            try {
                jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                byte[] body = jwks().getBytes(StandardCharsets.UTF_8);
                jwksServer.createContext("/certs", exchange -> {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
                jwksServer.start();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/certs";
    }

    public static String customer(String username) {
        return token(claims(username, CUSTOMER_CLIENT, List.of("default-roles-javatodev-internet-banking")), TRUSTED);
    }

    public static String staff(String username) {
        return token(claims(username, CUSTOMER_CLIENT, List.of("default-roles-javatodev-internet-banking", STAFF_ROLE)), TRUSTED);
    }

    public static String serviceAccount(String clientId) {
        return token(claims("service-account-" + clientId, clientId, List.of("default-roles-javatodev-internet-banking")), TRUSTED);
    }

    public static String forged(String username) {
        return token(claims(username, CUSTOMER_CLIENT, List.of(STAFF_ROLE)), UNTRUSTED);
    }

    public static String subjectOf(String username) {
        return UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static Map<String, Object> claims(String username, String clientId, List<String> roles) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "http://localhost:8080/realms/javatodev-internet-banking");
        claims.put("sub", subjectOf(username));
        claims.put("azp", clientId);
        claims.put("preferred_username", username);
        claims.put("iat", now);
        claims.put("exp", now + 300);
        claims.put("realm_access", Map.of("roles", roles));
        return claims;
    }

    private static String token(Map<String, Object> claims, KeyPair keyPair) {
        try {
            String header = b64(MAPPER.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT", "kid", "test-key")));
            String payload = b64(MAPPER.writeValueAsBytes(claims));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
            return header + "." + payload + "." + b64(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String jwks() {
        RSAPublicKey key = (RSAPublicKey) TRUSTED.getPublic();
        Map<String, Object> jwk = Map.of("kty", "RSA", "kid", "test-key", "use", "sig", "alg", "RS256",
            "n", b64(unsigned(key.getModulus().toByteArray())), "e", b64(unsigned(key.getPublicExponent().toByteArray())));
        try {
            return MAPPER.writeValueAsString(Map.of("keys", List.of(jwk)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] unsigned(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
