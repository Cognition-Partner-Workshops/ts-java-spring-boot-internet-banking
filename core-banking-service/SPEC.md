# SPEC — Account Transaction History Endpoint

## User need

An account holder (and internal banking UIs) must be able to retrieve the
statement / transaction history for a single bank account, optionally narrowed
to a date period, with the most recent activity shown first. This backs
statement views and reconciliation, so ordering and date-boundary semantics must
be exact.

## Endpoint contract

```
GET /api/v1/account/{accountNumber}/transactions
```

| Part            | Value                                                                 |
|-----------------|-----------------------------------------------------------------------|
| Path variable   | `accountNumber` — `BankAccountEntity.number` of the target account    |
| Query param     | `from` (optional) — ISO local date `yyyy-MM-dd`, inclusive lower bound |
| Query param     | `to`   (optional) — ISO local date `yyyy-MM-dd`, inclusive upper bound |
| Success         | `200 OK` with a JSON array of transaction line items (most-recent-first) |
| Account missing | `400 Bad Request` via existing `EntityNotFoundException` handling      |

### Response line item (`TransactionResponse`)

```json
{
  "id": 12,
  "transactionId": "b3f1...",
  "referenceNumber": "A2",
  "transactionType": "FUND_TRANSFER",
  "amount": -100.00,
  "accountNumber": "A1",
  "timestamp": "2026-07-10T23:30:00"
}
```

## Filtering / ordering / pagination

- **Date period (optional, inclusive on both ends).** A `from` date is
  interpreted as `from.atStartOfDay()`; a `to` date is interpreted as
  `to.atTime(LocalTime.MAX)` (end of that day). A transaction whose timestamp
  falls on either boundary day — including later in the `to` day — is included.
  Combinations:
  - neither → all transactions for the account
  - both → timestamp within `[from 00:00:00, to 23:59:59.999999999]`
  - `from` only → timestamp `>=` start of `from`
  - `to` only → timestamp `<=` end of `to`
- **Ordering.** Deterministic most-recent-first: `timestamp` descending, with
  `id` descending as a tie-breaker so rows sharing a timestamp keep a stable
  order.
- **Pagination.** The initial endpoint is **non-paginated** — no existing
  endpoint in this module paginates, so pagination is deliberately out of scope
  for v1 and returns the full ordered list. This is documented so a paged
  variant can be added later without breaking the contract.

## Acceptance criteria

1. **Happy path** — a known account with transactions returns them all as
   `TransactionResponse` items with correct fields.
2. **Ordering** — results are strictly most-recent-first (`timestamp` desc,
   `id` desc tie-break).
3. **Empty result** — a known account with no matching transactions returns
   `200 OK` and an empty array (not an error).
4. **Inclusive boundaries** — with `from`/`to` set, a transaction whose
   timestamp equals a boundary (e.g. `to` day at 23:30) is included, not dropped.
5. **Filter behaviour** — `from`/`to`/both/neither each narrow the set to the
   correct inclusive datetime window.
6. **Account not found** — an unknown `accountNumber` raises the existing
   `EntityNotFoundException` (→ `400` with error code
   `BANKING-CORE-SERVICE-1000`), consistent with `AccountService.readBankAccount`.

## Edge cases

- Empty history and empty filtered window both yield `[]`, never null.
- Rows with equal timestamps are ordered deterministically by `id` desc.
- New transactions written by fund-transfer and utility-payment flows now carry
  a `timestamp`, so they appear in history immediately.
