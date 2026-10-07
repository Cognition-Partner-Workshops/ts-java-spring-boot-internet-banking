run_id: UNT5-15
manifest_sha: f672bf2f9220

# Wave 1 independent verification — core-banking (MySQL 8 → MongoDB Atlas)

**Wave verdict: PASS.** One batch, `w1-b01` (unit `core-banking`): **PASS**. Zero findings.

**The PASS below comes from a PATCHED recon harness.** The plugin as cloned (mongo-migration 0.7.0, commit `caeb34d`) crashes
in `MongoTargetAdapter.index_keys` on any target with a secondary index; I reproduced that crash first with the unpatched
harness, then applied the same two-line fix as PR #39 / ledger s4.1.b01 entry 3 **in my plugin clone only** (nothing in the
migration repo was changed) and re-ran. The diff I applied is committed beside this report as `harness-index_keys.patch`.

Verifier: independent session UNT5-15; I wrote none of PR #39 and did not merge anything. Batch under test: PR
https://github.com/Cognition-Partner-Workshops/ts-java-spring-boot-internet-banking/pull/39, branch
`devin/1791339936-unt5-14-migrate-core-banking` at `0f11592`, base `origin/mmp-rt/b1-mysql` at `f48fb2f` — verified from the branch,
not merged.

## 1. Inputs and pins (verified against committed bytes, not pasted values)

| Item | Value | How verified |
|---|---|---|
| mapping `map-draft-3` | sha256 `669b4e98932d00d893b249ba66fa88c9c44e89831b50d7370d85ff681a2a0c38` | `git -C <repo> show origin/mmp-rt/b1-mysql:.migration/mapping_spec.json \| sha256sum` — matches the ticket |
| tolerances `tol-1` | sha256 `8ea506ef76b192076c5f023d25fe3591f384a40d3e044e7743679ba5d11e690d` | same, `.migration/recon_tolerances.json` — matches the ticket |
| allowlist | sha256 `2717426d9ae09f5429e03b953787f780157624bcaedbbdbe69690950176717e6`; databases `["mmp_rt_b1_mysql"]`, mode `block` | `origin/mmp-rt/b1-mysql:.migration/allowed_targets.json` |
| ops (casefold rules) | `.migration/recon/core-banking/ops.json` on the PR branch: exactly 3 `collation_casefold` fields — `bankingCoreAccount.number`, `bankingCoreUser.identificationNumber`, `bankingCoreUtilityAccount.providerName` | read from `0f11592`, passed via `--ops` |
| canonicalization | the 10 `recon_canonicalization` rules of `skills/mongo-migration/profiles/mysql.md` | extracted to JSON, `recon selftest PASS: 9 canonicalization rules exercised` |
| source | MySQL `8.4.11` (`mysql:8`), db `banking_core_service`, user `fixture_ro`, `@@global.time_zone = @@session.time_zone = +00:00` | rebuilt this session, see §2 |
| target | Atlas, database `mmp_rt_b1_mysql`, target class `migration_cluster`, principal `mmp_rt_b1_mysql_rw` | `connectionStatus` → roles `["readWrite@mmp_rt_b1_mysql"]` only |

The recon result files record the same two sha256 values (`mapping_sha256`, `tolerance_sha256` in `w1-b01/core-banking/result.json`).

## 2. Source fixture (rebuilt, read-only)

Rebuilt per `.migration/fixtures/w1-b01.json` with `skills/schema-modeling/docker-compose.local.yml` (container
`schema-modeling-mysql-1`, Flyway DDL + `*__temp_data.sql` seed from `core-banking-service/src/main/resources/db/migration`).
Counts via `fixture_ro` before any recon: `banking_core_user=4`, `banking_core_account=14`, `banking_core_utility_account=6`,
`banking_core_transaction=0` — equal to the fixture manifest. Read-only proven: `CREATE TABLE` as `fixture_ro` →
`ERROR 1142 (42000): CREATE command denied`. DSN exported as `MMP_RT_SRC_DSN` (local env, not committed). Source query
concurrency 1 (`--source-concurrency 1`).

## 3. Target principal and Atlas read-only posture

The shared `MONGODB_ATLAS_URI` was not used for recon (it is `readWriteAnyDatabase`; the harness refuses it). The existing Atlas
DB user `mmp_rt_b1_mysql_rw` had its password rotated for this session via Atlas Admin API v2
`PATCH /api/atlas/v2/groups/{projectId}/databaseUsers/admin/mmp_rt_b1_mysql_rw` (HTTP 200; roles unchanged:
`readWrite@mmp_rt_b1_mysql`; no user created). The session URI was exported as `MMP_RT_B1_TARGET_URI` from a 0600 file outside the
repo; no secret value appears in any artifact here.

Nothing was written to Atlas by this verification: the harness only reads; the probes are `find`/`aggregate`/`explain`/
`dbStats`/`listIndexes`; the app replay used GET endpoints only (no POST transfer/payment). `dbStats` before the first probe and
after the last app request are identical: 5 collections, `objects=24`, `dataSize=3255`, `indexSize=290816` (0.43 MB total,
under the 10 MB cap). `_connectivity_probe` (0 docs) pre-existed and is not mine. No database other than `mmp_rt_b1_mysql` was opened.

## 4. Recon — unpatched harness first (independent reproduction of the known defect)

Command (identical for both runs; run from `$HOME` through a 4-line wrapper that `os.chdir`s into a scratch dir holding only a
copy of the committed `.migration/allowed_targets.json`, because the harness resolves the allowlist from its process cwd and the
org-wide dbx guard rejects any shell whose cwd is inside this repo):

```
recon run --unit core-banking --family mysql \
  --mapping <committed mapping_spec.json> --tolerances <committed recon_tolerances.json> \
  --canonicalization <mysql profile rules.json> --mode live \
  --source-dsn-secret MMP_RT_SRC_DSN --target-uri-secret MMP_RT_B1_TARGET_URI \
  --target-db mmp_rt_b1_mysql --target-class migration_cluster \
  --ops <PR-branch .migration/recon/core-banking/ops.json> \
  --collections bankingCoreAccount,bankingCoreUser,bankingCoreTransaction,bankingCoreUtilityAccount \
  --source-concurrency 1 --seed 1 --out <dir>
```

Run 1, plugin `caeb34d` unmodified → exit 1:

```
  File ".../harness/recon/engine.py", line 95, in run_recon
    n_checks, idx_findings = index_findings(spec, target)
  File ".../harness/recon/tiers.py", line 116, in index_findings
    have = {(tuple(ix.keys), ix.unique) for ix in target.index_keys(c.collection)}
  File ".../harness/recon/adapters.py", line 287, in index_keys
    if not all(v in (1, -1) for v in key_items.values()):
AttributeError: 'list' object has no attribute 'values'
```

The harness still wrote a result: `w1-b01/core-banking/unpatched/result.json` — `verdict: ERROR`, `merge_eligible: false`, Tier 1
(4 checks) passed before the crash, `error.type AttributeError`, `error.message 'list' object has no attribute 'values'`. This
matches ledger s4.1.b01 entry 3 exactly (same file, same line, same message).

## 5. Recon — patched harness (the gate evidence)

Patch applied to the plugin clone only (`harness-index_keys.patch`, 2 changed lines in `adapters.py` `index_keys`: iterate the
`key_items` list as `for _, v in key_items` / `for k, v in key_items` instead of calling `.values()` / `.items()` on it). The
plugin repo is otherwise at `caeb34d`; the migration repo received no code change.

Run 2 → exit 0: `recon PASS: unit=core-banking mode=live target_class=migration_cluster mapping=map-draft-3 tolerances=tol-1
merge_eligible=True`. Result: `w1-b01/core-banking/result.json` (+ `report.md`, `recon.summary.md`):

| Tier | Checks | Result |
|---|---|---|
| 1 counts_through_mapping | 9 | PASS |
| 2 per_field_aggregates | 5 | PASS |
| 3 keyed_diffs | 24 | PASS |
| 4 app_level_parity (incl. index checks and the 3 casefold ops) | 7 | PASS |

`verdict PASS`, `merge_eligible true`, `mode live`, `target_class migration_cluster`, 0 findings in every tier, `warnings []`,
`redaction_salted true`. Tier/check counts are identical to the batch worker's committed result
(`.migration/recon/core-banking/result.json` on the PR branch: 9/5/24/7), which I did not reuse.

Note on redaction: `RECON_REDACT_SALT` was session-local to the batch worker and is session-local here, so salted finding ids would
not match between the two results — irrelevant at 0 findings on both sides.

## 6. Probes past the gate (`w1-b01/core-banking/probes.json`)

All probes read-only, via the scoped principal, on `mmp_rt_b1_mysql`.

| Probe | Result |
|---|---|
| Collections present | `bankingCoreAccount` 14, `bankingCoreUser` 4, `bankingCoreUtilityAccount` 6, `bankingCoreTransaction` 0 (plus pre-existing `_connectivity_probe` 0) — equal to source |
| Null / missing per field | 0 null and 0 missing on every mapped field of every document (user: `_id,email,firstName,identificationNumber,lastName`; account: `_id,number,type,status,actualBalance,availableBalance,userId`; utility: `_id,number,providerName`); no unexpected extra fields; no `_class` discriminator |
| Types | all `_id` and `userId` are Int64 (legacy numeric ids kept); strings are strings; both balances are Decimal128 on all 14 accounts |
| Duplicate `_id` | 0 in all four collections (`$group`/`$match n>1`); also 0 duplicate `account.number`, `user.identificationNumber`, `utilityAccount.providerName` |
| Decimal128 scale | `$type: decimal` holds for 14/14 on `actualBalance` and `availableBalance`; scale histogram `{2: 14}` for both — matches source `decimal(19,2)` |
| Indexes | account: `number_1` unique + collation `en`/strength 2, `userId_1`; user: `identificationNumber_1` unique + collation `en`/2; utility: `providerName_1` + collation `en`/2; transaction: `accountId_1` present |
| Collation `findByIdentificationNumber` | swapcase probe (`808829932v`): plain equality 0 hits, with `{locale:en,strength:2}` 1 hit, `IXSCAN` on the collated index — matches MySQL `utf8mb4_0900_ai_ci` |
| Collation `findByProviderName` | swapcase probe (`vodafone`): plain 0, collated 1, `IXSCAN` — same |
| Collation `findByNumber` | **vacuous**: every `number` is all digits, swapcase == original, so case-insensitivity cannot be exercised (plain 1 / collated 1, `IXSCAN`) |
| `bankingCoreTransaction` | exists, 0 documents, `accountId_1` index present; graded on 0 rows (nothing to compare — stated as such) |
| References | 14/14 `bankingCoreAccount.userId` resolve to a `bankingCoreUser._id`; 0 dangling; 0 users without accounts; per-user `{1:2, 2:3, 3:5, 4:4}` equals source `GROUP BY user_id` |
| Utility accounts | 0 of 6 carry a `userId` (none in source either) |
| Domains / boundaries | `type ∈ {SAVINGS_ACCOUNT}`, `status ∈ {ACTIVE}`; `_id` ranges 1–14 / 1–4 / 1–6 contiguous as in source |

## 7. App-level replay (`w1-b01/core-banking/app-replay.json`) — first end-to-end run of the service against Atlas

- `main` (`957765d`) `core-banking-service` bootJar, JDK 21, against the MySQL fixture on `:8081`: config server and Eureka
  disabled by property overrides, `spring.flyway.enabled=false` (the read-only `fixture_ro` cannot write the schema-history
  table), `ddl-auto=none`, JDBC URL built from `MMP_RT_SRC_DSN` with UTC time zone.
- PR #39 branch (`0f11592`) bootJar, JDK 21, against Atlas on `:8082` with `MMP_RT_B1_TARGET_URI` (the module-local
  `application.yml` override from PR #39's README; config server and Eureka disabled). Started cleanly in 2.8 s; its startup
  index creation found every index already present (index set unchanged, see §3).
- 43 GET requests replayed against both and compared on status code + parsed JSON body: all 14 `GET /api/v1/account/bank-account/{number}`,
  all 4 `GET /api/v1/user/{identification}` (each embedding the user's bankAccounts), all 6 `GET /api/v1/account/util-account/{name}`,
  mixed-case probes (4 lower-case identifications, 6 capitalised + 6 lower-case provider names — MySQL `*_ai_ci` and the Mongo
  `en`/strength-2 collation both resolve them), and 3 not-found probes (both return 400 `BANKING-CORE-SERVICE-1000`
  "Requested entity not present in the DB."). **43/43 identical, 0 mismatches.**
- Not exercised: `POST /api/v1/transaction/*` (fund transfer / utility payment) — they write to Atlas and are out of scope for a
  verifier; `GET /api/v1/user` (paged list) is not in the ticket.

## 8. Tool defects, blind spots and substitutions (ledger `.migration/05_decisions.md`, entries `s4.1.verify` 1–5)

1. `MongoTargetAdapter.index_keys` crash — reproduced unpatched, fixed in the plugin clone only (§4–5).
2. Harness refuses the spec as committed without `--collections`: `recon refused: mmpFixtureMeta: no comparison key` — the
   mapping spec carries the `mmpFixtureMeta` bookkeeping collection with no key, so every run must pass the four write-target
   collections explicitly; the PASS covers exactly those four.
3. dbx-migration-factory guard vs. harness cwd: wrapper + scratch allowlist copy (§4); `cd <worktree> && ./gradlew` was blocked,
   `gradlew -p <abs path>` from `$HOME` works; `generateGitProperties` fails in a git worktree (`RepositoryNotFoundException`), built
   with `-x generateGitProperties`. Manager finding recorded: `preflight.py --grade/--verify` shells bare `git` in the process
   cwd, which the guard forbids inside the repo — grade with `GIT_DIR`/`GIT_WORK_TREE`.
4. App replay substitutions: no config server in this session (property overrides instead); Flyway disabled on `main` because the
   fixture user is read-only. My first `main` run wrongly added `spring.jpa.open-in-view=false`, which made `GET /api/v1/user/*`
   fail with `LazyInitializationException` on the MySQL side — a verifier configuration error, not a defect in either branch;
   re-run with Spring Boot defaults gave 43/43.
5. Blind spots stated: `findByNumber` casefold is vacuous on digit-only data; `bankingCoreTransaction` graded on 0 rows; salted
   finding ids not comparable across sessions (0 findings, so moot); the external config-server repo change is still a cutover
   prerequisite (ledger s3.3) and is unverified here.

## 9. Verdict

- `w1-b01` / `core-banking`: **PASS** — live recon PASS at the pinned mapping/tolerance bytes on `migration_cluster` (patched
  harness, §5), every probe past the gate clean (§6), 43/43 app-level parity (§7). No findings.
- Wave 1: **PASS**. Not a merge: the manager grades with `preflight.py --verify` and merges PR #39.

Verify result JSON (also at `.migration/recon/wave-1/verify-result.json`):

```json
{"wave_verdict": "PASS",
 "unit_verdicts": {"w1-b01": "PASS"},
 "recon_results": {"w1-b01": [".migration/recon/wave-1/w1-b01/core-banking/result.json"]},
 "findings": [],
 "report_path": "recon/wave-1:.migration/recon/wave-1/verify-report.md"}
```

## 10. Self-check with `preflight.py --grade --verify` (verifier's own run; the manager's run is authoritative)

The wave spec was reconstructed from the ticket block (1 batch `w1-b01`, unit `core-banking`, the four write targets, `map-draft-3`
/ `tol-1` with the two pinned sha256s, `fixture_manifest .migration/fixtures/w1-b01.json`, `auto_merge false`, `source_access live`,
`target_access migration_cluster`) and hashes to `manifest_sha f672bf2f9220` — identical to the ticket, so the headers of this
report are checked against the real manifest. Run from `$HOME` with `GIT_DIR`/`GIT_WORK_TREE` pointing at the repo (ledger
s4.1.verify entry 3) after pushing `origin/recon/wave-1` at `5cd2ff0`:

```
python3 <plugin>/skills/wave-preflight/preflight.py --wave <wave-1.json> --root <abs repo> \
  --grade <PR-branch .migration/recon/core-banking/batch_result.json> \
  --verify .migration/recon/wave-1/verify-result.json --run-id UNT5-15
→ "verify_problems": [], "mergeable_prs": ["https://github.com/Cognition-Partner-Workshops/ts-java-spring-boot-internet-banking/pull/39"]
  wave 1 OK: 1 batches, manifest_sha f672bf2f9220   (exit 0)
```

Full transcript: `preflight-verify-transcript.txt` beside this report.
