# SPEC — Account Transaction History / Statement

Module: `core-banking-service`
Stage: 1 — Requirements

## Context (existing behaviour analysed)

- `banking_core_transaction` rows are written today only by `TransactionService.fundTransfer`
  and `TransactionService.utilPayment` (`controller/TransactionController`,
  `POST /api/v1/transaction/{fund-transfer,util-payment}`).
- `TransactionEntity` currently has **no timestamp** column, so transactions cannot be
  ordered or filtered by time. Every history/statement requirement below depends on
  adding one.
- `TransactionRepository` is an empty `JpaRepository` — no read methods exist.
- Accounts are looked up by `BankAccountEntity.number` (a `String`);
  `AccountService.readBankAccount` throws `EntityNotFoundException` when the account
  is missing.

## User story

As an internet-banking customer, I want to retrieve the transaction history (statement)
for one of my accounts — optionally narrowed to a date range — so that I can review the
activity on that account.

## Functional scope

A read-only endpoint on the core banking service that returns the transactions belonging
to a single bank account, most-recent-first, with optional inclusive date-range filtering.

## Acceptance criteria (testable)

- **AC1 — List all:** `GET /api/v1/account/{account_number}/transactions` returns `200`
  with every transaction for the account, ordered by `createdAt` descending
  (most-recent-first).
- **AC2 — Date range filter:** When `fromDate` and `toDate` (`yyyy-MM-dd`) are supplied,
  only transactions whose `createdAt` falls within `[fromDate 00:00:00.000,
  toDate 23:59:59.999999999]` are returned, still ordered most-recent-first.
- **AC3 — Boundary inclusivity:** A transaction created exactly at the start of `fromDate`,
  or at any instant during `toDate`, **is included** in the filtered result. (Exclusive
  comparison that drops boundary rows is a defect.)
- **AC4 — Empty result:** An existing account with no transactions — or none inside the
  requested range — returns `200` with an empty list (`[]`).
- **AC5 — Unknown account:** A non-existent `account_number` results in an
  `EntityNotFoundException` (surfaced as `400` with the standard `ErrorResponse` by
  `GlobalExceptionHandler`), so callers can distinguish "no such account" from
  "account has no transactions".
- **AC6 — Response shape / no data leak:** Each item exposes `transactionId`,
  `referenceNumber`, `transactionType`, `amount`, and `createdAt`. It does **not** expose
  account balances or the account owner (customer PII).
- **AC7 — Timestamped writes:** Newly created transactions (fund transfer, utility
  payment) persist a `createdAt` timestamp so they appear in history and honour filtering.
- **AC8 — Partial range rejected:** Supplying only one of `fromDate` / `toDate` is a
  client error (`400`); the range requires both bounds or neither.

## Out of scope

- Pagination / sorting options beyond most-recent-first.
- Cross-account or per-customer aggregated statements.
- Changes to any module other than `core-banking-service`.
- Authentication/authorization (handled upstream by the API gateway).
