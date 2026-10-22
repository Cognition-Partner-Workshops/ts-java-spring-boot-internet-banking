# SPEC — Monthly Statement Summary (totals by transaction type)

## User need
An account holder (or downstream service) needs a monthly statement summary for a
single bank account over a requested period: the net signed total and count of
transactions, broken down by each transaction type that occurred in the period.
This supports statement generation and reconciliation.

## Endpoint contract
```
GET /api/v1/account/{accountNumber}/statement-summary?from={ISO_DATE}&to={ISO_DATE}
```
- `accountNumber` (path) — `BankAccountEntity.number`.
- `from`, `to` (query, required) — ISO-8601 dates (`yyyy-MM-dd`). The period is the
  closed date interval `[from, to]`, **inclusive on both ends**. The interval maps
  to instants `from.atStartOfDay()` .. `to.atTime(LocalTime.MAX)`.

### Response `200 OK` — `StatementSummaryResponse`
```json
{
  "accountNumber": "0000000001",
  "from": "2021-01-01",
  "to": "2021-01-31",
  "netTotal": -150.00,
  "totals": [
    { "transactionType": "FUND_TRANSFER",  "totalAmount": -100.00, "transactionCount": 2 },
    { "transactionType": "UTILITY_PAYMENT", "totalAmount": -50.00,  "transactionCount": 1 }
  ]
}
```
- `totals` contains **one entry per transaction type that is present in the
  period** (types with no transactions are omitted).
- `totalAmount` sums the **signed** `banking_core_transaction.amount` (debits are
  stored negative), so the summary reconciles against balance movement.
- `netTotal` is the signed sum across all included types.
- **Deterministic output:** `totals` is ordered by `transactionType` name
  (enum natural/alphabetical order), independent of row insertion order.

## Aggregation semantics
- Group the account's transactions whose timestamp is within `[from, to]` by
  `transactionType`; sum `amount` and count rows per type.
- Amounts are `BigDecimal` (scale 2); summation preserves scale/sign exactly.

## Acceptance criteria
1. **Happy path** — a single-type period returns one total with the exact signed
   sum and count.
2. **Multiple types** — a period spanning both `FUND_TRANSFER` and
   `UTILITY_PAYMENT` returns one entry per type, each with the exact signed total
   and count, ordered deterministically by type name.
3. **Exact monetary totals** — totals equal the arithmetic sum of the signed
   amounts (no rounding drift), asserted with `BigDecimal` equality.
4. **Empty result** — an account with no transactions in the period returns a
   well-defined empty summary: `totals` empty, `netTotal` = `0`, `from`/`to`/
   `accountNumber` echoed. (200, not an error.)
5. **Inclusive boundaries** — transactions timestamped exactly at `from` 00:00:00
   and exactly at `to` 23:59:59.999999999 are included. The service must query the
   window `from.atStartOfDay()` .. `to.atTime(LocalTime.MAX)` inclusively.
6. **Invalid range** — `from` after `to` is rejected as a client error
   (`SimpleBankingGlobalException`, code `BANKING-CORE-SERVICE-1002`) → HTTP 400.
7. **Account not found** — unknown `accountNumber` yields the existing
   `EntityNotFoundException` (code `BANKING-CORE-SERVICE-1000`) → HTTP 400,
   consistent with other account reads.

## Edge cases
- Empty period (criterion 4).
- Single-day period (`from == to`) is valid and covers that whole day.
- Boundary rows at both ends (criterion 5).

## Pagination
**Not applicable.** The response is a bounded aggregate (at most one row per
`TransactionType`), so there is no page/size/sort parameter. The full summary is
always returned in a single deterministic response.
