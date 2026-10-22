# DESIGN — Statement Export

Grounded in the existing symbols: `TransactionEntity`, `TransactionRepository`,
`TransactionService`, `BankAccountEntity.number`, `TransactionType`,
`AccountController`, `SimpleBankingGlobalException` / `GlobalExceptionHandler`,
and the Flyway migration convention `V1.0.<timestamp>__<desc>.sql`.

## 1. Persistence — add a transaction timestamp
`banking_core_transaction` has no time column, so history cannot be ordered or
date-filtered. Add one via a **new** migration (never edit an applied one):

`V1.0.20260715120000__add_timestamp_to_transaction.sql`
```sql
ALTER TABLE `banking_core_transaction` ADD COLUMN `timestamp` datetime DEFAULT NULL;
```
`TransactionEntity` gains `private LocalDateTime timestamp;`. A JPA `@PrePersist`
hook defaults it to `LocalDateTime.now()` when unset, so every persisted row is
timestamped even for code paths that don't set it explicitly. `TransactionService`
also sets it explicitly on the rows it builds (fund transfer × 2, utility payment)
so the behavior is deterministic and unit-testable. Column is nullable to remain
backward-compatible with pre-existing rows.

## 2. Format model — `model/StatementFormat`
`enum StatementFormat { CSV, JSON }`, mirroring the existing `model/*` enums
(`TransactionType`, `AccountType`). Parsing is case-insensitive; an unknown value
raises `UnsupportedExportFormatException`.

## 3. DTOs — typed projection (`model/dto/response`)
Java `record`s (immutable, deterministic component order = serialized field order):
- `StatementLineDto(LocalDateTime timestamp, String transactionId,
   TransactionType transactionType, BigDecimal amount, String referenceNumber,
   String accountNumber)`
- `StatementExportDto(String accountNumber, LocalDate fromDate, LocalDate toDate,
   int count, List<StatementLineDto> lines)`
- `StatementExportResult(StatementFormat format, StatementExportDto data,
   String csvBody)` — service→controller carrier; `csvBody` is null for JSON.

Records are chosen for the API contract because they pin field order and prevent
accidental entity leakage (the entity's `account`/lazy graph is never referenced).

## 4. Repository query — ordering at the data layer
`TransactionRepository` (currently empty) gains one idiomatic derived query:
```java
List<TransactionEntity> findByAccount_NumberOrderByTimestampDescIdDesc(String accountNumber);
```
This scopes to the account (nested `account.number`) and orders most-recent-first
at the DB. `TransactionType`/filtering that is boundary-sensitive stays in the
service so it is unit-testable without a Spring context.

## 5. Service — projection, filtering, export (`TransactionService.exportStatement`)
```
exportStatement(accountNumber, format, from, to):
  fmt = parseFormat(format)                 # -> UnsupportedExportFormatException
  validatePeriod(from, to)                  # from>to -> InvalidStatementPeriodException
  accountService.readBankAccount(number)    # -> EntityNotFoundException if absent
  rows = repo.findByAccount_NumberOrderByTimestampDescIdDesc(number)
  lines = rows.stream()
            .filter(withinRange(from, to))   # INCLUSIVE both ends, day granularity
            .sorted(STATEMENT_ORDER)         # timestamp desc, id desc tie-break
            .map(toLine).toList()
  data = new StatementExportDto(number, from, to, lines.size(), lines)
  return CSV ? result(fmt, data, StatementCsvRenderer.render(data))
             : result(fmt, data, null)
```
`withinRange` uses inclusive comparisons (`!date.isBefore(from) && !date.isAfter(to)`)
— **not** exclusive `isAfter/isBefore`, which would silently drop boundary rows
(the classic statement bug). Sorting is re-applied in the service (with the `id`
tie-break) so ordering is deterministic regardless of provider ordering.

## 6. CSV rendering — `service/StatementCsvRenderer`
Static renderer producing the fixed header
`timestamp,transactionId,transactionType,amount,referenceNumber,accountNumber`,
RFC-4180 escaping (quote iff field contains `, " \r \n`; double embedded quotes),
CRLF terminators, `amount` via `BigDecimal.toPlainString()`, `timestamp` via
`toString()` (ISO_LOCAL_DATE_TIME), enum via `.name()`. Empty export → header only.

## 7. Controller — `AccountController` (base `/api/v1/account`)
Add `GET /{accountNumber}/statement/export` mirroring the existing
`@PathVariable` + `@GetMapping` style. `TransactionService` is injected via the
existing Lombok `@RequiredArgsConstructor`. `from`/`to` bind as `LocalDate` with
`@DateTimeFormat(iso = DATE)`. CSV → `text/csv` body (`csvBody`); JSON →
`application/json` body (`data`); both set `Content-Disposition: attachment`.

## 8. Errors — reuse the global handler
New `SimpleBankingGlobalException` subclasses + codes in `GlobalErrorCode`:
`INVALID_STATEMENT_PERIOD = BANKING-CORE-SERVICE-1002`,
`UNSUPPORTED_EXPORT_FORMAT = BANKING-CORE-SERVICE-1003`. `GlobalExceptionHandler`
already maps `SimpleBankingGlobalException` → HTTP 400 `ErrorResponse`, so no
handler change is needed; `EntityNotFoundException` covers account-not-found.

## 9. Tests (`TransactionServiceTest`, JUnit 5 + Mockito, no Spring context)
Mock `AccountService` + `TransactionRepository`; assert the `StatementExportDto`
and `csvBody` directly. Cover AC 1–11: JSON/CSV happy, empty, ordering + tie-break,
inclusive `from`/`to` boundaries, CSV escaping & header order, JSON typed
projection, invalid period, account-not-found, unsupported format, and timestamp
assignment on newly written transactions.
