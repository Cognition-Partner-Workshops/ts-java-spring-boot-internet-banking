# CI/CD

GitHub Actions pipelines for this Spring Cloud estate. **This is a demo setup: every
AWS identifier is a mock placeholder and the deploy steps are guarded so they cannot
touch a real account.**

## Modules covered

The repo has no aggregate root build — each module carries its own Gradle wrapper
(8.6), `build.gradle`, and `Dockerfile`. Both pipelines iterate the same seven
modules as a matrix:

| Module | Port |
| --- | --- |
| `internet-banking-service-registry` | 8081 |
| `internet-banking-api-gateway` | 8082 |
| `internet-banking-user-service` | 8083 |
| `internet-banking-fund-transfer-service` | 8084 |
| `internet-banking-utility-payment-service` | 8085 |
| `internet-banking-config-server` | 8090 |
| `core-banking-service` | 8092 |

> Build tooling note: the modules are **Gradle** projects (`./gradlew`), not Maven —
> the workflows use `actions/setup-java` with `cache: gradle` (`~/.gradle` caching)
> rather than `~/.m2`.

## `ci.yml`

Triggered on push to `main` and on pull requests targeting `main`.

1. **changes** — computes the build matrix. On a PR only modules with changed files
   are selected; a change under `.github/` or `docker-compose/` selects everything.
   A push to `main` always builds all seven modules.
2. **validate** — Gradle wrapper checksum validation, `classes testClasses` compile
   for the selected modules, and a static-analysis step that runs `gradle check -x test`
   for any module that declares Checkstyle/Spotless/PMD. No module declares one today,
   so that step reports "skipping" until a quality plugin is added.
3. **build-test** — per-service matrix (`fail-fast: false`) running `./gradlew build`
   on JDK 21 Temurin. Uploads JUnit HTML+XML reports (`test-reports-<service>`,
   always) and the boot jar (`jar-<service>`).
4. **ci** — aggregate status job; use this one as the required branch-protection check
   (it stays green when a PR touches no service module).

Concurrency: one run per ref, in-progress runs cancelled for PRs only.
Permissions: `contents: read`.

## `cd.yml`

Triggered on push to `main` and via `workflow_dispatch` with `service`
(`all` or a single module) and `environment` (`staging` | `production`) inputs.

1. **plan** — resolves the service matrix and the image tag (`${GITHUB_SHA::12}`).
2. **build-image** — builds the boot jar (`bootJar -x test`), then builds the module
   image via Buildx with GHA layer cache. Credentials come from **GitHub OIDC**
   (`aws-actions/configure-aws-credentials` with `id-token: write`) — there are no
   long-lived AWS keys anywhere in this repo.
3. **deploy-staging** — GitHub Environment `staging`, namespace `demo-staging`.
   Deploys shared infra placeholders and then each service via
   `.github/scripts/deploy.sh`, which renders a Deployment + Service per module.
4. **deploy-production** — `needs: deploy-staging`, GitHub Environment `production`,
   namespace `demo-prod`. The staging → production promotion gate is the
   **required reviewers** protection rule on the `production` environment; the job
   waits for a human approval before running.

### Mock-mode guard

`AWS_MOCK_MODE` (repository variable, default **`true`**) controls every side effect:

| Step | `AWS_MOCK_MODE=true` (default) | `AWS_MOCK_MODE=false` |
| --- | --- | --- |
| `configure-aws-credentials` (OIDC) | skipped | assumes the role |
| ECR login | skipped | real login |
| Image build | built and loaded locally, **`push: false`** | pushed to ECR |
| `kubectl apply` | `--dry-run=client --validate=false`; if no API server is reachable at all, falls back to local YAML validation + printing the manifest | real apply |
| Shared infra | printed placeholders only | Helm/Terraform-managed |

So a fork or demo clone can run the whole pipeline end-to-end without any AWS access:
a broken Dockerfile or invalid manifest still fails the run, but nothing is published.

## Mock identifiers (all fake)

Supplied as Actions **variables** with the fake defaults baked into `cd.yml`:

| Variable | Mock default |
| --- | --- |
| `AWS_MOCK_MODE` | `true` |
| `AWS_REGION` | `us-east-1` |
| `AWS_ACCOUNT_ID` | `123456789012` |
| `ECR_REGISTRY` | `123456789012.dkr.ecr.us-east-1.amazonaws.com` |
| `ECR_REPOSITORY_PREFIX` | `demo/internet-banking` |
| `EKS_CLUSTER_NAME` | `demo-eks` |
| `EKS_NAMESPACE_STAGING` | `demo-staging` |
| `EKS_NAMESPACE_PROD` | `demo-prod` |
| `AWS_OIDC_ROLE_ARN` | `arn:aws:iam::123456789012:role/demo-github-actions-deploy` |
| `AWS_OIDC_ROLE_ARN_STAGING` | `arn:aws:iam::123456789012:role/demo-github-actions-deploy-staging` |
| `AWS_OIDC_ROLE_ARN_PROD` | `arn:aws:iam::123456789012:role/demo-github-actions-deploy-prod` |

No repository **secrets** are required: OIDC replaces access keys, and the ECR login
token is issued by the assumed role at run time.

## Going from mock to real

1. Create the IAM OIDC provider for `token.actions.githubusercontent.com` and three
   deploy roles trusted to this repo (`repo:<org>/<repo>:ref:refs/heads/main` and
   `environment:staging` / `environment:production` conditions).
2. Create one ECR repository per module under your real prefix.
3. Create the EKS cluster and the two namespaces; grant the deploy roles access
   (EKS access entries or `aws-auth`).
4. Replace every variable in the table above with real values.
5. Provision the shared infra dependencies (Keycloak, MySQL/RDS, Keycloak Postgres,
   RabbitMQ/Amazon MQ, Zipkin) and replace the placeholder block in
   `.github/scripts/deploy.sh` with the real Helm releases or Terraform outputs.
6. Set `AWS_MOCK_MODE=false` **last** — that single flip is what enables pushes and
   live `kubectl apply`.
7. Configure the GitHub Environments: required reviewers on `production`, plus any
   wait timer / branch restriction you want on `staging`.

## Dependency updates

`.github/dependabot.yml` covers weekly updates for `github-actions`, `gradle`
(one entry per module, Spring artifacts grouped), the seven service `docker`
Dockerfiles, the locally built compose images (Keycloak, MySQL), and the `docker-compose` infra images.
