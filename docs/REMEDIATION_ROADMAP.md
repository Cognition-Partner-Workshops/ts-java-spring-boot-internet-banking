# Remediation Roadmap

> Phased plan to address gaps identified in the [Gap Analysis](GAP_ANALYSIS.md).

---

## Phase 1: Quick Wins (1–2 weeks)

High-impact, low-effort fixes that address critical security and correctness issues.

### 1.1 Fix Balance Calculation Bug (RE-8)

**Severity**: Critical | **Effort**: Small

The `TransactionService.utilPayment()` and `internalFundTransfer()` methods double-deduct available balance. After subtracting from `actualBalance`, the code sets `availableBalance = actualBalance.subtract(amount)` using the already-reduced balance.

**Devin Prompt**:
> Fix the balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In both `utilPayment()` and `internalFundTransfer()`, the `availableBalance` is being set to `actualBalance.subtract(amount)` after `actualBalance` has already been subtracted, causing a double deduction. The correct behavior: after subtracting `amount` from `actualBalance`, set `availableBalance` equal to the new `actualBalance` (not subtract again). Fix for both the from-account and to-account sides of fund transfers. Add unit tests to verify correct balances after transactions.

---

### 1.2 Stop Leaking Stack Traces and Passwords in Logs (EH-4, OB-4, SE-10)

**Severity**: Critical/High | **Effort**: Small

**Devin Prompt**:
> Fix security issues across all microservices: (1) In every `GlobalExceptionHandler`, change the catch-all `Exception` handler to return a generic `500 Internal Server Error` with a safe message like `{"code":"INTERNAL_ERROR","message":"An unexpected error occurred"}` instead of `"Exception occur inside API " + e`. (2) In `internet-banking-user-service/UserController.java`, remove the `request.toString()` from the log statement in `createUser()` — it logs the user's password. Replace with a safe log that only includes the email. (3) In `internet-banking-fund-transfer-service/FundTransferController.java`, sanitize the log to not include full request details. Ensure all services follow the same pattern.

---

### 1.3 Add Input Validation to All Request DTOs (SE-2, EH-6)

**Severity**: Critical | **Effort**: Small

**Devin Prompt**:
> Add Jakarta Bean Validation to all request DTOs across the project: (1) In `core-banking-service`, add `@NotBlank` to `fromAccount`, `toAccount` and `@NotNull @DecimalMin("0.01")` to `amount` in `FundTransferRequest`. Add `@NotNull` to `providerId`, `@NotBlank` to `account`, `referenceNumber`, and `@NotNull @DecimalMin("0.01")` to `amount` in `UtilityPaymentRequest`. (2) In `internet-banking-user-service`, add `@NotBlank` to email, password, identification fields in the `User` DTO. (3) Add `@Valid` annotation to all `@RequestBody` parameters in all controllers. (4) Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns `400` with field-level error details. (5) Add `spring-boot-starter-validation` to each service's `build.gradle` if not present.

---

### 1.4 Fix HTTP Status Codes in Error Handlers (EH-1, EH-2)

**Severity**: High | **Effort**: Small

**Devin Prompt**:
> Improve error handling across all microservices: (1) Add a dedicated `@ExceptionHandler` for `EntityNotFoundException` that returns `404 Not Found` with structured `ErrorResponse`. (2) Add a handler for `InsufficientFundsException` that returns `422 Unprocessable Entity`. (3) Change the catch-all `Exception` handler from `400 Bad Request` to `500 Internal Server Error`. (4) Ensure all handlers return consistent `ErrorResponse` objects (never plain strings). Apply these changes to `GlobalExceptionHandler` in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.

---

### 1.5 Externalize Hardcoded Secrets (SE-1, SE-5)

**Severity**: Critical | **Effort**: Small

**Devin Prompt**:
> Replace all hardcoded credentials in `docker-compose/docker-compose.yml`, `docker-compose/docker-compose-support-apps.yml`, and `docker-compose/mysql/Dockerfile` with environment variable references. Create a `.env.example` file documenting all required variables: `MYSQL_ROOT_PASSWORD`, `MYSQL_APP_USER_PASSWORD`, `KC_DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`. Add `.env` to `.gitignore`. Remove the test credentials from `README.md` and replace with instructions to configure credentials. Update the MySQL Dockerfile to use `ARG`/`ENV` instead of hardcoded password.

---

### 1.6 Fix Swagger Dependency (AD-5)

**Severity**: Medium | **Effort**: Small

**Devin Prompt**:
> In `core-banking-service/build.gradle`, `internet-banking-user-service/build.gradle`, `internet-banking-fund-transfer-service/build.gradle`, and `internet-banking-utility-payment-service/build.gradle`, replace `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0`. These services use Spring MVC (not WebFlux), so the webflux Swagger artifact is incorrect. Verify that `/swagger-ui.html` or `/swagger-ui/index.html` works after the change.

---

### 1.7 Add Typed ResponseEntity to All Controllers (AD-1)

**Severity**: Medium | **Effort**: Small

**Devin Prompt**:
> Add generic type parameters to all `ResponseEntity` return types across all controllers. For example, in `AccountController`, change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. Do this for every controller method in: `core-banking-service` (AccountController, TransactionController, UserController), `internet-banking-fund-transfer-service` (FundTransferController), `internet-banking-utility-payment-service` (UtilityPaymentController). This improves type safety and OpenAPI documentation generation.

---

## Phase 2: Important Improvements (3–6 weeks)

Structural improvements for reliability, testability, and operational readiness.

### 2.1 Add Circuit Breakers and Timeouts (RE-1, RE-2, RE-3)

**Severity**: High | **Effort**: Medium

**Devin Prompt**:
> Add Resilience4j circuit breaker and timeout configuration to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. (1) Add `spring-cloud-starter-circuitbreaker-resilience4j` to both services' `build.gradle`. (2) Configure Feign integration with Resilience4j: set connection timeout to 5s, read timeout to 10s, circuit breaker to open after 5 failures in 60s window, half-open after 30s. (3) Add fallback methods for each Feign client call that return a meaningful error response (e.g., "Core banking service is temporarily unavailable. Please try again later."). (4) Update `application.yml` with Resilience4j configuration. (5) Add retry with 3 max attempts and exponential backoff (initial interval 500ms) for transient errors.

---

### 2.2 Fix Cross-Service Transaction Consistency (RE-5)

**Severity**: Critical | **Effort**: Large

**Devin Prompt**:
> Implement the Saga pattern for fund transfers in `internet-banking-fund-transfer-service`. Currently, the local `FundTransferEntity` is saved as SUCCESS before confirming the core banking transaction succeeded. Refactor `FundTransferService.fundTransfer()` to: (1) Save local entity as PENDING. (2) Call core banking Feign endpoint. (3) On success: update local entity to SUCCESS. (4) On failure: update local entity to FAILED and log the failure. (5) Wrap the Feign call in a try-catch and never mark as SUCCESS unless the remote call confirms success. Apply the same pattern to `UtilityPaymentService`. Add compensation logic that marks payments as FAILED when the core banking call throws an exception.

---

### 2.3 Extract Shared Library (CO-1, CO-5)

**Severity**: Medium | **Effort**: Medium

**Devin Prompt**:
> Create a new Gradle module `banking-common` as a shared library. (1) Create `banking-common/build.gradle` with `java-library` plugin. (2) Move duplicated classes into it: `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` (as a base class), `BaseMapper`, `AuditAware`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`. (3) Set up a Gradle composite build or multi-project build so all services can depend on `banking-common`. (4) Remove the duplicated classes from each service and add `implementation project(':banking-common')` to their build files. (5) Verify all services compile and tests pass.

---

### 2.4 Add Comprehensive Unit and Integration Tests (TS-1, TS-2, TS-4)

**Severity**: High | **Effort**: Large

**Devin Prompt**:
> Add unit and integration tests to the three internet banking services that currently lack them. For each of `internet-banking-user-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`: (1) Add unit tests for each service class (e.g., `FundTransferServiceTest`) using Mockito to mock Feign clients and repositories. Test happy paths and error paths. (2) Add `@WebMvcTest` controller tests verifying request/response serialization, status codes, and validation. (3) Add an integration test using `@SpringBootTest` with H2 in-memory database (already in dependencies) to verify JPA repository queries and Flyway migrations. Target at least 70% line coverage per service. Use JUnit 5 and Mockito (already in test dependencies).

---

### 2.5 Implement Structured Logging (OB-1, OB-5)

**Severity**: High | **Effort**: Medium

**Devin Prompt**:
> Implement structured JSON logging across all microservices. (1) Add `net.logstash.logback:logstash-logback-encoder:7.4` to each service's `build.gradle`. (2) Create a shared `logback-spring.xml` configuration in each service's `src/main/resources/` that outputs JSON format in Docker/production profile and console format in development. (3) Add MDC fields for `traceId`, `spanId` (from Micrometer), `userId` (from X-Auth-Id header), and `service` (from application name). (4) Update the `AppAuthUserFilter` in each service to set `userId` in MDC. (5) Replace all string concatenation in log statements with parameterized logging.

---

### 2.6 Add Prometheus Metrics (OB-3)

**Severity**: Medium | **Effort**: Small

**Devin Prompt**:
> Enable Prometheus metrics across all services. (1) Add `io.micrometer:micrometer-registry-prometheus` to each service's `build.gradle`. (2) Configure Actuator to expose the Prometheus endpoint: add `management.endpoints.web.exposure.include=health,info,prometheus,metrics` to each service's configuration. (3) Add custom metrics for key business operations: fund transfer count/latency, utility payment count/latency, user registration count. Use `MeterRegistry` to create counters and timers in the relevant service classes.

---

### 2.7 Add Custom Health Indicators (OB-2)

**Severity**: Medium | **Effort**: Small

**Devin Prompt**:
> Add custom health indicators to services with external dependencies. (1) In `internet-banking-user-service`, add a `KeycloakHealthIndicator` that checks Keycloak connectivity by calling the realm endpoint. (2) In `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`, add a health indicator that checks the core banking service availability via the Eureka client. (3) Configure detailed health info in application properties: `management.endpoint.health.show-details=always`.

---

### 2.8 Add Idempotency for Transactions (RE-6)

**Severity**: High | **Effort**: Medium

**Devin Prompt**:
> Implement idempotency for fund transfers and utility payments. (1) Add an `idempotencyKey` field (UUID) to `FundTransferRequest` and `UtilityPaymentRequest`. (2) Add a `UNIQUE` constraint on `idempotencyKey` in both `fund_transfer` and `utility_payment` tables via Flyway migration. (3) In `FundTransferService.fundTransfer()`, check if a record with the same idempotency key already exists — if so, return the existing response instead of processing again. (4) Apply the same pattern to `UtilityPaymentService`. (5) Document the idempotency key in the API documentation. Clients should generate a UUID and send it with each request; retries with the same key are safe.

---

### 2.9 Scope MySQL Permissions (SE-8)

**Severity**: Medium | **Effort**: Small

**Devin Prompt**:
> In `docker-compose/mysql/privileges.sql`, replace the wildcard `GRANT ... on *.*` with database-specific grants: `GRANT ALL ON banking_core_service.* TO 'javatodev_development'@'%'`, and repeat for `banking_core_user_service`, `banking_core_fund_transfer_service`, and `banking_core_utility_payment_service`. Remove `DROP` and `ALTER` privileges from the grant for production environments and document this.

---

## Phase 3: Polish & Production Readiness (6–12 weeks)

Refinements for a production-grade application.

### 3.1 Add Contract Tests (TS-3)

**Severity**: High | **Effort**: Large

**Devin Prompt**:
> Add Spring Cloud Contract tests between services. (1) In `core-banking-service`, add `spring-cloud-starter-contract-verifier` to `build.gradle`. Create contract definitions (Groovy DSL) for: `GET /api/v1/account/bank-account/{number}`, `GET /api/v1/user/{id}`, `POST /api/v1/transaction/fund-transfer`, `POST /api/v1/transaction/util-payment`. (2) In consumer services (`internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, `internet-banking-user-service`), add `spring-cloud-starter-contract-stub-runner` and write `@AutoConfigureStubRunner` tests that verify each Feign client against the stubs generated from core banking contracts. This ensures API contract changes are caught at build time.

---

### 3.2 Add Pagination Metadata to Responses (AD-2)

**Severity**: Medium | **Effort**: Small

**Devin Prompt**:
> Fix paginated endpoints to return full pagination metadata. (1) Create a generic `PageResponse<T>` DTO in the shared library with fields: `content`, `page`, `size`, `totalElements`, `totalPages`, `last`. (2) Update `UserController.readUsers()` in both `core-banking-service` and `internet-banking-user-service` to return `PageResponse<User>`. (3) Update `FundTransferController.readFundTransfers()` and `UtilityPaymentController.readPayments()` similarly. (4) Update the corresponding service methods to pass through the Spring `Page` metadata instead of calling `.getContent()` and discarding it.

---

### 3.3 Set Up CI/CD Pipeline

**Severity**: Medium | **Effort**: Medium

**Devin Prompt**:
> Create a GitHub Actions CI/CD pipeline. (1) Create `.github/workflows/ci.yml` with jobs: `build` (compile all services with Gradle), `test` (run all unit tests), `lint` (checkstyle or spotless), `security` (OWASP dependency-check). (2) Use a matrix strategy to build all 6 services in parallel. (3) Cache Gradle dependencies across runs. (4) Add a `docker` job that builds Docker images for all services. (5) Add badge to README showing CI status.

---

### 3.4 Add API Filtering and Search (AD-7)

**Severity**: Low | **Effort**: Medium

**Devin Prompt**:
> Add filtering and search capabilities to list endpoints. (1) In `core-banking-service`, add query parameters to `GET /api/v1/user`: `email`, `firstName`, `lastName`. Implement using Spring Data JPA Specifications or `@Query`. (2) In `internet-banking-fund-transfer-service`, add filters to `GET /api/v1/transfer`: `status`, `fromAccount`, `toAccount`, `dateFrom`, `dateTo`. (3) In `internet-banking-utility-payment-service`, add filters to `GET /api/v1/utility-payment`: `status`, `providerId`, `dateFrom`, `dateTo`. Use `@RequestParam(required = false)` for all filter parameters.

---

### 3.5 Fix Keycloak Singleton Thread Safety (SE-6)

**Severity**: Medium | **Effort**: Small

**Devin Prompt**:
> Fix the thread-safety issue in `internet-banking-user-service/KeycloakProperties.java`. Replace the lazy singleton `getInstance()` pattern with a Spring-managed `@Bean` method. Create a `@Configuration` class `KeycloakConfig` that declares a `@Bean Keycloak keycloak()` method using `KeycloakBuilder`. Remove the static `keycloakInstance` field and `getInstance()` method. Inject the `Keycloak` bean directly where needed.

---

### 3.6 Convert Mappers to MapStruct (CO-4)

**Severity**: Low | **Effort**: Medium

**Devin Prompt**:
> Replace manual mapper classes with MapStruct. (1) Add `org.mapstruct:mapstruct:1.5.5.Final` and `org.mapstruct:mapstruct-processor:1.5.5.Final` to each service's `build.gradle`. (2) Convert each mapper interface: `BankAccountMapper`, `UserMapper`, `UtilityAccountMapper`, `FundTransferMapper`, `UtilityPaymentMapper` to MapStruct `@Mapper` interfaces. (3) Remove the `BaseMapper` abstract class and all manual `convertToDto`/`convertToEntity` implementations. (4) Inject mappers as Spring beans using `@Mapper(componentModel = "spring")`. (5) Verify all tests pass.

---

### 3.7 Implement Notification Service

**Severity**: Low | **Effort**: Large

**Devin Prompt**:
> Implement the notification service mentioned in the README as "PENDING Development". (1) Create a new `internet-banking-notification-service` module. (2) Add `spring-boot-starter-amqp` for RabbitMQ integration. (3) Add RabbitMQ to docker-compose. (4) In `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`, publish events to RabbitMQ after successful transactions. (5) In the notification service, consume messages and log them (email sending can be mocked). (6) Register with Eureka and connect to Config Server.

---

### 3.8 Add Bulkhead Isolation (RE-7)

**Severity**: Medium | **Effort**: Medium

**Devin Prompt**:
> Add bulkhead isolation using Resilience4j. (1) Configure thread pool bulkheads for each Feign client in `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. (2) Set max concurrent calls to 25 and max wait duration to 500ms. (3) Add bulkhead configuration to `application.yml`. (4) Add metrics for bulkhead usage (available permits, rejected calls) via the existing Micrometer integration.

---

## Summary Timeline

| Phase | Items | Duration | Key Deliverables |
|-------|-------|----------|-----------------|
| **Phase 1** | 7 items | 1–2 weeks | Bug fixes, security hardening, input validation, proper error codes |
| **Phase 2** | 9 items | 3–6 weeks | Resilience patterns, shared library, test coverage, observability |
| **Phase 3** | 8 items | 6–12 weeks | Contract tests, CI/CD, filtering, notification service, production polish |

### Priority Matrix

```
                    High Impact
                        │
         ┌──────────────┼──────────────┐
         │  Phase 1     │  Phase 2     │
         │  (Do First)  │  (Plan Now)  │
 Low ────┼──────────────┼──────────────┼──── High
 Effort  │  Phase 1     │  Phase 3     │    Effort
         │  (Easy Wins) │  (Roadmap)   │
         └──────────────┼──────────────┘
                        │
                    Low Impact
```
