# DESIGN — Account Statement & Transaction History

Each decision is tied to an existing pattern in this module.

## Schema change (Flyway migration)

New additive migration `V1.0.20260812064900__add_created_at_to_transaction_table.sql`
adding `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP` to
`banking_core_transaction`. Follows the `V1.0.<timestamp>__<description>.sql`
naming used by the existing migrations in `src/main/resources/db/migration/`;
applied migrations are never edited.

## Entity

`TransactionEntity` gains a `createdAt` field mapped to `created_at` with
Hibernate `@CreationTimestamp`, so it is populated on insert without touching
the existing write paths (`fundTransfer`, `utilPayment`).

## DTO

`model/dto/TransactionHistoryDto` — Lombok `@Builder @Getter @Setter` class
(same shape as `FundTransferResponse`/`UtilityPaymentResponse`) with fields
`id`, `amount`, `type`, `referenceNumber`, `timestamp`.

## Repository

`TransactionRepository.findByAccount_NumberOrderByCreatedAtDesc(String accountNumber)`
— a Spring Data derived query traversing `TransactionEntity.account`
(`BankAccountEntity.number`), ordering newest-first in the query itself so the
ordering contract lives in the repository layer.

## Service

`TransactionService.getTransactionHistory(accountNumber, from, to, type, page, size)`:

1. Fetch the account's transactions newest-first from the repository.
2. Apply **inclusive** date-range filtering: `!createdAt.isBefore(from)` and
   `!createdAt.isAfter(to)` (never `isAfter`/`isBefore`, which are exclusive
   and drop boundary rows).
3. Apply optional `TransactionType` filtering.
4. Slice by `page`/`size` and map to `TransactionHistoryDto`.

Lives in the existing `@Service @Transactional @RequiredArgsConstructor`
`TransactionService`, keeping constructor wiring testable with plain Mockito
(as in `TransactionServiceTest`).

## Controller

`TransactionController.getTransactionHistory` —
`@GetMapping("/api/v1/account/{accountNumber}/transactions")` with `@Operation`
summary/description and the existing `@Tag`, mirroring `AccountController`'s
`@PathVariable` + `@GetMapping` read style. `from`/`to` bound with
`@DateTimeFormat(iso = ISO.DATE_TIME)`.

## Verification

`./gradlew test` in `core-banking-service` is the gate. New tests in
`TransactionServiceTest` cover happy path, empty result, most-recent-first
ordering, and inclusive range boundaries.
