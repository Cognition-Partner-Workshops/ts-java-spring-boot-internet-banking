# SPEC — Statement Export (CSV / JSON projections of transaction history)

## User need
An account holder (or a downstream service) needs to export the transaction
history of a single bank account as a **statement**, in either a machine-friendly
**JSON** projection or a spreadsheet-friendly **CSV** document, optionally scoped
to a date period. The export must be a *stable, typed contract* — not raw entity
serialization — so it can be consumed by reconciliation tooling without breaking
when the persistence model changes.

## Scope
- Module: `core-banking-service` only.
- Extends existing transaction symbols: `TransactionEntity`
  (`banking_core_transaction`), `TransactionRepository`, `TransactionService`,
  `BankAccountEntity.number`, `TransactionType`, and the `/api/v1/account/...`
  route family (`AccountController`).

## Endpoint contract
```
GET /api/v1/account/{accountNumber}/statement/export
      ?format=JSON|CSV        (optional, default JSON, case-insensitive)
      &from=YYYY-MM-DD         (optional, ISO_LOCAL_DATE, inclusive)
      &to=YYYY-MM-DD           (optional, ISO_LOCAL_DATE, inclusive)
```
- `format=JSON` → `200`, `Content-Type: application/json`, body = `StatementExportDto`.
- `format=CSV`  → `200`, `Content-Type: text/csv`, body = CSV document.
- Both responses set `Content-Disposition: attachment; filename="statement-{accountNumber}.{ext}"`.

### JSON projection (`StatementExportDto`) — deterministic field order
```
accountNumber : string
fromDate      : string|null (ISO date, echoes request filter)
toDate        : string|null
count         : integer (number of lines)
lines         : StatementLine[]   (most-recent-first)
```
`StatementLine` — deterministic field order:
```
timestamp        : string|null (ISO_LOCAL_DATE_TIME)
transactionId    : string
transactionType  : "FUND_TRANSFER" | "UTILITY_PAYMENT"
amount           : number (signed; debits negative, credits positive)
referenceNumber  : string
accountNumber    : string
```
The projection exposes only these typed fields — never the JPA entity graph
(no `account` object, no lazy relations).

### CSV document — deterministic header & column order
Header (exact, in this order), RFC-4180 quoting, CRLF (`\r\n`) line terminators:
```
timestamp,transactionId,transactionType,amount,referenceNumber,accountNumber
```
One data row per transaction, same order as the JSON `lines`. A field is wrapped
in double quotes iff it contains a comma, double-quote, CR or LF; embedded
double-quotes are doubled (`"` → `""`). Empty export → header row only.

## Ordering
Transactions are returned **most-recent-first**: `timestamp` descending, with
`id` descending as a deterministic tie-breaker (a single fund transfer writes two
rows that may share a timestamp, so a tie-breaker is required for determinism).

## Date filtering (inclusive, day granularity)
`from` and `to` are calendar dates compared against each transaction's
`timestamp.toLocalDate()`. A transaction is included iff its date is **not before
`from`** and **not after `to`** — i.e. both bounds are **inclusive**. Either bound
may be omitted (open-ended). Transactions with a `null` timestamp are excluded
whenever any bound is supplied, and included when no bound is supplied.

## Pagination semantics
This endpoint intentionally does **not** paginate: a statement/CSV is a single
self-contained document, and fragmenting it across pages would break totals and
CSV consumers. The **date filter is the bounding mechanism** for large histories.
`count` reflects the full returned set. If per-account volume ever requires it, a
separate paged transaction-list endpoint should be added rather than paginating
this export.

## Acceptance criteria
1. JSON happy path: known transactions → `count` and `lines` match, typed fields only.
2. CSV happy path: exact header line, one row per transaction, correct column order.
3. Empty export: JSON `count=0` / empty `lines`; CSV = header row only.
4. Ordering: output is most-recent-first, `id` desc tie-break for equal timestamps.
5. Inclusive boundaries: transactions dated exactly on `from` and on `to` are included.
6. CSV escaping: fields with comma / quote / newline are quoted and quote-doubled.
7. JSON projection: response contains the typed line fields, not entity serialization.
8. Invalid period: `from > to` → error (`INVALID_STATEMENT_PERIOD`, HTTP 400).
9. Account not found: unknown `accountNumber` → `EntityNotFoundException` (HTTP 400).
10. Unsupported format: `format` not in {CSV, JSON} → error (`UNSUPPORTED_EXPORT_FORMAT`, HTTP 400).
11. Newly written transactions (fund transfer, utility payment) receive a timestamp.
