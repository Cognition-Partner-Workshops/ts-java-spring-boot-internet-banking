#!/usr/bin/env bash
# mongo-migration (online, MySQL source) post-setup checks for the mmp-rt/b1-mysql run.
# Run by the blueprint's maintenance step and by any worker that wants to re-verify its VM.
# Never prints a secret value: MONGODB_ATLAS_URI is passed to mongosh by name only.
set -euo pipefail

MMP_TARGET_DB="${MMP_TARGET_DB:-mmp_rt_b1_mysql}"
RECON="${RECON_VENV:-/home/ubuntu/.venvs/recon}/bin/recon"
PLUGIN_DIR="${MMP_PLUGIN_DIR:-/home/ubuntu/repos/mongo-migration-plugin}"
PLUGIN_COMMIT="${MMP_PLUGIN_COMMIT:-caeb34dc6fadcae4926b54d9f8648a16d3cced79}"
fail=0

check() { # name, command...
  local name="$1"; shift
  if "$@" >/dev/null 2>&1; then echo "WORKS  $name"; else echo "BLOCKED $name"; fail=1; fi
}

check "docker image mysql:8 present"  docker image inspect mysql:8
check "docker image mongo:7 present"  docker image inspect mongo:7
check "mysql client"                   mysql --version
check "mongosh"                        mongosh --version
check "mongodb-database-tools"         mongodump --version
check "atlas cli"                      bash -c 'PATH="$HOME/.local/bin:$PATH" atlas --version'
check "plugin pinned at $PLUGIN_COMMIT" bash -c "[ \"\$(git -C '$PLUGIN_DIR' rev-parse HEAD)\" = '$PLUGIN_COMMIT' ]"
check "recon venv has PyMySQL (mysql extra)" "${RECON_VENV:-/home/ubuntu/.venvs/recon}/bin/python" -c "import pymysql, pymongo"
check "recon selftest"                 "$RECON" selftest

if [ -z "${MONGODB_ATLAS_URI:-}" ]; then
  echo "BLOCKED mongosh connect: secret MONGODB_ATLAS_URI is not set in this environment"; fail=1
else
  # List the authenticated principal's roles; read-only command, no write.
  roles="$(mongosh "$MONGODB_ATLAS_URI" --quiet --eval '
    const s = db.runCommand({connectionStatus: 1});
    const want = "'"$MMP_TARGET_DB"'";
    const r = s.authInfo.authenticatedUserRoles;
    const rw = r.some(x => (x.role === "readWrite" || x.role === "dbOwner") && x.db === want)
            || r.some(x => x.role === "readWriteAnyDatabase" || x.role === "atlasAdmin" || x.role === "root");
    const scoped = r.every(x => x.db === want);
    print(JSON.stringify({readWriteOnTarget: rw, scopedToTargetOnly: scoped,
                          roles: r.map(x => x.role + "@" + x.db)}));' 2>/dev/null || true)"
  if [ -z "$roles" ]; then
    echo "BLOCKED mongosh connect with MONGODB_ATLAS_URI"; fail=1
  else
    echo "WORKS  mongosh connect with MONGODB_ATLAS_URI: $roles"
    case "$roles" in
      *'"readWriteOnTarget":true'*) ;;
      *) echo "BLOCKED principal lacks readWrite on $MMP_TARGET_DB"; fail=1 ;;
    esac
    case "$roles" in
      *'"scopedToTargetOnly":true'*) ;;
      *) echo "WARN   principal is not scoped to $MMP_TARGET_DB only; the committed .migration/allowed_targets.json plus mongo_guard are the enforcement (see .migration/05_decisions.md)" ;;
    esac
  fi
fi

echo "NOTE   MMP_RT_SRC_DSN (local MySQL fixture DSN) is set per shell by the fixture step, not by the blueprint; no source read is attempted here."
exit $fail
