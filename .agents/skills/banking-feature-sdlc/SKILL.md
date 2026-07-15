---
name: banking-feature-sdlc
description: >-
  Repo-specific mechanics for delivering a brownfield feature end-to-end in the
  ts-java-spring-boot-internet-banking core-banking-service module: build/test
  commands, package layout, persistence conventions, and the existing
  transaction symbols to extend. Auto-loaded when working in this repo; pair with
  the !deliver-banking-feature-sdlc playbook for the general procedure.
---

# Skill: Deliver a feature in core-banking-service

Repo-specific mechanics the `!deliver-banking-feature-sdlc` playbook deliberately
omits. This repo is a Java 21 / Spring Boot 3.2.x banking microservices system
built with Gradle. Feature work for accounts and transactions lives in the
**`core-banking-service`** module.

## Build and verify

All commands run from the `core-banking-service/` directory (the module has its
own Gradle wrapper):

```bash
cd core-banking-service
./gradlew test            # the verification gate — must be green
./gradlew build           # compile + test + assemble
```

Tests are JUnit 5 + Mockito with plain constructor-wired service instances (no
Spring context) — see `src/test/java/com/javatodev/finance/service/`. H2 is used
for any persistence-layer tests. The verification loop is **`./gradlew test`
green**; a compile alone is not "done."

## Package layout (`com.javatodev.finance`)

```
controller/        # @RestController, @RequestMapping("/api/v1/...")
service/           # @Service @Transactional, constructor injection via Lombok
repository/        # Spring Data JPA interfaces (JpaRepository)
model/             # enums (TransactionType, AccountType, AccountStatus)
model/dto/         # API DTOs (+ dto/request, dto/response)
model/entity/      # JPA @Entity classes (Lombok @Getter/@Setter/@Builder)
model/mapper/      # entity <-> dto mappers
exception/         # custom exceptions + GlobalExceptionHandler
```

Conventions to match: Lombok `@RequiredArgsConstructor` for injection,
`@Builder` on entities, `@Operation` + `@Tag` (springdoc/OpenAPI) on endpoints,
existing route prefix style `/api/v1/<area>`.

## Transaction feature symbols (the "before" to extend)

- `controller/TransactionController.java` — `@RequestMapping("/api/v1/transaction")`,
  currently `POST /fund-transfer` and `POST /util-payment`. New read endpoints for
  account statements go under `/api/v1/account/{accountNumber}/transactions`
  (mirror `AccountController`'s `@PathVariable` + `@GetMapping` style).
- `service/TransactionService.java` — `@Service @Transactional`; add read/mapping
  methods here.
- `repository/TransactionRepository.java` — empty `JpaRepository<TransactionEntity,
  Long>`; add derived or `@Query` methods (ordering + filtering live here).
- `model/entity/TransactionEntity.java` — maps to table
  `banking_core_transaction`; fields `amount`, `transactionType`,
  `referenceNumber`, `transactionId`, and `account` (`@OneToOne` →
  `BankAccountEntity`). Account number is `BankAccountEntity.number`.

## Persistence

- Flyway migrations: `src/main/resources/db/migration/`, named
  `V1.0.<timestamp>__<description>.sql`. The transaction table is created by
  `V1.0.20210429210839__create_transaction_table.sql`
  (`banking_core_transaction`). Add new columns via a **new** migration file —
  never edit an applied one — and reflect the column on `TransactionEntity`.
- MySQL dialect in production config; H2 in tests.

## Gotchas

- Date-range filtering is **inclusive** on both ends unless stated otherwise;
  Spring Data `Between` is inclusive. Exclusive comparisons (`isAfter`/`isBefore`)
  silently drop boundary rows — the classic bug the boundary test must catch.
- Transaction history is ordered **most-recent-first**; assert ordering in a test.
- Keep changes inside `core-banking-service`; the other services build
  independently and are out of scope for account/transaction features.
