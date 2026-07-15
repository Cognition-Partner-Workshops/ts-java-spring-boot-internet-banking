# SPEC — Balance-as-of-date query for an account

## User need

Operations and reconciliation staff need to know what an account's balance
*was* at a specific point in time (e.g. end of a statement period), not only its
current balance. The core-banking ledger stores each account's current balance
(`BankAccountEntity.actualBalance`) and an append-only, signed transaction log
(`TransactionEntity.amount`, negative for debits, positive for credits). This
feature exposes the historical balance derived rigorously from those two sources.

## Endpoint contract

```
GET /api/v1/account/{accountNumber}/balance?asOf=<ISO-8601 date-time>
```

- Path variable `accountNumber` — the bank account `number` (e.g. `100015003000`).
- Query parameter `asOf` — **required**, ISO-8601 local date-time
  (`yyyy-MM-dd'T'HH:mm:ss`, e.g. `2026-07-15T14:00:00`).

Success `200 OK` returns a stable JSON DTO:

```json
{
  "accountNumber": "100015003000",
  "asOf": "2026-07-15T14:00:00",
  "balance": 100000.00
}
```

## Semantics (provable from the current ledger model)

The stored `actualBalance` is the balance *after every* transaction. Because
transaction amounts are **signed**, the balance as of instant `T` is the current
balance with every transaction that happened strictly after `T` reversed:

```
balance(T) = actualBalance - Σ amount[i]   for every transaction i with timestamp[i] > T
```

- **Inclusive boundary:** a transaction whose timestamp equals `asOf` is treated
  as having happened by `asOf` and is therefore *included* in the returned
  balance (it is NOT reversed). Only strictly-after transactions are reversed.
- **Signed amounts:** reversing a signed amount naturally restores both credits
  (subtract the positive amount) and debits (subtract the negative amount, i.e.
  add it back).
- No historical opening balance is fabricated; the result is derived from the
  stored current balance and the timestamped, signed ledger only.

## Acceptance criteria

1. **Happy path:** account with transactions before and after `asOf` returns
   `actualBalance` minus the sum of strictly-after signed amounts.
2. **No history:** account with no transactions returns `actualBalance` unchanged.
3. **Inclusive boundary:** a transaction whose timestamp equals `asOf` is included
   (not reversed).
4. **Signed credits/debits:** a post-`asOf` credit lowers the historical balance;
   a post-`asOf` debit raises it.
5. **`asOf` after all transactions (current cutoff / future):** returns the
   current `actualBalance`.
6. **`asOf` before all transactions:** returns the derived opening balance (all
   transactions reversed).
7. **Invalid input:** missing/null `asOf` is rejected as a bad request.
8. **Account not found:** unknown `accountNumber` yields the standard
   entity-not-found error.

## Ordering & pagination

**Not applicable.** The result is a single scalar balance (one DTO), not a
collection, so ordering and pagination have no meaning here.
