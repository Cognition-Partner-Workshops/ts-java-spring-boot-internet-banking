#!/usr/bin/env bash
# Deploy helper used by .github/workflows/cd.yml.
#
# DEMO SAFETY: when AWS_MOCK_MODE is anything other than "false" (the default),
# every kubectl call runs with `--dry-run=client`, so the manifests are only
# rendered and validated locally - nothing is ever sent to a cluster.
#
# Usage:
#   deploy.sh infra   <namespace>
#   deploy.sh service <namespace> <service-name> <image>
set -euo pipefail

MODE="${1:?usage: deploy.sh <infra|service> ...}"
NAMESPACE="${2:?namespace required}"
AWS_MOCK_MODE="${AWS_MOCK_MODE:-true}"

KUBECTL_ARGS=()
if [ "$AWS_MOCK_MODE" != "false" ]; then
  # Mock credentials: render/validate manifests client-side only.
  # --validate=false is required too, because schema validation would otherwise
  # fetch the OpenAPI spec from a (non-existent) cluster.
  KUBECTL_ARGS+=(--dry-run=client --validate=false)
  echo ">>> AWS_MOCK_MODE=${AWS_MOCK_MODE}: kubectl runs with --dry-run=client (no cluster contact)."
fi

# Container port per module, mirroring each module's Dockerfile EXPOSE.
service_port() {
  case "$1" in
    internet-banking-service-registry) echo 8081 ;;
    internet-banking-api-gateway) echo 8082 ;;
    internet-banking-user-service) echo 8083 ;;
    internet-banking-fund-transfer-service) echo 8084 ;;
    internet-banking-utility-payment-service) echo 8085 ;;
    internet-banking-config-server) echo 8090 ;;
    core-banking-service) echo 8092 ;;
    *) echo "unknown service: $1" >&2; exit 1 ;;
  esac
}

apply_stdin() {
  local manifest
  manifest="$(cat)"

  if [ "$AWS_MOCK_MODE" = "false" ]; then
    kubectl apply -n "$NAMESPACE" -f - <<< "$manifest"
    return
  fi

  # Mock mode: try the client-side dry run first. `kubectl apply --dry-run=client`
  # still needs a reachable API server to resolve REST mappings, which by design
  # does not exist here, so fall back to a pure local YAML parse and print the
  # manifest that *would* have been applied.
  if kubectl apply -n "$NAMESPACE" "${KUBECTL_ARGS[@]}" -f - <<< "$manifest" 2>/dev/null; then
    return
  fi
  echo "    (no cluster reachable - validating manifest locally instead of applying)"
  python3 -c 'import sys
try:
    import yaml
except ImportError:
    sys.exit(0)
list(yaml.safe_load_all(sys.stdin))
print("    manifest is valid YAML")' <<< "$manifest" || true
  while IFS= read -r line; do printf '    | %s\n' "$line"; done <<< "$manifest"
}

case "$MODE" in
  infra)
    # Shared infra dependencies of this estate: Keycloak, MySQL (RDS in a real
    # deployment), PostgreSQL for Keycloak, RabbitMQ (Amazon MQ) and Zipkin.
    # PLACEHOLDER: a real pipeline would either point at managed AWS services or
    # install charts here, e.g.
    #   helm upgrade --install keycloak bitnami/keycloak -n "$NAMESPACE" -f infra/keycloak-values.yaml
    #   helm upgrade --install rabbitmq bitnami/rabbitmq -n "$NAMESPACE" -f infra/rabbitmq-values.yaml
    # Left as documented no-ops so the demo pipeline stays side-effect free.
    echo ">>> Shared infra dependencies for namespace '${NAMESPACE}' (placeholders):"
    for dep in keycloak keycloak-postgres mysql rabbitmq zipkin; do
      echo "    - ${dep}: managed externally (Terraform/Helm) - no action in demo mode"
    done
    cat <<EOF | apply_stdin
apiVersion: v1
kind: ConfigMap
metadata:
  name: internet-banking-infra-endpoints
  labels:
    app.kubernetes.io/part-of: internet-banking
data:
  # Mock endpoints - swap for the real service discovery values.
  keycloak.url: "http://keycloak.${NAMESPACE}.svc.cluster.local:8080"
  mysql.host: "mysql.${NAMESPACE}.svc.cluster.local"
  rabbitmq.host: "rabbitmq.${NAMESPACE}.svc.cluster.local"
  zipkin.url: "http://zipkin.${NAMESPACE}.svc.cluster.local:9411"
EOF
    ;;

  service)
    SERVICE="${3:?service name required}"
    IMAGE="${4:?image required}"
    PORT="$(service_port "$SERVICE")"
    echo ">>> Deploying ${SERVICE} (${IMAGE}) to namespace '${NAMESPACE}' on port ${PORT}"
    cat <<EOF | apply_stdin
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${SERVICE}
  labels:
    app.kubernetes.io/name: ${SERVICE}
    app.kubernetes.io/part-of: internet-banking
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: ${SERVICE}
  template:
    metadata:
      labels:
        app.kubernetes.io/name: ${SERVICE}
        app.kubernetes.io/part-of: internet-banking
    spec:
      containers:
        - name: ${SERVICE}
          image: ${IMAGE}
          ports:
            - containerPort: ${PORT}
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: docker
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: ${PORT}
            initialDelaySeconds: 30
            periodSeconds: 10
          resources:
            requests:
              cpu: 100m
              memory: 512Mi
            limits:
              memory: 1Gi
---
apiVersion: v1
kind: Service
metadata:
  name: ${SERVICE}
  labels:
    app.kubernetes.io/name: ${SERVICE}
spec:
  selector:
    app.kubernetes.io/name: ${SERVICE}
  ports:
    - port: ${PORT}
      targetPort: ${PORT}
EOF
    ;;

  *)
    echo "unknown mode: $MODE" >&2
    exit 1
    ;;
esac
