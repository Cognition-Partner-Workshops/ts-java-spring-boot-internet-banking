package com.javatodev.finance.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double for core-banking-service. Account reads behave like the fixed core service (403 unless the
 * bearer token's preferred_username owns the account); money-movement endpoints behave like the unfixed
 * core service (accept anything) so the tests show whether the calling service forwards a request at all.
 */
public final class StubCoreBanking {

    public record Call(String method, String path, String authorization, String body) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> ACCOUNT_OWNERS = Map.of(
        "ALICE-1", "alice@example.com",
        "BOB-1", "bob@example.com");
    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static HttpServer server;

    private StubCoreBanking() {
    }

    public static synchronized String url() {
        if (server == null) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/", StubCoreBanking::handle);
                server.start();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public static List<Call> calls() {
        return List.copyOf(CALLS);
    }

    public static List<Call> calls(String pathPrefix) {
        return CALLS.stream().filter(call -> call.path().startsWith(pathPrefix)).toList();
    }

    public static void reset() {
        CALLS.clear();
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        CALLS.add(new Call(exchange.getRequestMethod(), path, authorization, body));
        if (path.startsWith("/api/v1/account/bank-account/")) {
            String number = path.substring("/api/v1/account/bank-account/".length());
            String owner = ACCOUNT_OWNERS.get(number);
            if (owner == null || !owner.equalsIgnoreCase(username(authorization))) {
                respond(exchange, 403, "{\"code\":\"BANKING-CORE-SERVICE-1002\",\"message\":\"denied\"}");
            } else {
                respond(exchange, 200, "{\"id\":1,\"type\":\"SAVINGS_ACCOUNT\",\"status\":\"ACTIVE\",\"actualBalance\":1000.00,\"availableBalance\":1000.00}");
            }
        } else if (path.startsWith("/api/v1/transaction/")) {
            respond(exchange, 200, "{\"message\":\"Transaction successfully completed\",\"transactionId\":\"TX-" + CALLS.size() + "\"}");
        } else {
            respond(exchange, 404, "{}");
        }
    }

    private static String username(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            String payload = authorization.substring(7).split("\\.")[1];
            Map<?, ?> claims = MAPPER.readValue(Base64.getUrlDecoder().decode(payload), Map.class);
            return (String) claims.get("preferred_username");
        } catch (Exception e) {
            return null;
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
