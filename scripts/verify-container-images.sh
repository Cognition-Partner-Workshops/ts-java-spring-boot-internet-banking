#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
services=(
  core-banking-service
  internet-banking-api-gateway
  internet-banking-config-server
  internet-banking-fund-transfer-service
  internet-banking-service-registry
  internet-banking-user-service
  internet-banking-utility-payment-service
)
failed=0

verify_service() {
  local service=$1 image="container-privileges:$1" uid configured_user
  if [[ ! -f "$service/build/libs/$service-0.0.1-SNAPSHOT.jar" ]]; then
    (cd "$service" && ./gradlew bootJar -x test) || return 1
  fi
  docker build -t "$image" "$service" || return 1
  uid=$(docker run --rm --entrypoint id "$image" -u) || return 1
  [[ "$uid" =~ ^[0-9]+$ && "$uid" != 0 ]] || return 1
  configured_user=$(docker inspect -f '{{.Config.User}}' "$image") || return 1
  case "${configured_user%%:*}" in ''|root|0) return 1 ;; esac
  docker run --rm --entrypoint sh "$image" -c \
    'test -r /app.jar && test -r /wait-for-it.sh && test -x /wait-for-it.sh && test -x ./wait-for-it.sh && test -d "$HOME" && test -w "$HOME"' || return 1
}

for service in "${services[@]}"; do
  if verify_service "$service"; then
    echo "PASS $service"
  else
    echo "FAIL $service"
    failed=1
  fi
done

if docker build -t container-privileges:mysql docker-compose/mysql &&
   envs=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' container-privileges:mysql) &&
   ! [[ "$envs" == *MYSQL_ROOT_PASSWORD=* ]]; then
  echo 'PASS mysql'
else
  echo 'FAIL mysql'
  failed=1
fi
exit "$failed"
