# DESIGN — Account Transaction History Endpoint

Each decision is tied to an existing pattern in `core-banking-service`
(`com.javatodev.finance`). Changes are confined to this module.

## 1. Schema change (new Flyway migration)

`banking_core_transaction` (created by
`V1.0.20210429210839__create_transaction_table.sql`) has no time column, so
history cannot be ordered or filtered by time. Add one via a **new** migration
(never edit an applied one):

```
V1.0.20260715120000__add_timestamp_to_transaction_table.sql
  ALTER TABLE `banking_core_transaction`
      ADD COLUMN `timestamp` datetime DEFAULT NULL;
```

MySQL dialect in production; H2 in tests (`@DataJpaTest` generates the column
from the entity via create-drop, matching the existing test datasource setup).

## 2. Entity change

`TransactionEntity` gains a `private LocalDateTime timestamp;` field
(Lombok `@Builder/@Getter/@Setter`, matching the existing style). It maps to the
new `timestamp` column.

## 3. Populate timestamp on writes

`TransactionService.internalFundTransfer` and `utilPayment` build
`TransactionEntity` via `@Builder`; each `.build()` now sets
`.timestamp(LocalDateTime.now())` so newly written transactions are visible in
history with an ordering key. (Existing behaviour and tests are unaffected —
they do not assert on timestamp.)

## 4. DTO (`model/dto/response/TransactionResponse`)

New response DTO in the existing `dto/response` package, using the same
`@Builder/@Getter/@Setter` shape as `FundTransferResponse`. Fields: `id`,
`transactionId`, `referenceNumber`, `transactionType`, `amount`,
`accountNumber`, `timestamp`. A dedicated read DTO keeps the entity graph
(`@OneToOne` account, user) out of the API surface.

## 5. Mapper (`model/mapper/TransactionMapper`)

Extends the existing `BaseMapper<TransactionEntity, TransactionResponse>`
(reusing its `convertToDtoList`). `convertToDto` flattens
`entity.getAccount().getNumber()` to `accountNumber`.

## 6. Repository query (ordering + filtering live here)

`TransactionRepository` gains derived queries. Ordering is
`OrderByTimestampDescIdDesc` (most-recent-first, deterministic tie-break).
Date filtering uses Spring Data `Between` / `GreaterThanEqual` /
`LessThanEqual`, which are **inclusive** — the key to correct boundary semantics
(exclusive `isAfter/isBefore` would silently drop boundary rows):

```java
List<TransactionEntity> findByAccount_NumberOrderByTimestampDescIdDesc(String number);
List<TransactionEntity> findByAccount_NumberAndTimestampBetweenOrderByTimestampDescIdDesc(String number, LocalDateTime from, LocalDateTime to);
List<TransactionEntity> findByAccount_NumberAndTimestampGreaterThanEqualOrderByTimestampDescIdDesc(String number, LocalDateTime from);
List<TransactionEntity> findByAccount_NumberAndTimestampLessThanEqualOrderByTimestampDescIdDesc(String number, LocalDateTime to);
```

(`Account_Number` navigates `TransactionEntity.account` →
`BankAccountEntity.number`.)

## 7. Service method

`TransactionService.getTransactionHistory(String accountNumber, LocalDate from,
LocalDate to)`:

1. `accountService.readBankAccount(accountNumber)` — reuses the existing lookup
   that throws `EntityNotFoundException` for unknown accounts (consistent error
   behaviour, no new exception type).
2. Convert the optional `LocalDate` bounds to an inclusive `LocalDateTime`
   window: `from.atStartOfDay()` and `to.atTime(LocalTime.MAX)`.
3. Dispatch to the matching repository query based on which bounds are present.
4. Map via `TransactionMapper.convertToDtoList` and return (empty list when no
   rows — never null).

## 8. Controller endpoint

The URL is account-scoped (`/api/v1/account/...`), which is exactly
`AccountController`'s class-level `@RequestMapping("/api/v1/account")`. Adding
the method there yields the required path with no prefix conflict and mirrors
`getBankAccount`'s `@PathVariable` + `@GetMapping` + `@Operation` style. The
repo skill suggested `TransactionController`, but that class is prefixed
`/api/v1/transaction`; hosting the account-scoped route in `AccountController`
keeps the contract path correct without inventing a new prefix.
`TransactionService` is injected into `AccountController` via the existing
Lombok `@RequiredArgsConstructor`.

```java
@GetMapping("/{accountNumber}/transactions")
public ResponseEntity getTransactionHistory(
    @PathVariable("accountNumber") String accountNumber,
    @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DATE) LocalDate from,
    @RequestParam(value = "to",   required = false) @DateTimeFormat(iso = DATE) LocalDate to) { ... }
```

## Verification

`./gradlew test` from `core-banking-service` is the gate:
- `TransactionServiceTest` (JUnit 5 + Mockito) — happy path, empty result,
  ordering passthrough, inclusive-window bound computation (ArgumentCaptor),
  each filter combination, and account-not-found error.
- `TransactionRepositoryTest` (`@DataJpaTest` + H2) — real inclusive boundary
  and most-recent-first ordering against generated schema.

Then `./gradlew build` (compile + test + assemble).
