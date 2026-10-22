# DESIGN — Account Transaction History / Statement

Module: `core-banking-service`
Stage: 2 — Technical design
Traces: `SPEC.md` (AC1–AC8)

## Endpoint contract

```
GET /api/v1/account/{account_number}/transactions
      ?fromDate=yyyy-MM-dd      (optional)
      &toDate=yyyy-MM-dd        (optional)
```

- Added to `AccountController` (class-level `@RequestMapping("/api/v1/account")`), mirroring
  its existing `@PathVariable` + `@GetMapping` style. Logic lives in `TransactionService`
  (per the module's convention that transaction read/mapping methods belong there).
- `200 OK` → `List<TransactionResponse>` (most-recent-first).
- Unknown account → `EntityNotFoundException` → `400` + `ErrorResponse` via the existing
  `GlobalExceptionHandler` (AC5).
- Exactly one of `fromDate`/`toDate` → `SimpleBankingGlobalException` → `400` (AC8).

### Response DTO — `model/dto/response/TransactionResponse`

`@Builder @Getter @Setter` (matches `FundTransferResponse`):

| field           | type            | source                         |
|-----------------|-----------------|--------------------------------|
| transactionId   | String          | `TransactionEntity.transactionId` |
| referenceNumber | String          | `TransactionEntity.referenceNumber` |
| transactionType | TransactionType | `TransactionEntity.transactionType` |
| amount          | BigDecimal      | `TransactionEntity.amount`     |
| createdAt       | LocalDateTime   | `TransactionEntity.createdAt` (new) |

Deliberately excludes `account` (balances + owner PII) to satisfy AC6.

## Data-model change (gap + fix)

`TransactionEntity` / `banking_core_transaction` have no timestamp — this is the gap that
blocks ordering and filtering. Addressed with a **new** Flyway migration (never edit an
applied one):

`src/main/resources/db/migration/V1.0.<timestamp>__add_created_at_to_transaction.sql`
```sql
ALTER TABLE `banking_core_transaction`
    ADD COLUMN `created_at` datetime DEFAULT NULL;
```

Entity change (`TransactionEntity`):
- add `@Column(name = "created_at") private LocalDateTime createdAt;`
- `@PrePersist` sets `createdAt = LocalDateTime.now()` when null (AC7) — existing
  `fundTransfer` / `utilPayment` builder calls stay untouched.
- add `@NoArgsConstructor` + `@AllArgsConstructor` alongside the existing `@Builder`. The
  entity currently has only `@Builder`, which suppresses the implicit no-arg constructor
  JPA requires; the persistence-layer test (below) needs a real instantiable entity.

There are no seeded transaction rows (`temp_data.sql` seeds users/accounts only), so no
backfill is required.

## Repository — `TransactionRepository`

Spring Data derived queries (ordering + inclusivity live here):
```java
List<TransactionEntity> findByAccount_NumberOrderByCreatedAtDesc(String number);

List<TransactionEntity> findByAccount_NumberAndCreatedAtBetweenOrderByCreatedAtDesc(
        String number, LocalDateTime start, LocalDateTime end);
```
`Between` is **inclusive** on both ends (AC3). `account` is the `@OneToOne` to
`BankAccountEntity`; account number is `BankAccountEntity.number`.

## Service — `TransactionService.getAccountTransactions`

```java
public List<TransactionResponse> getAccountTransactions(
        String accountNumber, LocalDate fromDate, LocalDate toDate) {

    accountService.readBankAccount(accountNumber); // AC5: throws EntityNotFoundException

    boolean hasFrom = fromDate != null, hasTo = toDate != null;
    if (hasFrom ^ hasTo) {                          // AC8: both-or-neither
        throw new SimpleBankingGlobalException(
            "Both fromDate and toDate are required for range filtering",
            GlobalErrorCode.VALIDATION_ERROR);
    }

    List<TransactionEntity> txns = hasFrom
        ? repository.findByAccount_NumberAndCreatedAtBetweenOrderByCreatedAtDesc(
              accountNumber, fromDate.atStartOfDay(), toDate.atTime(LocalTime.MAX)) // AC2/AC3
        : repository.findByAccount_NumberOrderByCreatedAtDesc(accountNumber);       // AC1

    return transactionMapper.convertToDtoList(txns);                                // AC4 → []
}
```

- `toDate.atTime(LocalTime.MAX)` expands the end bound to `23:59:59.999999999` so the whole
  `toDate` day is inclusive (AC3).
- New `GlobalErrorCode.VALIDATION_ERROR` constant for AC8.

## Mapper — `model/mapper/TransactionMapper`

`extends BaseMapper<TransactionEntity, TransactionResponse>`; `convertToDto` builds the
response from entity fields. `convertToDtoList` (from `BaseMapper`) maps the list.

## Test plan (Stage 4)

- **`TransactionServiceTest`** (JUnit5 + Mockito, existing style):
  - all-transactions path delegates to `findByAccount_NumberOrderByCreatedAtDesc` (AC1)
  - range path passes `atStartOfDay` / `atTime(MAX)` bounds — asserted with
    `ArgumentCaptor` (AC2/AC3, catches the exclusive-boundary defect at the service layer)
  - empty repository result → empty list (AC4)
  - unknown account → `EntityNotFoundException` (AC5)
  - one-sided range → `SimpleBankingGlobalException` (AC8)
  - mapping exposes the AC6 fields
- **`TransactionRepositoryTest`** (`@DataJpaTest` + H2): persist transactions at known
  timestamps and assert `Between` inclusivity on both boundaries + `Desc` ordering
  (AC1/AC2/AC3) against a real query.

## Verification (Stage 5)

`cd core-banking-service && ./gradlew test` must be green. Change stays within
`core-banking-service`.
