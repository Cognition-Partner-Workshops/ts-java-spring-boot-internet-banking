#!/usr/bin/env python3
"""Load the four core-banking tables from MySQL into MongoDB per .migration/mapping_spec.json.

Batch w1-b01 (board UNT5). Secrets by environment-variable NAME only:
  MMP_RT_SRC_DSN        JSON {"user","password","host","port","database"} of the read-only MySQL principal
  MMP_RT_B1_TARGET_URI  Atlas URI of the principal scoped readWrite@mmp_rt_b1_mysql

Rules enforced here: source session is READ ONLY with time_zone='+00:00' and one connection
(source concurrency 1); the only writable database is the one in .migration/allowed_targets.json
and the only collections are the four camelCase targets of the batch; no drop ever runs — the
load is an idempotent replaceOne(upsert) keyed by the legacy numeric id; every index in the spec
is created BEFORE the rows so a duplicate on a unique key fails the load visibly; the three lookup
indexes carry collation {locale: en, strength: 2} (ledger s3.2 entry 3). Nothing secret is printed.

Usage (run from $HOME with absolute paths):
  recon venv python load_core_banking_mongo.py --spec <abs>/.migration/mapping_spec.json \
      --allowed-targets <abs>/.migration/allowed_targets.json --target-db mmp_rt_b1_mysql [--verify-only]
"""
from __future__ import annotations

import argparse
import decimal
import json
import os
import sys

import pymysql
from bson.decimal128 import Decimal128
from bson.int64 import Int64
from pymongo import ASCENDING, MongoClient
from pymongo.collation import Collation
from pymongo.errors import DuplicateKeyError

WRITE_TARGETS = ("bankingCoreUser", "bankingCoreAccount", "bankingCoreUtilityAccount", "bankingCoreTransaction")
# d-collation (ledger s3.2 entry 3): exactly these three fields, nothing else
COLLATED_FIELDS = {("bankingCoreAccount", "number"), ("bankingCoreUser", "identificationNumber"),
                   ("bankingCoreUtilityAccount", "providerName")}
CASE_INSENSITIVE = Collation(locale="en", strength=2)


def _secret(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        sys.exit(f"secret {name} not found in environment (pass secrets by name only)")
    return value


def to_bson(value, bson_type: str):
    if value is None:
        return None
    if bson_type == "decimal":
        return Decimal128(value if isinstance(value, decimal.Decimal) else decimal.Decimal(str(value)))
    if bson_type == "long":
        return Int64(value)
    if bson_type == "int":
        return int(value)
    if bson_type == "string":
        return str(value)
    return value


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--spec", required=True)
    ap.add_argument("--allowed-targets", required=True)
    ap.add_argument("--target-db", required=True)
    ap.add_argument("--verify-only", action="store_true", help="compare only; write nothing")
    args = ap.parse_args()

    allowed = json.load(open(args.allowed_targets))["databases"]
    if args.target_db not in allowed:
        sys.exit(f"refused: --target-db {args.target_db} not in allowed_targets {allowed}")
    spec = json.load(open(args.spec))
    collections = [c for c in spec["collections"] if c["collection"] in WRITE_TARGETS]
    skipped = [c["collection"] for c in spec["collections"] if c["collection"] not in WRITE_TARGETS]
    print(f"spec {spec['version']}: loading {[c['collection'] for c in collections]}, skipping {skipped}")

    dsn = json.loads(_secret("MMP_RT_SRC_DSN"))
    src = pymysql.connect(user=dsn["user"], password=dsn["password"], host=dsn["host"],
                          port=int(dsn.get("port", 3306)), database=dsn["database"], autocommit=True,
                          init_command="SET SESSION TRANSACTION READ ONLY",
                          cursorclass=pymysql.cursors.DictCursor)
    cur = src.cursor()
    cur.execute("SET time_zone = '+00:00'")
    cur.execute("SELECT @@session.time_zone AS tz, @@session.transaction_read_only AS ro")
    print("source session", cur.fetchone())

    client = MongoClient(_secret("MMP_RT_B1_TARGET_URI"), serverSelectionTimeoutMS=20000)
    db = client[args.target_db]
    roles = db.command("connectionStatus")["authInfo"]["authenticatedUserRoles"]
    print("target principal roles", [(r["role"], r["db"]) for r in roles])
    before = db.command("dbStats", scale=1)
    print("dbStats before", {k: before.get(k) for k in ("collections", "objects", "dataSize", "storageSize", "indexSize")})

    exit_code = 0
    for c in collections:
        name, table = c["collection"], c["root_table"]
        key_src = c["key"]["source"]
        assert key_src == ["id"] and c["key"]["target"] == "_id", f"{name}: unexpected key {c['key']}"
        fields = c["fields"]
        coll = db[name]

        if not args.verify_only:
            for ix in c["indexes"]:
                keys = [(k, ASCENDING if d == 1 else -d) for k, d in ix["keys"]]
                opts = {"unique": True} if ix.get("unique") else {}
                if len(keys) == 1 and (name, keys[0][0]) in COLLATED_FIELDS:
                    opts["collation"] = CASE_INSENSITIVE
                created = coll.create_index(keys, **opts)
                print(f"{name}: index {created} unique={bool(ix.get('unique'))} collated={'collation' in opts}")

        cols = ", ".join(f"`{k}`" for k in key_src) + "".join(f", `{f['source']}`" for f in fields)
        cur.execute(f"SELECT {cols} FROM `{table}` ORDER BY `id`")
        rows = cur.fetchall()
        upserted = matched = 0
        if not args.verify_only:
            for row in rows:
                doc = {"_id": Int64(row["id"])}
                for f in fields:
                    v = to_bson(row[f["source"]], f["bson_type"])
                    if v is not None:
                        doc[f["target"]] = v
                try:
                    r = coll.replace_one({"_id": doc["_id"]}, doc, upsert=True)
                except DuplicateKeyError as exc:
                    print(f"FAIL {name}: duplicate on a unique key for source id {row['id']}: {exc.details.get('errmsg')}")
                    exit_code = 1
                    continue
                upserted += 1 if r.upserted_id is not None else 0
                matched += r.matched_count
        target_count = coll.count_documents({})
        status = "ok" if target_count == len(rows) else "COUNT MISMATCH"
        if status != "ok":
            exit_code = 1
        print(f"{name}: source rows {len(rows)} -> target docs {target_count} (upserted {upserted}, replaced {matched}) {status}")

        # hand-applied recon rule (not expressible in the pinned spec): casefold parity on exactly
        # the three collated fields, exact parity on every other field
        for f in fields:
            fold = (name, f["target"]) in COLLATED_FIELDS
            src_map = {row["id"]: row[f["source"]] for row in rows}
            mism = 0
            for d in coll.find({}, {f["target"]: 1}):
                s, t = src_map.get(d["_id"]), d.get(f["target"])
                if isinstance(t, Decimal128):
                    t = t.to_decimal()
                if fold and isinstance(s, str) and isinstance(t, str):
                    s, t = s.casefold(), t.casefold()
                if s != t:
                    mism += 1
            if mism:
                exit_code = 1
            print(f"  {name}.{f['target']}: {'casefold' if fold else 'exact'} parity mismatches={mism}")

    after = db.command("dbStats", scale=1)
    print("dbStats after", {k: after.get(k) for k in ("collections", "objects", "dataSize", "storageSize", "indexSize")})
    total = (after.get("storageSize") or 0) + (after.get("indexSize") or 0)
    print(f"storage+index bytes {total} ({total / 1048576:.3f} MB; limit 10 MB) ->", "ok" if total < 10 * 1048576 else "OVER LIMIT")
    print("collections now", sorted(db.list_collection_names()))
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
