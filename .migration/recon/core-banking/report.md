# Recon report: unit `core-banking`

- **Verdict: PASS** (values redacted)
- Mode: `live`
- Merge eligible: yes (fixture/continuous evidence never merges)
- Mapping version: `map-draft-3` (sha256 `669b4e98932d`)
- Tolerance version: `tol-1` (sha256 `8ea506ef76b1`)
- Collections: `bankingCoreAccount`, `bankingCoreUser`, `bankingCoreTransaction`, `bankingCoreUtilityAccount`
- Seed: `1`
- Generated: 2026-10-07T02:40:58.728468+00:00
- 17 fields: Tier 2 aggregates deferred to Tier 3 (rules change the value)
- 12 string fields: min/max/distinct deferred to Tier 3

| Tier | Name | Checks | Result |
|---|---|---|---|
| 1 | counts_through_mapping | 9 | PASS |
| 2 | per_field_aggregates | 5 | PASS |
| 3 | keyed_diffs | 24 | PASS |
| 4 | app_level_parity | 7 | PASS |

## Tier 1 coverage
```json
{
  "source_counts": {
    "bankingCoreAccount": 14,
    "bankingCoreUser": 4,
    "bankingCoreTransaction": 0,
    "bankingCoreUtilityAccount": 6
  }
}
```

## Tier 2 coverage
```json
{
  "deferred_to_tier3": [
    "bankingCoreAccount.actualBalance",
    "bankingCoreAccount.availableBalance",
    "bankingCoreAccount.number",
    "bankingCoreAccount.status",
    "bankingCoreAccount.type",
    "bankingCoreAccount.userId",
    "bankingCoreUser.email",
    "bankingCoreUser.firstName",
    "bankingCoreUser.identificationNumber",
    "bankingCoreUser.lastName",
    "bankingCoreTransaction.amount",
    "bankingCoreTransaction.transactionType",
    "bankingCoreTransaction.referenceNumber",
    "bankingCoreTransaction.transactionId",
    "bankingCoreTransaction.accountId",
    "bankingCoreUtilityAccount.number",
    "bankingCoreUtilityAccount.providerName"
  ],
  "string_aggregates_deferred_to_tier3": [
    {
      "field": "bankingCoreAccount.number",
      "stats": [
        "min",
        "max",
        "distinct_count"
      ]
    },
    {
      "field": "bankingCoreAccount.status",
      "stats": [
        "min",
        "max",
        "distinct_count"
      ]
    },
    {
      "field": "bankingCoreAccount.type",
      "stats": [
        "min",
        "max",
        "distinct_count"
      ]
    },
    {
      "field": "bankingCoreUser.email",
      "stats": [
        "min",
        "max",
        "distinct_count"
      ]
    },
    {
      "field": "bankingCoreUser.firstName",
      "stats": [
        "min",
        "max",
        "distinct_count"
      ]
    },
    {
      "field": "bankingCoreUser.identificationNumber",
      "stats": [
        "min",
        "max",
        "distinct_count"
      ]
    },
    {
      "field": "bankingCoreUser.lastName",
      "stats": [
        "min",
        "max",
        "distinct_count"
      ]
    },
    {
      "field": "bankingCoreTransaction.transactionType",
      "stats": [
        "min",
        "max",
        "distinct_count"
      ]
    },
    {
      "field": "bankingCoreTransaction.referenceNumber",
      "stats": [
        "min",
        "max",
        "distinct_count"
      ]
    },
    {
      "field": "bankingCoreTransaction.transactionId",
      "stats": [
        "min",
        "max",
        "distinct_count"
      ]
    },
    {
      "field": "bankingCoreUtilityAccount.number",
      "stats": [
        "min",
        "max",
        "distinct_count"
      ]
    },
    {
      "field": "bankingCoreUtilityAccount.providerName",
      "stats": [
        "min",
        "max",
        "distinct_count"
      ]
    }
  ],
  "fields_fully_deferred": 12
}
```

## Tier 3 coverage
```json
{
  "bankingCoreAccount": {
    "mode": "full_diff",
    "population": 14,
    "duplicate_source_key_count": 0
  },
  "bankingCoreUser": {
    "mode": "full_diff",
    "population": 4,
    "duplicate_source_key_count": 0
  },
  "bankingCoreTransaction": {
    "mode": "full_diff",
    "population": 0,
    "duplicate_source_key_count": 0
  },
  "bankingCoreUtilityAccount": {
    "mode": "full_diff",
    "population": 6,
    "duplicate_source_key_count": 0
  }
}
```
