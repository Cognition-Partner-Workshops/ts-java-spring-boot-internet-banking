# DESIGN — Balance-as-of-date query for an account

Each decision below extends an existing pattern in `core-banking-service`
(package `com.javatodev.finance`). Change is confined to this module.

## 1. Schema change (Flyway migration)

`TransactionEntity` / `banking_core_transaction` had no time dimension, so a
point-in-time balance could not be computed. Add a nullable `timestamp` column
in a **new** migration (never edit an applied one), matching the existing
`V1.0.<timestamp>__<description>.sql` convention:

```
V1.0.20260715144500__add_transaction_timestamp.sql
  ALTER TABLE `banking_core_transaction` ADD COLUMN `timestamp` datetime DEFAULT NULL;
```

Nullable so the migration applies cleanly to pre-existing rows; the service
treats a `null` timestamp as "not strictly after `asOf`" (included), which is the
safe default for legacy rows.

## 2. Entity change

`model/entity/TransactionEntity.java` gains:

```java
private LocalDateTime timestamp;

@PrePersist
void onCreate() {
    if (timestamp == null) {
        timestamp = LocalDateTime.now();
    }
}
```

`@PrePersist` guarantees every newly persisted transaction receives a timestamp
even if a caller forgets. The existing write paths in `TransactionService`
(`fundTransfer` → `internalFundTransfer`, `utilPayment`) also set
`.timestamp(LocalDateTime.now())` explicitly on the builder, keeping intent
visible at the call site.

## 3. Response DTO

New `model/dto/response/AccountBalanceResponse.java`, mirroring the existing
`FundTransferResponse` style (Lombok `@Builder @Getter @Setter`):

```java
private String accountNumber;
private LocalDateTime asOf;
private BigDecimal balance;
```

Stable, minimal, and exactly the three values the contract promises.

## 4. Repository query

`repository/TransactionRepository.java` gains an explicit JPQL query traversing
the `account` relationship (ordering/filtering conventionally live in the
repository layer):

```java
@Query("SELECT t FROM TransactionEntity t WHERE t.account.number = :accountNumber")
List<TransactionEntity> findByAccountNumber(@Param("accountNumber") String accountNumber);
```

The as-of comparison is deliberately performed in the service (Java) rather than
the query, so the **boundary semantics are unit-testable** with Mockito without a
database — the classic inclusive/exclusive bug lives there and must be asserted.

## 5. Service computation

`service/TransactionService.java` gains `getBalanceAsOf(String accountNumber,
LocalDateTime asOf)`. It already injects `bankAccountRepository` and
`transactionRepository`, so no constructor change (and no disturbance to existing
tests). Formula from SPEC:

```java
if (asOf == null) throw new InvalidRequestException("As-of date/time is required");
BankAccountEntity account = bankAccountRepository.findByNumber(accountNumber)
        .orElseThrow(EntityNotFoundException::new);
BigDecimal balance = account.getActualBalance();
for (TransactionEntity t : transactionRepository.findByAccountNumber(accountNumber)) {
    if (t.getTimestamp() != null && t.getTimestamp().isAfter(asOf)) {   // strictly-after → reverse
        balance = balance.subtract(t.getAmount());                       // signed amount
    }
}
return AccountBalanceResponse.builder()
        .accountNumber(accountNumber).asOf(asOf).balance(balance).build();
```

`isAfter` (strict) is the crux: it reverses only transactions *after* `asOf`,
leaving a transaction exactly at `asOf` included — satisfying the inclusive
boundary. Using `>=` (`!isBefore`) here would wrongly drop the boundary
transaction.

## 6. Controller endpoint

The path is account-scoped, so it lives on `AccountController`
(`@RequestMapping("/api/v1/account")`) rather than `TransactionController`
(fixed base `/api/v1/transaction`). `AccountController` gains a
`TransactionService` dependency (constructor injection via Lombok
`@RequiredArgsConstructor`) — the balance is derived from the ledger, which
`TransactionService` owns:

```java
@GetMapping("/{account_number}/balance")
public ResponseEntity getBalanceAsOf(
        @PathVariable("account_number") String accountNumber,
        @RequestParam("asOf") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf) {
    return ResponseEntity.ok(transactionService.getBalanceAsOf(accountNumber, asOf));
}
```

`@Operation` documents the endpoint (springdoc), matching existing style. A
missing/unparseable `asOf` is rejected at binding time (400); a null reaching the
service is rejected via `InvalidRequestException`.

## 7. Error handling

New `exception/InvalidRequestException` extends `SimpleBankingGlobalException`
with new code `GlobalErrorCode.INVALID_REQUEST = "BANKING-CORE-SERVICE-1002"`,
consistent with `EntityNotFoundException` / `InsufficientFundsException`. Both
map to `400` via the existing `GlobalExceptionHandler`. Account-not-found reuses
`EntityNotFoundException` (`BANKING-CORE-SERVICE-1000`).

## Ordering & pagination

Not applicable — the endpoint returns a single scalar balance, not a collection.

## Verification

`./gradlew test` (JUnit 5 + Mockito, plain constructor-wired services) is the
gate; then `./gradlew build`. New `TransactionServiceTest` cases cover happy
path, no-history, inclusive boundary, signed credit/debit, before/after cutoff,
invalid input, and account-not-found.
