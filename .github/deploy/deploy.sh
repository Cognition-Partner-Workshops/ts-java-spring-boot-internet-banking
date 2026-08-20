#!/usr/bin/env bash
# Renders and applies the demo Kubernetes manifests for every service.
#
# DEMO SAFETY: unless ENABLE_REAL_DEPLOY=true AND DRY_RUN=false, the script only
# prints the kubeconfig/kubectl commands and the rendered manifests. All AWS and
# cluster identifiers it consumes are mocked placeholders.
set -euo pipefail

: "${IMAGE_TAG:?IMAGE_TAG is required}"
: "${K8S_NAMESPACE:?K8S_NAMESPACE is required}"
ECR_REGISTRY="${ECR_REGISTRY:-123456789012.dkr.ecr.us-east-1.amazonaws.com}"
AWS_REGION="${AWS_REGION:-us-east-1}"
EKS_CLUSTER="${EKS_CLUSTER:-demo-eks-cluster}"
DRY_RUN="${DRY_RUN:-true}"
ENABLE_REAL_DEPLOY="${ENABLE_REAL_DEPLOY:-false}"

# service:container-port, matching docker-compose/docker-compose.yml
SERVICES=(
  "internet-banking-config-server:8090"
  "internet-banking-service-registry:8081"
  "internet-banking-api-gateway:8082"
  "internet-banking-user-service:8083"
  "internet-banking-fund-transfer-service:8084"
  "internet-banking-utility-payment-service:8085"
  "core-banking-service:8092"
)

template="$(dirname "$0")/deployment.tmpl.yaml"
outdir="${RUNNER_TEMP:-/tmp}/rendered-manifests"
mkdir -p "${outdir}"

for entry in "${SERVICES[@]}"; do
  service="${entry%%:*}"
  port="${entry##*:}"
  sed -e "s|\${SERVICE}|${service}|g" \
      -e "s|\${SERVICE_PORT}|${port}|g" \
      -e "s|\${K8S_NAMESPACE}|${K8S_NAMESPACE}|g" \
      -e "s|\${ECR_REGISTRY}|${ECR_REGISTRY}|g" \
      -e "s|\${IMAGE_TAG}|${IMAGE_TAG}|g" \
      "${template}" > "${outdir}/${service}.yaml"
done

if [ "${ENABLE_REAL_DEPLOY}" = "true" ] && [ "${DRY_RUN}" != "true" ]; then
  aws eks update-kubeconfig --name "${EKS_CLUSTER}" --region "${AWS_REGION}"
  kubectl create namespace "${K8S_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
  kubectl apply -n "${K8S_NAMESPACE}" -f "${outdir}"
  for entry in "${SERVICES[@]}"; do
    kubectl rollout status -n "${K8S_NAMESPACE}" "deployment/${entry%%:*}" --timeout=5m
  done
else
  echo "DRY RUN — no cluster is contacted. Would run:"
  echo "  aws eks update-kubeconfig --name ${EKS_CLUSTER} --region ${AWS_REGION}"
  echo "  kubectl apply -n ${K8S_NAMESPACE} -f ${outdir}"
  for entry in "${SERVICES[@]}"; do
    echo "  kubectl rollout status -n ${K8S_NAMESPACE} deployment/${entry%%:*}"
  done
  echo "Rendered manifests:"
  for entry in "${SERVICES[@]}"; do
    echo "::group::${entry%%:*}.yaml"
    cat "${outdir}/${entry%%:*}.yaml"
    echo "::endgroup::"
  done
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
      echo "### Mocked deploy to \`${K8S_NAMESPACE}\` (${EKS_CLUSTER}, ${AWS_REGION})"
      echo "Image tag: \`${IMAGE_TAG}\` — no AWS or Kubernetes API was called."
    } >> "${GITHUB_STEP_SUMMARY}"
  fi
fi
