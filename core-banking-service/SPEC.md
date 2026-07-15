# SPEC — Account Statement & Transaction History

## Context (analysis of existing code)

The `core-banking-service` module already records money movements as
`TransactionEntity` rows in the `banking_core_transaction` table:

- `controller/TransactionController` — `@RequestMapping("/api/v1/transaction")`,
  exposes `POST /fund-transfer` and `POST /util-payment` only. There is **no**
  read endpoint for transactions today.
- `service/TransactionService` — `@Service @Transactional`, writes
  `TransactionEntity` rows on transfers / utility payments.
- `repository/TransactionRepository` — empty `JpaRepository<TransactionEntity, Long>`;
  no query methods.
- `model/entity/TransactionEntity` — fields `id`, `amount`, `transactionType`
  (`FUND_TRANSFER` | `UTILITY_PAYMENT`), `referenceNumber`, `transactionId`, and
  `account` (`@OneToOne` → `BankAccountEntity`, whose account number is
  `BankAccountEntity.number`).

**Key data-model gap:** `TransactionEntity` / `banking_core_transaction` has **no
timestamp column**, so transactions cannot currently be ordered chronologically
or filtered by date range. A statement feature requires a timestamp.

## User stories

- **US-1 — List account transactions.** As an account holder, I want to retrieve
  the list of transactions for a given account number so that I can review my
  statement.
- **US-2 — Filter by date range.** As an account holder, I want to filter my
  statement to a date range so that I can see activity for a specific period.
- **US-3 — Filter by transaction type.** As an account holder, I want to filter
  by transaction type (`FUND_TRANSFER` / `UTILITY_PAYMENT`) so that I can isolate
  a category of activity.
- **US-4 — Paginate results.** As an account holder with many transactions, I want
  results returned page by page so that responses stay small and fast.

## Acceptance criteria (testable)

- **AC-1** `GET /api/v1/account/{accountNumber}/transactions` returns transactions
  belonging only to the given account number.
- **AC-2** Results are ordered **most-recent-first** (descending timestamp).
- **AC-3** Each returned item exposes: transaction id, amount, transaction type,
  reference number, and timestamp.
- **AC-4** When the account has no matching transactions, the endpoint returns an
  empty page (not an error), with `totalElements = 0`.
- **AC-5** When `fromDate` and/or `toDate` are supplied, only transactions whose
  timestamp falls within the range are returned. The range is **inclusive** on
  both ends (a transaction dated exactly on `fromDate` or `toDate` is included).
- **AC-6** When `type` is supplied, only transactions of that type are returned.
  Filters combine (AND) with the date range.
- **AC-7** Results are paginated via standard `page` / `size` query params and the
  response reports total element / page metadata.
