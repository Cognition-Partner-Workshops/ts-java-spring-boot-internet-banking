# DESIGN — Monthly Statement Summary

Grounded in existing symbols in `com.javatodev.finance`. Every layer mirrors an
established repo pattern; the change is confined to `core-banking-service`.

## 1. Persistence — add a transaction timestamp
`banking_core_transaction` (created by
`V1.0.20210429210839__create_transaction_table.sql`) has no time column, so a
period query is impossible today. Add one via a **new** Flyway migration
(never edit an applied one), following the repo's
`V1.0.<timestamp>__<description>.sql` naming:

`V1.0.20260715000000__add_transaction_timestamp.sql`
```sql
ALTER TABLE `banking_core_transaction`
    ADD COLUMN `transaction_timestamp` datetime DEFAULT NULL;
```
Column name `transaction_timestamp` avoids the SQL reserved word `timestamp`.

## 2. Entity — `TransactionEntity`
Add a field mapped to the new column (Lombok `@Builder/@Getter/@Setter` already
present):
```java
@Column(name = "transaction_timestamp")
private LocalDateTime timestamp;
```
Newly written transactions get a timestamp: the three `TransactionEntity.builder()`
sites in `TransactionService` (`internalFundTransfer` x2, `utilPayment`) set
`.timestamp(LocalDateTime.now())`.

## 3. DTO — response (`model/dto/response`, Lombok `@Builder`, matching `FundTransferResponse`)
```java
StatementSummaryResponse { String accountNumber; LocalDate from; LocalDate to;
                           BigDecimal netTotal; List<TransactionTypeTotal> totals; }
TransactionTypeTotal     { TransactionType transactionType; BigDecimal totalAmount;
                           long transactionCount; }
```
The repo's DTOs are Lombok classes (not records), so these follow suit.

## 4. Repository — `TransactionRepository`
Ordering/filtering live in the repository (skill guidance). Add an explicit
`@Query`; the window is passed as instants and matched with **inclusive**
`BETWEEN` (Spring Data / JPQL `BETWEEN` is inclusive — the deliberate choice that
avoids the exclusive-bound boundary bug):
```java
@Query("SELECT t FROM TransactionEntity t " +
       "WHERE t.account.number = :accountNumber " +
       "AND t.timestamp BETWEEN :start AND :end")
List<TransactionEntity> findForStatement(String accountNumber,
                                         LocalDateTime start, LocalDateTime end);
```

## 5. Service — `TransactionService.getStatementSummary(accountNumber, from, to)`
`@Service @Transactional` (already). Steps:
1. `accountService.readBankAccount(accountNumber)` — reuses the existing lookup so
   an unknown account throws `EntityNotFoundException` (criterion 7).
2. Validate range: if `from.isAfter(to)` throw
   `SimpleBankingGlobalException(msg, GlobalErrorCode.INVALID_DATE_RANGE)`
   (new code `BANKING-CORE-SERVICE-1002`) → 400 via `GlobalExceptionHandler`
   (criterion 6).
3. Compute inclusive window: `start = from.atStartOfDay()`,
   `end = to.atTime(LocalTime.MAX)` (criterion 5).
4. `findForStatement(...)`, then aggregate in-memory grouped by `transactionType`:
   sum signed `amount`, count rows.
5. Build `totals` sorted by `transactionType.name()` (deterministic, criterion 2),
   `netTotal` = sum of all; empty list + `ZERO` when no rows (criterion 4).

## 6. Controller — `AccountController`
The route is account-scoped, so it lives on `AccountController`
(`@RequestMapping("/api/v1/account")`), mirroring its `@GetMapping` +
`@PathVariable` style; `TransactionService` is added via the existing
`@RequiredArgsConstructor`:
```java
@GetMapping("/{accountNumber}/statement-summary")
ResponseEntity getStatementSummary(@PathVariable String accountNumber,
    @RequestParam @DateTimeFormat(iso = DATE) LocalDate from,
    @RequestParam @DateTimeFormat(iso = DATE) LocalDate to)
```
`@Operation` + existing `@Tag` documented per repo convention.

## 7. Verification
JUnit 5 + Mockito, constructor-wired `TransactionService` (no Spring context), per
`TransactionServiceTest`. Boundary inclusivity is asserted at the service contract
with an `ArgumentCaptor` proving the repository is queried with
`from.atStartOfDay()` .. `to.atTime(LocalTime.MAX)`. Gate: `./gradlew test` green,
then `./gradlew build`.

## Pagination
Not applicable — bounded aggregate (≤ one row per `TransactionType`); no paging
parameters (see SPEC).
