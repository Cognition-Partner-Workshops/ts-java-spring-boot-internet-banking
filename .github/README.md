# CI/CD

Two workflows live in `.github/workflows`. The build tooling in this repository is
**Gradle** (one wrapper per service, Gradle 8.6, Java 21) — there are no Maven
POMs, so every command below is a `./gradlew` invocation run inside a service
directory.

## Service matrix

| Service | Port | Notes |
| --- | --- | --- |
| `internet-banking-config-server` | 8090 | Spring Cloud Config server |
| `internet-banking-service-registry` | 8081 | Eureka |
| `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway + OAuth2 |
| `internet-banking-user-service` | 8083 | Keycloak admin client |
| `internet-banking-fund-transfer-service` | 8084 | |
| `internet-banking-utility-payment-service` | 8085 | |
| `core-banking-service` | 8092 | Ledger, Flyway/MySQL at runtime |

## `ci.yml`

Triggers: push to `main`/`master`, pull requests against them, manual dispatch.
Concurrency cancels superseded PR runs. Permissions are `contents: read` for
every job.

| Job | What it does |
| --- | --- |
| `changes` | `dorny/paths-filter` builds the matrix. On PRs only changed services build; pushes, manual runs, and changes under `docker-compose/**` or `.github/workflows/**` build everything. |
| `build` | Matrix per service: `actions/setup-java` (Temurin 21, Gradle cache) → `./gradlew build` → `./gradlew jacocoTestReport`. Uploads JUnit XML/HTML and JaCoCo reports as artifacts and writes a per-service test count to the job summary. |
| `compose-validate` | `docker compose config --quiet` for `docker-compose.yml` and `docker-compose-support-apps.yml`. |
| `dockerfile-lint` | `hadolint` (via container) over every `Dockerfile`, failing only on `error`-level findings. |
| `workflow-lint` | `actionlint` over the workflow files. |
| `dependency-scan` | Trivy filesystem scan (vuln + secret + misconfig, HIGH/CRITICAL) run from the `aquasec/trivy` image; findings go to the job summary and a SARIF artifact. Reported, not enforced. |
| `ci-status` | `always()` aggregate gate — the single check to mark required in branch protection. Skipped jobs pass; failures and cancellations fail. |

Testing notes:

* Every suite runs on in-memory H2 (`spring-boot-starter-test` + `com.h2database:h2`),
  so no MySQL/PostgreSQL service container or Testcontainers setup is required.
  Add a `services:` block to the `build` job if a service later gains tests that
  need a real database.
* The JaCoCo plugin is applied per service in `build.gradle`; `test` is finalized
  by `jacocoTestReport`, emitting XML + HTML.
* No formatter or static-analysis plugin (Spotless, Checkstyle, PMD) is configured
  in the Gradle builds, so CI has no Java format gate — only YAML/Dockerfile lint.

## `cd.yml` — mocked demo pipeline

`workflow_dispatch` only, with inputs `environment` (`staging`/`production`),
`dry_run` (default `true`) and `image_tag` (defaults to the commit SHA).

Flow: build the boot jar → `docker build` the per-service image → push to Amazon
ECR → deploy Kubernetes manifests to Amazon EKS via `.github/deploy/deploy.sh`,
which renders `.github/deploy/deployment.tmpl.yaml` (Deployment + Service) for
each service.

**Every AWS identifier is a placeholder and the pipeline cannot reach real
infrastructure** unless the repository variable `ENABLE_REAL_DEPLOY` is `true`
*and* the run is dispatched with `dry_run: false`. Otherwise the ECR login/push,
`aws eks update-kubeconfig` and `kubectl apply` steps only print what they would
run and dump the rendered manifests into the job log/summary.

Mocked values:

| Value | Placeholder |
| --- | --- |
| Account / region | `123456789012` / `us-east-1` |
| ECR registry | `123456789012.dkr.ecr.us-east-1.amazonaws.com` |
| EKS cluster | `demo-eks-cluster` |
| Namespaces | `demo-banking-staging`, `demo-banking-prod` |
| OIDC role | `arn:aws:iam::123456789012:role/demo-github-actions-deploy` |

AWS access uses OIDC (`aws-actions/configure-aws-credentials` with
`permissions: id-token: write`) — no long-lived keys are stored.

### Required GitHub configuration

| Kind | Name | Purpose |
| --- | --- | --- |
| Environment | `staging` | No protection rules needed. |
| Environment | `production` | Add required reviewers so the prod deploy waits for approval. |
| Variable | `ENABLE_REAL_DEPLOY` | Unset/`false` keeps the pipeline mocked. Set to `true` only against real infrastructure. |
| Secrets | *(none)* | OIDC replaces static AWS keys; if you wire real infra, replace the placeholder role ARN and registry in `cd.yml`. |

## `dependabot.yml`

Weekly updates for Gradle (per service directory, grouped Spring/test bumps),
GitHub Actions, and Docker base images (services plus `docker-compose/keycloak`
and `docker-compose/mysql`).

## Running the same checks locally

```shell
export JAVA_HOME=/path/to/jdk-21 && export PATH=$JAVA_HOME/bin:$PATH
cd core-banking-service && ./gradlew build jacocoTestReport --no-daemon
cd ../docker-compose && docker compose -f docker-compose.yml config --quiet
```
