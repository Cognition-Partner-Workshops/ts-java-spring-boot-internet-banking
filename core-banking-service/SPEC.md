# SPEC — Account Statement & Transaction History

## User need

Account holders (and downstream services such as the internet-banking gateway)
need to read an account's transaction history — an account statement — filtered
by date range and transaction type, most recent first, with pagination.

## Endpoint contract

`GET /api/v1/account/{accountNumber}/transactions`

Query parameters (all optional):

| Param  | Type                | Meaning                                            |
|--------|---------------------|----------------------------------------------------|
| `from` | ISO-8601 date-time  | Range start, **inclusive**                         |
| `to`   | ISO-8601 date-time  | Range end, **inclusive**                           |
| `type` | `TransactionType`   | `FUND_TRANSFER` or `UTILITY_PAYMENT`               |
| `page` | int, default `0`    | Zero-based page index                              |
| `size` | int, default `20`   | Page size                                          |

Response: `200 OK` with a JSON array of transaction history items:

```json
[
  {
    "id": 3,
    "amount": -50.00,
    "type": "UTILITY_PAYMENT",
    "referenceNumber": "REF123",
    "timestamp": "2026-08-10T09:15:00"
  }
]
```

Ordering: newest first by the transaction's `created_at` timestamp.

## Acceptance criteria

1. Returns all transactions belonging to the given account number, newest
   first, when no filters are supplied.
2. Date-range filtering is **inclusive on both ends**: a transaction whose
   timestamp equals `from` or `to` is included.
3. `type` filtering returns only transactions of the requested
   `TransactionType`.
4. Pagination: `page`/`size` slice the filtered, ordered result; a page past
   the end returns an empty list.
5. An account with no (matching) transactions returns `200 OK` with `[]` —
   not an error.

## Edge cases

- Empty result (unknown account, filters that match nothing, out-of-range
  page) → empty list.
- Boundary timestamps exactly equal to `from` / `to` must be included
  (inclusive semantics; exclusive comparisons would silently drop the first or
  last transaction of a statement period).
- `from` without `to` (open-ended range) and vice versa are both valid.
