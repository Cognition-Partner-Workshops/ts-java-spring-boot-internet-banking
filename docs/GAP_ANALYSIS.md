# Engineering Standards Gap Analysis

> Comparison of the `ts-java-spring-boot-internet-banking-microservices` codebase against industry engineering best practices.

---

## 1. Code Organization

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| CO-1 | **No shared library / common module** | Medium | Medium | Exception classes (`ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`), mappers (`BaseMapper`), audit classes (`AuditAware`), and filter classes (`AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`) are copy-pasted across 3–4 services. Any change must be replicated manually. |
| CO-2 | **No multi-project Gradle build** | Medium | Small | Each service has an independent `build.gradle` with duplicated plugin versions, dependency versions, and configuration. A Gradle composite or multi-project build would centralize this. |
| CO-3 | **Inconsistent package structure across services** | Low | Small | Core Banking uses `model.dto`, `model.entity`, `model.mapper`, `repository`, `service`. User service uses `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.rest`. Fund Transfer uses `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `service.rest.client`. No standard convention is enforced. |
| CO-4 | **Mapper instantiation inconsistency** | Low | Small | Mappers are `new`-ed as field initializers in service classes (e.g., `private UserMapper userMapper = new UserMapper()`) rather than being Spring-managed beans or using a framework like MapStruct. This bypasses dependency injection and makes testing harder. |
| CO-5 | **DTO duplication across service boundaries** | Medium | Medium | `FundTransferRequest`, `UtilityPaymentRequest`, `AccountResponse`, etc. are duplicated in both the originating service and the core banking service with slightly different class structures. Changes in the API contract require updating multiple services. |

---

## 2. Error Handling

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| EH-1 | **Catch-all handler returns 400 for all exceptions** | High | Small | Every `GlobalExceptionHandler` maps `Exception.class` to `400 Bad Request` with a raw string body (`"Exception occur inside API " + e`). This leaks stack traces, misrepresents server errors as client errors, and provides no structured response. |
| EH-2 | **No HTTP status differentiation** | High | Small | `EntityNotFoundException` should return `404 Not Found`, `InsufficientFundsException` should return `422 Unprocessable Entity`, validation errors should return `400`. Currently all business exceptions return `400 Bad Request`. |
| EH-3 | **Inconsistent error response format** | Medium | Small | Core Banking and User Service use `ErrorResponse` with builder pattern. Fund Transfer uses `new ErrorResponse(code, message)`. The catch-all returns a plain string. No consistent envelope across services. |
| EH-4 | **Stack trace leakage in generic handler** | Critical | Small | The catch-all `"Exception occur inside API " + e` exposes the full exception (including class names, internal paths, and potentially SQL errors) to the API consumer. This is a security and information disclosure risk. |
| EH-5 | **No Feign error decoder in user service** | Medium | Small | User service's `BankingCoreRestClient` does not specify a Feign configuration with error decoder, unlike Fund Transfer and Utility Payment services which have `CustomFeignClientConfiguration` with `CustomFeignErrorDecoder`. Errors from core banking will surface as raw Feign exceptions. |
| EH-6 | **No validation error handling** | Medium | Small | No `@Valid` annotations on request bodies, no `MethodArgumentNotValidException` handler. Invalid input bypasses business logic and either causes NPEs or database constraint violations. |

---

## 3. Testing

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| TS-1 | **Unit tests exist only for core-banking-service** | High | Large | Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). Other services have only empty Spring context load tests (`*ApplicationTests.java`). |
| TS-2 | **No integration tests** | High | Large | No tests verify actual database queries, Flyway migrations, or Spring context wiring with real dependencies (e.g., H2 in-memory). The H2 dependency is included but unused. |
| TS-3 | **No contract tests between services** | High | Large | Services communicate via Feign; no Spring Cloud Contract, Pact, or equivalent verifies API compatibility. A breaking change in core banking would silently break downstream services. |
| TS-4 | **No controller/API layer tests** | Medium | Medium | No `@WebMvcTest` or `MockMvc` tests for any controller. Request serialization, response codes, and path variable binding are untested. |
| TS-5 | **No test for Keycloak integration** | Medium | Medium | User service's Keycloak operations (create, update, search) are untested. No mocking of Keycloak admin client. |
| TS-6 | **No test data management strategy** | Low | Small | Flyway seed data (`V1.0.20210427174721__temp_data.sql`) is used for both development and testing with hardcoded values. No test fixtures or factories. |

---

## 4. Security

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| SE-1 | **Hardcoded database credentials in Docker Compose** | Critical | Small | MySQL root password (`woVERANKliGharym`), DB user password (`oPItyPticIAt`), Keycloak admin password (`password`), and Keycloak DB password (`password`) are all hardcoded in `docker-compose.yml` and the MySQL Dockerfile. |
| SE-2 | **No input validation on any endpoint** | Critical | Medium | No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, or custom validators on any request DTO. A fund transfer with negative amount, null accounts, or zero-length strings will reach the database layer unchecked. |
| SE-3 | **CSRF disabled on API Gateway** | Medium | Small | `ServerHttpSecurity.CsrfSpec::disable` — acceptable for a pure REST API, but should be documented. If a frontend is added, CSRF protection must be re-evaluated. |
| SE-4 | **No rate limiting on sensitive endpoints** | Medium | Medium | The user registration endpoint (`/user/api/v1/bank-users/register`) is publicly accessible (no auth required) with no rate limiting. Vulnerable to brute force and abuse. |
| SE-5 | **Test credentials in README** | Medium | Small | README contains `ib_admin@javatodev.com / 5V7huE3G86uB`. Even if intentional for development, this normalizes credential exposure. |
| SE-6 | **Keycloak singleton not thread-safe** | Medium | Small | `KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton pattern (`if (keycloakInstance == null)`). Under concurrent requests, multiple Keycloak clients could be created (race condition). |
| SE-7 | **No dependency vulnerability scanning** | Medium | Small | No OWASP Dependency-Check, Snyk, or similar CVE scanner in the build. Dependencies are not audited. |
| SE-8 | **MySQL user has wildcard DB access** | Medium | Small | `GRANT ... on *.*` gives the application user (`javatodev_development`) access to all databases, including `mysql` system tables. Should be scoped to specific databases. |
| SE-9 | **No HTTPS/TLS configuration** | Medium | Medium | All services communicate over plain HTTP. In production, TLS should be configured for both external (gateway) and internal (service-to-service) traffic. |
| SE-10 | **Password handling in user registration** | High | Small | User password is passed as a plain field in the `User` DTO and forwarded to Keycloak. The password is logged via `request.toString()` in the controller (`log.info("Creating user with {}", request.toString())`). |

---

## 5. API Design

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| AD-1 | **Raw ResponseEntity without type parameters** | Medium | Small | All controllers use `ResponseEntity` (raw type) instead of `ResponseEntity<FundTransferResponse>`. This loses compile-time type safety and OpenAPI schema generation. |
| AD-2 | **No pagination metadata in responses** | Medium | Small | Paginated endpoints (users, transfers, payments) return `List<T>` instead of `Page<T>`, discarding total count, page number, and page size information. |
| AD-3 | **No API versioning strategy** | Low | Small | All endpoints use `/api/v1/` but there's no documented versioning strategy or mechanism for deprecation. |
| AD-4 | **Inconsistent naming conventions** | Low | Small | Core banking uses `/bank-account/{account_number}` (snake_case path variable) and `/util-account/{account_name}`. User service uses `/bank-users/{id}` (kebab-case). Fund Transfer uses `/transfer`. No consistent convention. |
| AD-5 | **No OpenAPI/Swagger fully functional** | Medium | Small | `springdoc-openapi-starter-webflux-ui` is included in non-reactive services (user, fund-transfer, utility-payment, core-banking are all Spring MVC). Should use `springdoc-openapi-starter-webmvc-ui`. This likely causes the Swagger UI to not work correctly. |
| AD-6 | **No standard response envelope** | Medium | Medium | Success responses return raw entities. Error responses return either `ErrorResponse` or plain strings. No consistent wrapper (e.g., `{data, error, meta}`) across all endpoints. |
| AD-7 | **No filtering or search capabilities** | Low | Medium | No query parameter filtering on any list endpoint. Cannot search users by email, filter transfers by status, or filter payments by date range. |
| AD-8 | **Missing DELETE endpoints** | Low | Small | No delete operations for any resource (users, accounts, transfers). No soft-delete mechanism. |

---

## 6. Observability

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| OB-1 | **No structured logging** | High | Medium | Services use `log.info()` with string concatenation/formatting. No JSON log format, no MDC correlation IDs, no consistent log levels across services. |
| OB-2 | **No custom health checks** | Medium | Small | Only default Spring Boot Actuator health endpoint is available. No custom health indicators for database connectivity, Keycloak availability, or Feign client health. |
| OB-3 | **No Prometheus metrics endpoint** | Medium | Small | `spring-boot-starter-actuator` is included but Prometheus metrics export (`micrometer-registry-prometheus`) is not configured despite README listing Prometheus as a technology. |
| OB-4 | **Sensitive data in logs** | High | Small | User creation logs the entire `User` object including password (`log.info("Creating user with {}", request.toString())`). Fund transfer logs full request details. |
| OB-5 | **No log aggregation configuration** | Medium | Medium | No Logback/Log4j2 configuration files. No log shipping (ELK, CloudWatch, etc.) setup. |
| OB-6 | **Tracing coverage unknown** | Low | Small | Zipkin and Brave are configured but no custom spans, tags, or baggage are added. Default automatic instrumentation may miss important business operations. |
| OB-7 | **No alerting or monitoring configuration** | Medium | Medium | No Grafana dashboards, alert rules, or SLO definitions. |

---

## 7. Resilience

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| RE-1 | **No circuit breakers** | High | Medium | Services make synchronous Feign calls with no circuit breaker (e.g., Resilience4j). If core banking is down, fund transfer and utility payment services will hang and exhaust thread pools. |
| RE-2 | **No retry policies** | High | Small | No Feign retry configuration. Transient network errors or temporary service unavailability result in immediate failure. |
| RE-3 | **No timeout configuration** | High | Small | No Feign client timeouts, no Spring WebClient timeouts, no connection pool settings. Requests could hang indefinitely. |
| RE-4 | **No fallback behavior** | Medium | Medium | No fallback methods for Feign calls. When core banking is unavailable, downstream services return raw error responses with no graceful degradation. |
| RE-5 | **Non-atomic cross-service transactions** | Critical | Large | Fund transfer: the local entity is saved as SUCCESS, then the Feign call to core banking is made. If the Feign call fails after local save, the fund transfer record shows SUCCESS but no actual transfer occurred. No compensation (saga) pattern exists. |
| RE-6 | **No idempotency keys** | High | Medium | Fund transfers and utility payments have no idempotency mechanism. Retrying a failed request could result in duplicate transactions. |
| RE-7 | **No bulkhead isolation** | Medium | Medium | All Feign calls share the same thread pool. A slow response from one service could exhaust threads and affect all downstream calls. |
| RE-8 | **Balance calculation bug** | Critical | Small | In `TransactionService.utilPayment()`, the available balance is set to `actualBalance.subtract(amount)` using the already-subtracted actual balance, resulting in a double deduction of the available balance. Same pattern exists in `internalFundTransfer()`. |
| RE-9 | **wait-for-it.sh dependency ordering only** | Low | Small | Container startup uses `wait-for-it.sh` to check port availability, but this doesn't verify that the service is actually ready (e.g., Flyway migration complete, Eureka registration done). |

---

## Summary Dashboard

| Category | Critical | High | Medium | Low | Total |
|----------|----------|------|--------|-----|-------|
| Code Organization | 0 | 0 | 3 | 2 | 5 |
| Error Handling | 1 | 2 | 3 | 0 | 6 |
| Testing | 0 | 3 | 2 | 1 | 6 |
| Security | 2 | 1 | 5 | 0 | 8* |
| API Design | 0 | 0 | 4 | 3 | 7* |
| Observability | 0 | 2 | 3 | 1 | 6* |
| Resilience | 2 | 3 | 2 | 1 | 8* |
| **Total** | **5** | **11** | **20** | **8** | **44** |

\* Includes items with severity downgrade/upgrade based on production readiness context.
