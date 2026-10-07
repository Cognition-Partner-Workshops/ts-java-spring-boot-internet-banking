#!/usr/bin/env python3
"""Supplement to data_profile.py for stats its census-driven planner does not emit.

data_profile.py plans row_bytes only for tables that take part in a census relationship
and value_domain only for columns the census flagged as a trap (yn_flag/int_flag/
code_lookup). On this census that leaves banking_core_utility_account without row bytes
and the status / type / transaction_type code columns without a value domain. This
script renders the SAME profile templates (mysql.md `profiling_queries`) through
data_profile.py's own helpers and runs them over one read-only connection, so the
output has the same shape and sha256-pinned SQL as data_profile.json.

    python3 data_profile_supplement.py --plugin <plugin> --census census.json \
        --source-dsn-secret MMP_RT_SRC_DSN --out data_profile.supplement.json \
        --row-bytes banking_core_utility_account \
        --value-domain banking_core_account.status banking_core_account.type \
        --value-domain banking_core_transaction.transaction_type
"""
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import sys


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--plugin", required=True, type=pathlib.Path)
    ap.add_argument("--census", required=True, type=pathlib.Path)
    ap.add_argument("--family", default="mysql")
    ap.add_argument("--source-dsn-secret", required=True)
    ap.add_argument("--out", required=True, type=pathlib.Path)
    ap.add_argument("--row-bytes", nargs="*", default=[], metavar="TABLE")
    ap.add_argument("--value-domain", nargs="*", action="extend", default=[],
                    metavar="TABLE.COLUMN")
    ap.add_argument("--statement-timeout", type=int, default=300)
    args = ap.parse_args()

    sys.path.insert(0, str(args.plugin / "skills" / "schema-modeling"))
    import data_profile as dp  # noqa: E402
    from catalog_census import parse_profile_queries  # noqa: E402

    census = json.loads(args.census.read_text(encoding="utf-8"))
    censusSha = hashlib.sha256(args.census.read_bytes()).hexdigest()
    templates = parse_profile_queries(
        args.plugin / "skills" / "mongo-migration" / "profiles" / f"{args.family}.md",
        section="profiling_queries")
    tables = census["tables"]

    stats: list[dict] = []
    for tableName in args.row_bytes:
        table = tables[tableName]
        bytesExpr, excludedColumns = dp._row_bytes_expr(args.family, table, "t")
        fromClause, samplePct, skipReason = dp._from_clause(args.family, tableName, table, "t", 10_000_000, False)
        sql = templates["row_bytes"].replace("{row_bytes}", bytesExpr).replace("{table_from}", fromClause)
        stats.append({"id": f"row_bytes:{tableName}", "query_id": "row_bytes",
                      "sql": None if skipReason else sql, "subject": {"table": tableName},
                      "sampled": samplePct is not None, "sample_pct": samplePct, "skipped_reason": skipReason,
                      **({"excluded_columns": excludedColumns} if excludedColumns else {})})
    for spec in args.value_domain:
        tableName, columnName = spec.rsplit(".", 1)
        table = tables[tableName]
        if columnName not in {c["name"] for c in table["columns"]}:
            print(f"supplement refused: {spec} is not a census column", file=sys.stderr)
            return 2
        fromClause, samplePct, skipReason = dp._from_clause(args.family, tableName, table, "t", 10_000_000, False)
        sql = (templates["value_domain"].replace("{col}", dp._quote(args.family, columnName))
               .replace("{table_from}", fromClause))
        stats.append({"id": f"value_domain:{tableName}.{columnName}", "query_id": "value_domain",
                      "sql": None if skipReason else sql,
                      "subject": {"table": tableName, "column": columnName},
                      "sampled": samplePct is not None, "sample_pct": samplePct, "skipped_reason": skipReason})

    results = dp.run_live(args.family, args.source_dsn_secret, stats, args.statement_timeout)
    bundle = dp.build_profile(census, censusSha, args.family, stats, results, "live")
    bundle["supplement_of"] = "data_profile.json"
    bundle["why"] = ("data_profile.py emits row_bytes only for relationship tables and "
                     "value_domain only for census trap columns; these stats were planned "
                     "by hand from the same census and rendered with the same templates")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(bundle, indent=2) + "\n")
    statusCounts: dict[str, int] = {}
    for s in bundle["stats"]:
        statusCounts[s["status"]] = statusCounts.get(s["status"], 0) + 1
    print(f"supplement written to {args.out}: stats={len(bundle['stats'])} "
          + " ".join(f"{k}={v}" for k, v in sorted(statusCounts.items())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
