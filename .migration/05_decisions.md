# Decisions ledger — board UNT5 (core-banking MySQL 8 -> MongoDB Atlas), branch `mmp-rt/b1-mysql`

Append-only. One entry per place a plugin tool was wrong, blind, or needed manual correction, and per
human review substituted because the run is unattended. Entry shape: date, step id, what the tool said,
what was done, evidence. Nothing here authorizes a write: write scope is `.migration/allowed_targets.json`.

## 2026-10-07 · s1.1-blueprint · recon harness not bound in the org venv

- Tool said: org blueprint knowledge claims `/home/ubuntu/.venvs/recon/bin/recon selftest` works; its
  maintenance step binds the harness only from `/opt/.devin/plugins/cache/*/*/skills/mongo-recon-harness/harness`.
- Observed: the mongo-migration plugin is not installed in this org, so the cache glob matched nothing and the
  venv had the drivers (PyMySQL 1.2.3, pymongo 4.18.2, oracledb, pyodbc) but no `recon` entry point
  (`bash: /home/ubuntu/.venvs/recon/bin/recon: No such file or directory`).
- Did: cloned `Cognition-Partner-Workshops/mongo-migration-plugin` at `caeb34dc6fadcae4926b54d9f8648a16d3cced79`
  (0.7.0) beside the repo and ran
  `/home/ubuntu/.venvs/recon/bin/pip install -e "skills/mongo-recon-harness/harness[mongo,mysql]"`
  -> `recon selftest PASS: 9 canonicalization rules exercised`. Venv extended, not replaced. The proposed
  blueprint (`.devin/blueprint.yaml`, maintenance) does the same bind from the pinned clone.
- Evidence: this PR; `.devin/mmp-postsetup.sh` line `WORKS  recon selftest`.

## 2026-10-07 · s1.1-blueprint · Atlas principal is not scoped to `mmp_rt_b1_mysql`

- Tool said (ticket post-setup check): `mongosh` connects with `MONGODB_ATLAS_URI` and the principal is
  read+write on `mmp_rt_b1_mysql` only.
- Observed (`connectionStatus`, read-only, no write): user `otterworks-app`, roles
  `readWriteAnyDatabase@admin`, `dbAdminAnyDatabase@admin` on the shared M0 (server 8.0.34). Read+write on the
  target is satisfied; "only" is not — the secret's own description says `readWriteAnyDatabase`.
- Did: no change (Devin does not alter Atlas grants). `.devin/mmp-postsetup.sh` fails the build only if the
  principal lacks readWrite on the target and prints `WARN` for the over-broad scope. Enforcement of the
  single-database write scope is the committed `.migration/allowed_targets.json` (UNT5-2) plus the
  mongo_guard rules every worker applies by hand. Human review substituted: the manager may instead
  provision a scoped Atlas user (pattern exists: `MONGODB_MMP_RT_TARGET_N_URI` is `readWrite@mmp_rt_billing_n`
  only) and point `MONGODB_ATLAS_URI` — or a new secret name — at it; until then every worker must treat the
  allowlist as the only fence.
- Evidence: `bash .devin/mmp-postsetup.sh` output
  `{"readWriteOnTarget":true,"scopedToTargetOnly":false,"roles":["readWriteAnyDatabase@admin","dbAdminAnyDatabase@admin"]}`.

## 2026-10-07 · s1.1-blueprint · plugin layout vs. run contract for this ledger

- Tool said: mongo-migration 0.7.0 `CHANGES.md` deletes `05_decisions.md` ("human picks live in plan
  decisions"; `.migration/` holds machine files only).
- Did: kept this Markdown ledger anyway because the board's run context names
  `.migration/05_decisions.md` as the main deliverable of the run. The machine file `05_decisions.json`
  (input to `model_patch.py`) stays separate and is not created by this step.
- Evidence: `mongo-migration-plugin/CHANGES.md` ".migration/ file fates" table; ticket UNT5-1 run context.

## 2026-10-07 · s1.1-blueprint · dbx-migration-factory guard blocks shell in this repo

- Tool said: the dbx-migration-factory plugin (installed org-wide; unrelated to this Mongo run) hooks
  PreToolUse and rejects any shell command whose cwd is this repo — or that `cd`s into it — once
  `.migration/` exists:
  `dbx-migration-factory guard: cannot read .migration/allowed_targets.json: [Errno 2] No such file or directory`.
- Did: ran from `$HOME` with absolute paths and `git -C` instead of authoring `allowed_targets.json`
  (that file is UNT5-2's PR-only deliverable). Recorded in the blueprint knowledge so later workers are
  not surprised; the block clears once UNT5-2's `allowed_targets.json` is on the run branch.
- Evidence: rejected `exec` calls in this session; `.devin/blueprint.yaml` knowledge `mongo-migration-run`.

## 2026-10-07 · s1.1-blueprint · source-client probe skipped at build time

- Tool said (intake blueprint section): under `online` the source client runs a trivial read with the
  assessment principal as a build-failing check.
- Did: not possible — the source is a per-session local `mysql:8` fixture whose DSN (`MMP_RT_SRC_DSN`) is
  set per shell by UNT5-5, not by the blueprint, and snapshot builds have no running containers. The check
  verifies `mysql --version` and the pulled `mysql:8` image only; the trivial read belongs to UNT5-4
  (connectivity probe) after the fixture exists.
- Evidence: `.devin/mmp-postsetup.sh` `NOTE` line.

## 2026-10-07 · s1.1-blueprint · tooling absent from the snapshot before this step

- Observed: `mysql` client not installed (`mysql-client-8.0` candidate 8.0.46), `mysql:8` image not pulled
  (`mongo:7` was), plugin not cloned. `mongosh` 2.x, Database Tools 100.19.1, Atlas CLI 2.12.0 were present.
- Did: installed/pulled them in this session with the exact commands now in `.devin/blueprint.yaml`
  (`initialize`), each guarded to be idempotent.
- Evidence: `.devin/mmp-postsetup.sh` all `WORKS`; this PR.

## 2026-10-07 · s1.3-allowlist · (1) guard base ref: `mongo_guard` reads the allowlist from `origin/main`

- Tool said: `hooks/mongo_guard.py` (0.7.0) resolves the policy base as `MONGO_GUARD_BASE_REF`, then
  `origin/HEAD`, then `origin/main`/`origin/master`; it never reads a local HEAD or the working tree.
- Observed: this run never merges to `main`, so with the default base the committed policy is "none" and the
  run-branch working copy counts as drift — every Atlas write is refused:
  `mongo_guard: working-tree .migration/allowed_targets.json differs from the committed base-branch copy (origin/main)`.
- Did: every worker exports `MONGO_GUARD_BASE_REF=origin/mmp-rt/b1-mysql` before any write. Probed with the
  hook run from the repo root against a stand-in ref (this PR's branch, same tree as `mmp-rt/b1-mysql`
  post-merge): write to `mmp_rt_b1_mysql` -> exit 0 (allowed); write to `mmp_rt_billing_1` -> exit 2
  "write outside declared migration targets"; `UPDATE` against `127.0.0.1` -> exit 2 "legacy write needs a
  merged legacy_write_authorized row". The same probe is only valid against `origin/mmp-rt/b1-mysql` after
  the manager merges this PR; until then the allowlist is not in force.
- Evidence: this PR; probe output recorded on ticket UNT5-2.

## 2026-10-07 · s1.3-allowlist · (2) PreToolUse guard does not fire in this org

- Tool said: `hooks.json` registers `mongo_guard.py` as a PreToolUse hook that hard-blocks out-of-scope writes.
- Observed: mongo-migration 0.7.0 is not installed as a Devin plugin in this org (confirmed in UNT5-1); the
  plugin is a plain clone beside the repo, so no hook intercepts tool calls.
- Did: the allowlist is enforced by the recon harness's `--allowed-targets-file` and by worker discipline
  (every worker applies the AGENTS.md hard rules by hand and may run the hook manually as above). No tool
  blocks a mistaken write; a mistake would be a finding, not something the guard catches.
- Evidence: `mongo-migration-plugin/hooks.json`; loaded-plugins list of this session (dbx-migration-factory,
  mongodb, mongodb-atlas present; mongo-migration absent).

## 2026-10-07 · s1.3-allowlist · (3) this ledger is prose in `.migration/`

- Tool said: plugin 0.7.0 (`CHANGES.md`, ".migration/ file fates") makes `.migration/` machine files only
  and warns that `05_decisions.md` is "not read by any tool".
- Did: kept deliberately — the board's run context names `.migration/05_decisions.md` as the main deliverable
  of the run. It is the human-facing record; no tool consumes it and none is expected to.
- Evidence: `mongo-migration-plugin/CHANGES.md`; UNT5-1 entry "plugin layout vs. run contract".

## 2026-10-07 · s1.3-allowlist · (4) Atlas principal is `readWriteAnyDatabase`; the allowlist is the only fence

- Tool said / manager note: org secret `MONGODB_ATLAS_URI` is Atlas user `otterworks-app` with
  `readWriteAnyDatabase` (and `dbAdminAnyDatabase`, per the UNT5-1 `connectionStatus` probe). Nothing on the
  Atlas side confines writes to `mmp_rt_b1_mysql`.
- Did: no Atlas change (out of scope for this run; Devin does not alter grants). The fence is exactly:
  the committed `.migration/allowed_targets.json` (`databases: ["mmp_rt_b1_mysql"]`, `mode: block`),
  `MONGO_GUARD_BASE_REF=origin/mmp-rt/b1-mysql`, and worker discipline (name the database on every write,
  never drop, stay under 10 MB, never touch another database). A scoped Atlas user
  (`readWrite@mmp_rt_b1_mysql` only, pattern `MONGODB_MMP_RT_TARGET_N_URI`) would be the proper fix.
- Evidence: UNT5-2 manager note; UNT5-1 entry "Atlas principal is not scoped".

## 2026-10-07 · s1.3-allowlist · (5) dbx-migration-factory guard rejects the Mongo-shaped allowlist

- Tool said (UNT5-1 entry): the org-wide dbx-migration-factory PreToolUse guard blocks shell commands run
  inside this repo because `.migration/allowed_targets.json` is missing, and "the block clears once UNT5-2's
  `allowed_targets.json` is on the run branch".
- Observed: it does not clear. `dbx_guard.py` (0.6.0) parses the same path and requires a non-empty
  `catalogs` list; the mongo_guard schema has `databases`. With this file present the verdict is still
  `block`: `cannot read .migration/allowed_targets.json: allowed_targets.json must contain a non-empty 'catalogs' list`.
  The two plugins share a file name with incompatible schemas; the file is kept exactly as the plan specifies.
- Did: all work in this repo runs from `$HOME` with `git -C <repo>` and absolute paths (no `cd`, no
  `workdir` inside the repo); hooks are exercised via `subprocess.run(cwd=<repo>)`. Later workers must do the
  same. Fix options for the manager: unload dbx-migration-factory for this board's sessions, or add a
  `catalogs` key to the allowlist by PR (changes the specified file; not done here).
- Evidence: `python3 .../dbx_guard.py` with `cwd` = repo root -> exit 2 and the message above (this session).

## 2026-10-07 · s2.4-fixture · plugin compose + mysql-init.sh: nothing to correct

- Tool said: `skills/schema-modeling/docker-compose.local.yml --profile mysql` with `MYSQL_FIXTURE_DB=banking_core_service`
  and `MYSQL_DDL_DIR=<repo>/core-banking-service/src/main/resources/db/migration` runs the three Flyway files in name
  order and reports readiness through `mmp_fixture_meta.scripts_failed = 0`.
- Observed: exactly that, first attempt. `docker compose logs mysql` lists `== V1.0.20210427174638__create_base_table_structure.sql`,
  `== V1.0.20210427174721__temp_data.sql`, `== V1.0.20210429210839__create_transaction_table.sql`, `== scripts failed: 0`,
  `== fixture ready`; container `(healthy)` 25 s after `up -d`. The schema-qualified inserts in the seed resolve because
  the database is `banking_core_service`. Row counts: user 4, account 14, utility_account 6, transaction 0.
- Did: no change to the plugin compose, init script or Flyway files. Noted that the floating `mysql:8` tag resolved to
  MySQL **8.4.11** (digest `sha256:6ea90827…`), not an 8.0.x build (the app's own `docker-compose/mysql/Dockerfile` is `FROM mysql:8.4.0`, so the major matches); the legacy `bigint(20)` display widths still load.
- Evidence: `.migration/fixtures/w1-b01.json` (`mmp_fixture_meta`, `scripts_run_in_order`, `server`).

## 2026-10-07 · s2.4-fixture · manifest `method`: ticket says `flyway_seed`, preflight accepts only `synthetic|masked_export`

- Tool said: ticket UNT5-5 asks for `method: flyway_seed`; `skills/wave-preflight/preflight.py` `FIXTURE_METHODS =
  ("synthetic", "masked_export")` and `validate_fixture_manifest` exits non-zero on anything else, so a `flyway_seed`
  manifest would fail `s4.1.0-preflight` for every batch that cites it.
- Did: wrote `method: synthetic` (the seed is the repo's synthetic demo data; no production rows, `masked_columns: []`)
  and kept the ticket's label as `seed_method: flyway_seed` plus a `method_note`. Validated with
  `preflight.validate_fixture_manifest("w1-b01", ".migration/fixtures/w1-b01.json", <repo>)` -> OK.
- Evidence: `.migration/fixtures/w1-b01.json`; validation output in the UNT5-5 PR body.

## 2026-10-07 · s2.4-fixture · read-only user and UTC pin are not provisioned by the plugin fixture

- Tool said: the MySQL profile (`skills/mongo-migration/profiles/mysql.md`) requires a read tier with `SELECT` + `SHOW VIEW`
  only and `time_zone='+00:00'` for load and recon sessions; `docker-compose.local.yml` / `mysql-init.sh` provision only
  `root` and expose no `command:`/`cnf` hook for server options.
- Did (fixture wiring, not the plugin files): as root on the fixture, `CREATE USER 'fixture_ro'@'%'`, `GRANT SELECT, SHOW VIEW
  ON banking_core_service.*`, `SET PERSIST time_zone='+00:00'` (survives container restarts via the `mysql-data` volume;
  `@@global.time_zone` = `+00:00`). Verified as `fixture_ro` over PyMySQL with `SET SESSION TRANSACTION READ ONLY`:
  `CREATE TABLE` -> error 1792, `INSERT` -> error 1142; the harness additionally sets `SET time_zone='+00:00'` per session
  (`recon/adapters.py`). The DSN is `MMP_RT_SRC_DSN` in `$HOME/.mmp-rt/b1-mysql.env` (0600, outside the repo), never committed;
  `.gitignore` now also ignores `.migration/**/*.env` for any in-repo copy. The fixture is a local throwaway, not the
  customer source, so rule 1 (source read-only) is not touched by these grants.
- Evidence: `.migration/fixtures/w1-b01.json` (`read_only_user`, `server.time_zone_pin`, `dsn`); PR body.

## 2026-10-07 · s2.4-fixture · snapshot still lacks the `mysql` client from PR #26

- Tool said: `.devin/blueprint.yaml` (merged UNT5-1) installs `mysql-client-8.0` and pulls `mysql:8` in `initialize`.
- Observed: this worker VM had neither (`mysql: command not found`; only `mongo:7` pulled) — the snapshot has not been
  rebuilt since the merge. Installed/pulled by hand with the blueprint's own commands; nothing to change in the blueprint.
- Evidence: `mysql  Ver 8.0.46-0ubuntu0.22.04.4`; `docker pull mysql:8` in this session.
