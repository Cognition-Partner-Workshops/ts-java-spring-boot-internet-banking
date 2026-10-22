# DESIGN — Account Statement & Transaction History

Implements the stories in `SPEC.md`. All work stays inside `core-banking-service`
and follows existing conventions (package `com.javatodev.finance`, Lombok DTOs,
`@Getter/@Setter` entities, Flyway migrations under
`src/main/resources/db/migration/`, springdoc `@Operation`/`@Tag`).

## Schema change (data-model gap → Flyway migration)

`banking_core_transaction` has no timestamp column, so ordering/date filtering is
impossible. Add one via a **new** migration (never edit an applied file):

`db/migration/V1.0.20260715143500__add_created_at_to_transaction.sql`

```sql
ALTER TABLE `banking_core_transaction`
    ADD COLUMN `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
```

Existing rows backfill to `CURRENT_TIMESTAMP` via the column default.

Reflect it on `TransactionEntity`:

```java
@Column(name = "created_at")
@Builder.Default
private LocalDateTime createdAt = LocalDateTime.now();
```

`@Builder.Default` ensures new transactions written by `TransactionService`
(fund transfer / utility payment builders) get a timestamp.

## DTO

`model/dto/TransactionHistoryDto` (`@Data @Builder`, matching existing DTO style):

| field           | type              |
|-----------------|-------------------|
| id              | Long              |
| amount          | BigDecimal        |
| transactionType | TransactionType   |
| referenceNumber | String            |
| createdAt       | LocalDateTime     |

## Repository query

Derived query on `TransactionRepository`, ordering lives here (most-recent-first):

```java
List<TransactionEntity> findByAccount_NumberOrderByCreatedAtDesc(String accountNumber);
```

Traverses the `account` association to `BankAccountEntity.number`.

## Service method

`TransactionService.getTransactionHistory(...)` fetches the account's ordered rows,
applies **date-range + type** filtering, maps to `TransactionHistoryDto`, and
returns a paginated slice:

```java
Page<TransactionHistoryDto> getTransactionHistory(
        String accountNumber,
        LocalDate fromDate,      // nullable
        LocalDate toDate,        // nullable
        TransactionType type,    // nullable
        Pageable pageable)
```

Filtering rules:
- `type` — keep when `type == null || type.equals(tx.transactionType)`.
- date range — **inclusive**: keep when `createdAt >= fromDate.atStartOfDay()`
  and `createdAt <= toDate.atTime(LocalTime.MAX)`. Using `isBefore`/`isAfter`
  against these bounds preserves boundary rows (the classic drop-the-boundary
  bug the tests guard against).

The filtered/mapped list is wrapped in a `PageImpl` honouring the requested
`Pageable` (offset/size) with `totalElements` = full filtered size.

## Endpoint contract

Added to `AccountController` (class base `/api/v1/account`, so the method maps to
the exact target URL and reuses the existing `@Tag`; `TransactionService` is
injected via the existing `@RequiredArgsConstructor`):

```
GET /api/v1/account/{accountNumber}/transactions
      ?fromDate=YYYY-MM-DD   (optional, ISO date)
      &toDate=YYYY-MM-DD     (optional, ISO date)
      &type=FUND_TRANSFER|UTILITY_PAYMENT  (optional)
      &page=0&size=20        (standard Spring Data pageable)
```

Response `200 OK` — a Spring `Page` of `TransactionHistoryDto`:

```json
{
  "content": [
    { "id": 12, "amount": -100.00, "transactionType": "FUND_TRANSFER",
      "referenceNumber": "1002", "createdAt": "2026-07-14T10:15:30" }
  ],
  "totalElements": 1, "totalPages": 1, "number": 0, "size": 20
}
```

Annotated with `@Operation` matching the surrounding controller style.

## Testing plan

Service-layer tests in `TransactionServiceTest` (JUnit 5 + Mockito, constructor
wiring — no Spring context), mocking `findByAccount_NumberOrderByCreatedAtDesc`:
happy path (maps + preserves order), empty result (empty page), and date-range
filtering including inclusive boundary.
