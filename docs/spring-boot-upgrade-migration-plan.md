# Spring Boot Upgrade Migration Plan

Planning document for upgrading the seven independently-built Gradle modules of this
repository from Spring Boot 3.2.4 / Spring Cloud 2023.0.0 to the latest stable Spring Boot
3.x release and its paired Spring Cloud release train. This document is documentation only;
no build or application code is changed by it.

Version facts below were verified on 2026-09-02 against `start.spring.io`, the
`spring-projects/spring-boot` and `spring-cloud/spring-cloud-release` GitHub release feeds,
the Spring Cloud project page release-train table, and the published BOM sources
(`spring-boot-dependencies` build.gradle at tag `v3.5.16`, `spring-cloud-dependencies`
pom at tag `v2025.0.3`). Re-verify before execution; a newer 3.5.x patch may exist.

---

## 1. Current-state inventory

All seven modules are standalone Gradle builds (no root/aggregate build, no shared
`settings.gradle`, no version catalog). Every module declares the same platform versions
inline in its own `build.gradle` and ships its own Gradle wrapper.

### 1.1 Platform versions (identical in all 7 modules)

| Item | Value | Where declared |
|---|---|---|
| Spring Boot plugin | `3.2.4` | `plugins { id 'org.springframework.boot' version '3.2.4' }` |
| Dependency-management plugin | `1.1.4` | `plugins { id 'io.spring.dependency-management' version '1.1.4' }` |
| Spring Cloud BOM | `2023.0.0` (Leyton) | `ext { set('springCloudVersion', "2023.0.0") }` + `mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"` |
| Java | `21` (`sourceCompatibility = '21'`) | `java { sourceCompatibility = '21' }` |
| Gradle wrapper | `8.6` | `gradle/wrapper/gradle-wrapper.properties` → `gradle-8.6-bin.zip` |
| gradle-git-properties plugin | `2.4.2` | all modules except `internet-banking-service-registry` |

### 1.2 Per-module dependency inventory

Versions shown in **bold** are hard-pinned in `build.gradle` (i.e. they override whatever
the Boot / Spring Cloud BOMs would manage). Unversioned entries are BOM-managed.

| Module | Spring Boot | Spring Cloud | Gradle | Java | Key starters (BOM-managed) | Hard-pinned third-party versions |
|---|---|---|---|---|---|---|
| `internet-banking-service-registry` | 3.2.4 | 2023.0.0 | 8.6 | 21 | `spring-boot-starter-web`, `spring-boot-starter-actuator`, `spring-cloud-starter-netflix-eureka-server` | none |
| `internet-banking-config-server` | 3.2.4 | 2023.0.0 | 8.6 | 21 | `spring-boot-starter-actuator`, `spring-cloud-config-server` | none |
| `internet-banking-api-gateway` | 3.2.4 | 2023.0.0 | 8.6 | 21 | `spring-cloud-starter-gateway`, `spring-cloud-starter-netflix-eureka-client`, `spring-cloud-starter-config`, `spring-cloud-starter-bootstrap`, `spring-boot-starter-oauth2-client`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security`, `spring-boot-starter-webflux`, `spring-boot-starter-actuator`, `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer` | none |
| `core-banking-service` | 3.2.4 | 2023.0.0 | 8.6 | 21 | `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-cloud-starter-netflix-eureka-client`, `spring-cloud-starter-config`, `spring-cloud-starter-bootstrap`, actuator + Brave/Zipkin tracing, `feign-micrometer`, Lombok | **`org.flywaydb:flyway-core:10.12.0`**, **`org.flywaydb:flyway-mysql:10.12.0`**, **`com.mysql:mysql-connector-j:8.4.0`**, **`org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0`**, **`com.h2database:h2:2.2.224`** (test) |
| `internet-banking-user-service` | 3.2.4 | 2023.0.0 | 8.6 | 21 | `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-cloud-starter-netflix-eureka-client`, `spring-cloud-starter-openfeign`, `spring-cloud-starter-config`, `spring-cloud-starter-bootstrap`, actuator + Brave/Zipkin tracing, `feign-micrometer`, Lombok | **`io.github.openfeign:feign-okhttp:13.2.1`**, **`org.keycloak:keycloak-admin-client:24.0.4`**, **`com.mysql:mysql-connector-j:8.4.0`**, **`org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0`**, **`com.h2database:h2:2.2.224`** (test) |
| `internet-banking-fund-transfer-service` | 3.2.4 | 2023.0.0 | 8.6 | 21 | `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-cloud-starter-openfeign`, `spring-cloud-starter-netflix-eureka-client`, `spring-cloud-starter-config`, `spring-cloud-starter-bootstrap`, actuator + Brave/Zipkin tracing, `feign-micrometer`, Lombok | **`com.mysql:mysql-connector-j:8.4.0`**, **`org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0`**, **`com.h2database:h2:2.2.224`** (test) |
| `internet-banking-utility-payment-service` | 3.2.4 | 2023.0.0 | 8.6 | 21 | `spring-boot-starter-data-jpa`, `spring-boot-starter-web`, `spring-cloud-starter-netflix-eureka-client`, `spring-cloud-starter-openfeign`, `spring-cloud-starter-config`, `spring-cloud-starter-bootstrap`, actuator + Brave/Zipkin tracing, `feign-micrometer`, Lombok | **`com.mysql:mysql-connector-j:8.4.0`**, **`org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0`**, **`com.h2database:h2:2.2.224`** (test) |

Observations relevant to the upgrade:

- The four servlet-based business services (`core-banking`, `user`, `fund-transfer`,
  `utility-payment`) pull in `springdoc-openapi-starter-webflux-ui` even though they are
  `spring-boot-starter-web` (Spring MVC) applications. This works today only because
  springdoc's WebFlux UI starter transitively drags in WebFlux and Boot picks MVC when both
  are present.
- Only `core-banking-service` uses Flyway; the other MySQL services rely on Hibernate DDL.
- `spring-cloud-starter-bootstrap` is used everywhere for legacy `bootstrap.yml` config
  loading (`spring.cloud.config.uri` per profile) instead of `spring.config.import`.
- Runtime configuration for all services is fetched by `internet-banking-config-server`
  from an external public Git repository, so gateway route definitions and OAuth2 client
  settings live outside this repo.

---

## 2. Target state

### 2.1 Versions to pin

| Item | Current | Target | Notes |
|---|---|---|---|
| Spring Boot | 3.2.4 | **3.5.16** | Latest stable 3.x GA (released 2026-06-25). Newer Boot lines (4.0.x, 4.1.x) are 4.x and out of scope for a "stay on 3.x" upgrade. |
| Spring Cloud release train | 2023.0.0 (Leyton) | **2025.0.3** (Northfields) | Spring Cloud project page maps `2025.0.x` ↔ Boot `3.5.x`. 2025.0.3 is the latest service release of that train (2026-06-12). Its parent `spring-cloud-build` 4.3.4 is built on Boot 3.5.x. |
| `io.spring.dependency-management` plugin | 1.1.4 | **1.1.7** (or latest 1.1.x) | Optional but recommended; 1.1.4 works with Boot 3.5. |
| Gradle wrapper | 8.6 | **8.14** (any 8.x ≥ 8.4 is accepted) | Boot 3.5 Gradle plugin requires Gradle 7.6.4+ or 8.4+. 8.6 already satisfies the minimum; bump anyway to a recent 8.x for Java 21 toolchain and plugin fixes. See §4.5. |
| Java | 21 | **21** (unchanged) | Boot 3.5 supports Java 17–25. |

Component versions managed by the target BOMs (for conflict analysis in §4.4):

| Managed by | Component | Version under Boot 3.5.16 / SC 2025.0.3 |
|---|---|---|
| Boot BOM | Spring Framework | 6.2.x |
| Boot BOM | Spring Security | 6.5.11 |
| Boot BOM | Hibernate ORM | 6.6.53.Final |
| Boot BOM | Flyway | 11.7.2 |
| Boot BOM | MySQL Connector/J | 9.7.0 |
| Boot BOM | H2 | 2.3.232 |
| Boot BOM | Micrometer Tracing | 1.5.12 |
| Boot BOM | Zipkin Reporter | 3.5.3 |
| Boot BOM | Netty | 4.1.135.Final |
| Spring Cloud BOM | Spring Cloud Commons | 4.3.3 |
| Spring Cloud BOM | Spring Cloud Config | 4.3.4 |
| Spring Cloud BOM | Spring Cloud Gateway | 4.3.5 |
| Spring Cloud BOM | Spring Cloud Netflix (Eureka) | 4.3.3 |
| Spring Cloud BOM | Spring Cloud OpenFeign | 4.3.3 |
| Spring Cloud OpenFeign BOM | OpenFeign core (`io.github.openfeign`) | 13.6.1 |

### 2.2 Support-window caveat

Per the Spring project support pages, OSS support for Spring Boot 3.5.x and for the
Spring Cloud 2025.0.x train ended in mid-2026 (the Spring Cloud page already marks
2025.0.x as end-of-life). 3.5.16 is therefore the terminal 3.x line: it is the correct
target for a 3.x-to-3.x step and it removes the largest chunk of drift, but the team
should treat this as a staging point and plan a follow-on Boot 4.x / Spring Cloud 2025.1.x
upgrade (which brings Spring Framework 7, Jakarta EE 11, and the Spring Security 7 API)
rather than a long-term resting place. Do not attempt to jump straight to 4.x in the same
change set as this plan; the 4.x migration has its own breaking-change list.

### 2.3 Target `build.gradle` shape (per module, for reference only)

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.16'
    id 'io.spring.dependency-management' version '1.1.7'
    id "com.gorylenko.gradle-git-properties" version "2.4.2"   // where present
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

ext {
    set('springCloudVersion', "2025.0.3")
}
```

`gradle/wrapper/gradle-wrapper.properties`:

```
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14-bin.zip
```

---

## 3. Test-suite map

| Module | Test classes | Nature | Spring context? |
|---|---|---|---|
| `core-banking-service` | `service/AccountServiceTest` (6 tests), `service/TransactionServiceTest` (9 tests), `service/UserServiceTest` (3 tests) | Real unit tests. JUnit 5 + Mockito (`mock(...)` on repositories, services constructed directly). | No |
| `core-banking-service` | `CoreBankingServiceApplicationTests` | `@SpringBootTest` `contextLoads()` | Yes (H2 via `spring-boot-starter-test` + `h2` test dependency) |
| `internet-banking-service-registry` | `InternetBankingServiceRegistryApplicationTests` | Empty `@SpringBootTest contextLoads()` | Yes |
| `internet-banking-config-server` | `InternetBankingConfigServerApplicationTests` | Empty `@SpringBootTest contextLoads()` | Yes |
| `internet-banking-api-gateway` | `InternetBankingApiGatewayApplicationTests` | Empty `@SpringBootTest contextLoads()` | Yes |
| `internet-banking-user-service` | `InternetBankingUserServiceApplicationTests` | Empty `@SpringBootTest contextLoads()` | Yes |
| `internet-banking-fund-transfer-service` | `InternetBankingFundTransferServiceApplicationTests` | Empty `@SpringBootTest contextLoads()` | Yes |
| `internet-banking-utility-payment-service` | `InternetBankingUtilityPaymentServiceApplicationTests` | Empty `@SpringBootTest contextLoads()` | Yes |

What this means for the upgrade:

- The only behavioural regression coverage is the 18 Mockito-based service tests in
  `core-banking-service`. They exercise `AccountService`, `TransactionService` and
  `UserService` in isolation and will catch Java/Mockito/JUnit API breakage but not
  Spring wiring, JPA/Hibernate, Flyway, Feign, or security changes.
- The `contextLoads()` tests are still valuable for this upgrade: they will fail on
  auto-configuration incompatibilities, bean-definition conflicts, and removed/renamed
  properties (Boot fails fast on unknown-but-deprecated property names when using the
  `spring-boot-properties-migrator`). Note that the context tests for modules using
  `spring-cloud-starter-config` + `bootstrap` may try to reach a config server unless the
  test profile disables it, so confirm how they currently pass before relying on them.
- **There is no CI workflow.** `.github/` contains only `FUNDING.yml`. Every verification
  step in this plan is run manually.
- **There are no automated integration or end-to-end tests.** E2E verification is the
  Postman collection in `postman_collection/`
  (`JAVA_TO_DEV_MICROSERVICES.postman_collection.json` with
  `BANKING_CORE_MICROSERVICES_PROJECT.postman_environment.json`), run against the full
  Docker Compose stack in `docker-compose/`. The Postman environment's `client_id` is
  known to be stale; use `javatodev-internet-banking-api-client` from the Keycloak realm
  export.

---

## 4. Risks and concerns

### 4.1 Spring Cloud release-train coupling

Spring Cloud trains are hard-coupled to a Boot minor line. `2023.0.x` supports Boot
3.2.x/3.3.x only; running Boot 3.5.16 against `2023.0.0` will produce class-loading or
auto-configuration failures (Spring Cloud Commons, Config and Gateway all compile against
specific Boot/Framework internals). The Boot plugin version and `springCloudVersion` must
therefore be bumped **together, in the same commit, in every module**. Intermediate states
(e.g. Boot 3.5 + SC 2023.0) are not valid and must not be committed even to a feature
branch that anyone else builds from.

Mitigation: change both values in one edit per module; run `./gradlew dependencies
--configuration runtimeClasspath` and confirm all `org.springframework.cloud:*` artifacts
resolve to the 4.3.x line and all `org.springframework.boot:*` to 3.5.16.

### 4.2 `spring-cloud-starter-gateway` artifact rename

In Spring Cloud Gateway 4.3.x (train 2025.0.x) the reactive gateway starter is
`spring-cloud-starter-gateway-server-webflux`. The old `spring-cloud-starter-gateway`
artifact still exists in 4.3.x as a compatibility alias, but it is deprecated and the
documentation now only references the new coordinates. Related renames in the same
release: `spring-cloud-starter-gateway-mvc` → `spring-cloud-starter-gateway-server-webmvc`,
and the configuration prefix moved from `spring.cloud.gateway.*` to
`spring.cloud.gateway.server.webflux.*` (the old prefix is still honoured with
deprecation warnings in 4.3, and is removed in the 2025.1.x train).

Impact for `internet-banking-api-gateway`:

- Switch the dependency to `spring-cloud-starter-gateway-server-webflux` as part of this
  upgrade so the next (4.x) upgrade is not blocked by a removed artifact.
- Gateway route definitions live in the external config repo served by
  `internet-banking-config-server`, not in this repository. Audit that repository for
  `spring.cloud.gateway.routes` / `discovery.locator` keys and plan a coordinated change
  (or accept deprecation warnings for now). The `GlobalFilter` bean in
  `GatewayConfiguration` uses stable APIs and should not need changes.

### 4.3 OAuth2 / WebFlux security default changes in the gateway (Keycloak)

`internet-banking-api-gateway` is a WebFlux application using `@EnableWebFluxSecurity`,
`ServerHttpSecurity`, `oauth2ResourceServer().jwt().jwkSetUri(...)`, and also declares
`spring-boot-starter-oauth2-client`. Moving Spring Security 6.2 → 6.5 brings:

- Continued deprecation of the lambda-less / non-DSL configuration styles; the current
  code already uses the lambda DSL for `csrf` and `oauth2ResourceServer`, but the
  `authorizeExchange` block should be reviewed for any deprecated matcher methods.
- Tighter defaults around `OAuth2AuthorizedClientManager`, PKCE for public clients, and
  `oauth2Login` redirect handling when both `oauth2-client` and `oauth2-resource-server`
  starters are present. Because both starters are on the classpath, Boot auto-configures
  both a resource-server filter chain contribution and OAuth2 client registrations; the
  presence of `spring.security.oauth2.client.registration.*` properties (from the external
  config repo) determines whether login redirects are wired. Verify the effective behaviour
  for an unauthenticated request is still `401` (resource server) and not a `302` to
  Keycloak.
- JWT validation: Spring Security 6.5's `NimbusReactiveJwtDecoder` and issuer/audience
  validators are stricter about `iss` matching and clock skew. Confirm the Keycloak realm
  issuer URL used in the token (`http://keycloak:8080/realms/...` inside Docker vs
  `localhost`) matches what the gateway validates.
- The `exchange.getPrincipal()` → `X-Auth-Id` header propagation in `GatewayConfiguration`
  depends on the JWT `sub` claim being the principal name; this is unchanged but should be
  covered by the Postman auth flow.

Additionally, Keycloak itself is external (Docker image + realm export under
`docker-compose/`); this upgrade does not change Keycloak. The `keycloak-admin-client`
library pin in the user service is covered in §4.4.

### 4.4 Hard-pinned dependency versions vs. newer BOM-managed versions

Every hard pin in §1.2 overrides the BOM. After the upgrade several of these will be
*older* than what Boot 3.5.16 manages, which risks runtime incompatibilities that the
compiler will not catch.

| Pin | Current | Boot 3.5.16 / SC 2025.0.3 manages | Recommendation |
|---|---|---|---|
| `org.springdoc:springdoc-openapi-starter-webflux-ui` | **2.1.0** | not managed by Boot; latest is 2.8.x | **Highest-risk pin.** springdoc 2.1.0 targets Boot 3.0/3.1 and Spring Framework 6.0. It is known to break on newer Boot lines (e.g. `NoSuchMethodError` around `ControllerAdviceBean` / `RequestMappingHandlerMapping`, and Swagger UI 404s from changed static-resource handling). Bump to the latest 2.8.x (2.8.6 at time of writing; springdoc's compatibility table lists 2.8.x for Boot 3.4/3.5). Also consider swapping the four MVC services to `springdoc-openapi-starter-webmvc-ui`, since they are Spring MVC apps; keep the WebFlux starter only if the transitive WebFlux dependency is deliberately relied upon. |
| `org.flywaydb:flyway-core` / `flyway-mysql` | **10.12.0** | 11.7.2 | Remove the pin and let Boot manage, or pin to 11.7.2. Flyway 10→11 has no MySQL-relevant migration-syntax changes, but Boot 3.5's `FlywayAutoConfiguration` is compiled against Flyway 11 API (`Configuration` interface additions) — a 10.x pin may fail context startup in `core-banking-service`. Check the Flyway schema history table is compatible (it is, 10→11 is a metadata no-op). |
| `com.mysql:mysql-connector-j` | **8.4.0** | 9.7.0 | Remove pin. 8.4 works against the MySQL server in `docker-compose/`, but 9.x is what Boot tests against. Note Connector/J 9.x drops `com.mysql.jdbc.Driver` legacy class name; the code uses the `com.mysql.cj` driver via Boot auto-detection, so no change expected. |
| `com.h2database:h2` (test) | **2.2.224** | 2.3.232 | Remove pin. H2 2.3 has minor SQL-mode differences; `core-banking-service` context test uses H2, so re-run it. |
| `io.github.openfeign:feign-okhttp` | **13.2.1** | 13.6.1 (via Spring Cloud OpenFeign BOM) | Remove pin so it aligns with `feign-core`/`feign-micrometer` from the BOM; mixing 13.2.1 and 13.6.1 Feign artifacts can cause `NoSuchMethodError` at first Feign call. Applies to `internet-banking-user-service`. |
| `org.keycloak:keycloak-admin-client` | **24.0.4** | not managed | Independent of Spring; compatible with the Keycloak server image in `docker-compose/`. Leave as-is unless the Keycloak server is upgraded. Note it brings RESTEasy/Jakarta client transitively; verify no Jakarta REST API version clash after the Boot bump (`./gradlew dependencyInsight --dependency jakarta.ws.rs-api`). |
| `com.gorylenko.gradle-git-properties` | 2.4.2 | n/a (Gradle plugin) | Compatible with Gradle 8.x; no change required. |

General rule for this upgrade: **prefer removing a pin over bumping it**, so the BOM
manages the version and future Boot bumps are a one-line change.

### 4.5 Gradle wrapper bump across all 7 modules

Boot 3.5's Gradle plugin requires Gradle 7.6.4+ or 8.4+, so the existing 8.6 wrapper
technically still works. However:

- Each module carries its own `gradle/wrapper/` directory and `gradlew`/`gradlew.bat`
  scripts; there is no root build to update once. The bump is seven separate wrapper
  updates (`./gradlew wrapper --gradle-version 8.14 --distribution-type bin` run inside
  each module) and seven pairs of changed `gradle-wrapper.jar` + properties files.
- Gradle 8.6 predates several Java 21 toolchain fixes and emits deprecation warnings that
  become errors in Gradle 9. Moving to a late 8.x now avoids a second bump when Boot 4.x
  (which requires Gradle 8.14+ / 9.x) is attempted.
- The Docker images built by the `docker-compose/` flow and the `maintenance` blueprint
  step run `./gradlew bootJar -x test --no-daemon` per service; any wrapper change is
  exercised there, and a mismatched wrapper jar/properties pair will break image builds.

### 4.6 Weak automated test safety net

As documented in §3, six of seven modules have zero behavioural tests and there is no CI.
Consequences:

- A green `./gradlew build` for a module proves only that it compiles and the Spring
  context starts with the test profile. Feign client contracts, JPA queries, Flyway
  migrations against real MySQL, gateway routing, and JWT validation are only exercised by
  bringing up the full Docker Compose stack and running the Postman collection.
- Recommended pre-work (separate PR, before the upgrade): add a minimal GitHub Actions
  workflow that runs `./gradlew clean build test --no-daemon` in each of the seven module
  directories on pull requests. Without this, the sequencing in §5 relies entirely on
  manual discipline.
- Recommended pre-work (optional): add `@DataJpaTest` slices for the repositories in the
  three MySQL business services and a `@WebFluxTest`/`WebTestClient` test for the gateway's
  security chain (401 without token, 200 with a mocked JWT). These turn several §4.3 and
  §4.4 risks into fast-failing tests.

### 4.7 Other items to check

- **`spring-cloud-starter-bootstrap` / `bootstrap.yml`**: still supported in Spring Cloud
  2025.0 but remains legacy. No change is required for this upgrade; consider moving to
  `spring.config.import=configserver:` in a follow-up.
- **Tracing**: Micrometer Tracing 1.2 → 1.5 and Zipkin Reporter 2.x → 3.x. Property names
  (`management.tracing.*`, `management.zipkin.tracing.endpoint`) are unchanged. Verify
  traces still reach Zipkin (port 9411) after the upgrade.
- **Hibernate 6.4 → 6.6**: stricter validation of `@Column` / `@Id` mappings and changed
  default for some `hibernate.ddl-auto` behaviours. The services without Flyway rely on
  Hibernate DDL; run against a fresh MySQL volume and against an existing one.
- **Boot 3.4/3.5 property renames**: run each module once with
  `runtimeOnly 'org.springframework.boot:spring-boot-properties-migrator'` added
  temporarily to surface deprecated properties in the (external) config repo, then remove
  it.
- **Jackson / Lombok**: Boot 3.5 manages Jackson 2.19; Lombok's version is BOM-managed and
  supports Java 21 with the bumped version. No action expected.

---

## 5. Recommended migration sequencing

Upgrade modules one at a time, in dependency order, each as its own commit (or PR), and
keep the Docker Compose stack runnable at every step. Because the platform is
version-coupled only *within* a module (every module is a standalone build), mixed states
across modules are acceptable during the rollout — Eureka, Config Server and the gateway
speak stable HTTP protocols to their clients and do not require the clients to be on the
same Spring Cloud train.

| Step | Module | Rationale |
|---|---|---|
| 1 | `internet-banking-service-registry` | Smallest dependency surface (web + actuator + Eureka server), no persistence, no security, no third-party pins. Every other service registers with it, so it must be proven stable first. Validates the Boot 3.5 + SC 2025.0 + Gradle wrapper combination with minimum noise. |
| 2 | `internet-banking-config-server` | Next-smallest surface; serves configuration to all remaining services, so it must be up and compatible before the clients are touched. Exercises Spring Cloud Config Server 4.3 against the external Git config repo. No DB, no security. |
| 3 | `internet-banking-api-gateway` | Highest-risk infrastructure module (WebFlux, Spring Security OAuth2, Gateway artifact rename, external route config) but it has no persistence and no downstream code dependencies of its own. Doing it before the business services means the security and routing risks in §4.2–§4.3 are surfaced while the downstream services are still on the known-good 3.2.4 — so any E2E failure is attributable to the gateway. |
| 4 | `core-banking-service` | First business service; system of record that the other three call via Feign. It has the only real unit tests, plus Flyway + MySQL + springdoc pins, so it is the best module for de-risking §4.4 (Flyway 11, Connector/J 9, springdoc 2.8, H2 2.3). Proving it here gives a template for the remaining three. |
| 5 | `internet-banking-user-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service` | Near-identical build files and dependency sets; all are Feign clients of `core-banking-service`. Once core is green the pattern is mechanical. `user-service` carries the two extra pins (`feign-okhttp`, `keycloak-admin-client`) and should go first within this group; the other two can be done in parallel by different engineers if desired. |

After all seven modules are upgraded: run the full Docker Compose stack, execute the
complete Postman collection, and then (separately) remove any temporary
`spring-boot-properties-migrator` dependencies and address deprecation warnings.

---

## 6. Rollback strategy and per-module verification

### 6.1 Rollback strategy

- **Granularity**: one module per commit/PR. Rolling back is `git revert` of that module's
  commit; because modules are independent builds, no other module is affected.
- **No shared state changes**: the upgrade must not include any Flyway migration scripts or
  schema changes. Flyway 10 → 11 does not alter `flyway_schema_history`, so
  `core-banking-service` can be reverted to 3.2.4/Flyway 10.12 against the same database.
  If any Hibernate DDL difference is observed on the non-Flyway services, capture it and
  treat it as a blocker rather than accepting an implicit schema change.
- **Docker images**: tag the last-known-good `javatodev/*` images before rebuilding
  (e.g. `docker tag javatodev/core-banking-service:latest javatodev/core-banking-service:pre-boot35`)
  so `docker-compose` can be pointed back without a rebuild.
- **External config repo**: if any gateway route / security property is changed there for
  §4.2/§4.3, make it in a separate, tagged commit in that repo so it can be reverted
  independently of this repo.
- **Abort criteria**: a failing `contextLoads()`, any failure in the 18 core-banking unit
  tests, a Postman auth-flow failure at the gateway, or a Flyway validation error against
  the existing MySQL volume is an immediate revert of that module — do not proceed to the
  next step in §5 until it is green.

### 6.2 Per-module verification checklist

Run in each module directory, with JDK 21 on the path
(`export JAVA_HOME=/usr/lib/jvm/jdk-21.0.2+13 && export PATH=$JAVA_HOME/bin:$PATH` on the
provisioned dev box):

1. **Wrapper sanity** — `./gradlew --version` reports the target Gradle version and JVM 21.
2. **Dependency resolution** —
   `./gradlew dependencies --configuration runtimeClasspath > /tmp/deps.txt` and confirm:
   `org.springframework.boot:*` = 3.5.16, `org.springframework.cloud:*` = 4.3.x,
   no leftover 10.x Flyway / 8.4 Connector/J / 2.1.0 springdoc / 13.2.1 Feign unless
   intentionally retained.
3. **Build and tests** — `./gradlew clean build test --no-daemon`. Must be green.
   For `core-banking-service` confirm 18 unit tests + 1 context test ran (check
   `build/reports/tests/test/index.html`).
4. **Image build** — `./gradlew bootJar -x test --no-daemon` then rebuild the module's
   Docker image via the `docker-compose/` flow.
5. **Runtime smoke** — `cd docker-compose && docker compose up -d`, then:
   - `curl http://localhost:<port>/actuator/health` returns `{"status":"UP"}` for the
     upgraded module (config-server 8090, registry 8081, gateway 8082, user 8083,
     fund-transfer 8084, utility-payment 8085, core-banking 8092).
   - Eureka UI at `http://localhost:8081` shows all 5 application services `UP`.
   - For the gateway and every business service: obtain a token from Keycloak
     (`http://localhost:8080/realms/javatodev-internet-banking/protocol/openid-connect/token`,
     client `javatodev-internet-banking-api-client`) and call
     `http://localhost:8082/banking-core/api/v1/user` through the gateway; expect `200`
     with a token and `401` without.
   - Zipkin (`http://localhost:9411`) shows a trace for the request above.
6. **E2E** — run the Postman collection
   `postman_collection/JAVA_TO_DEV_MICROSERVICES.postman_collection.json` with the
   `BANKING_CORE_MICROSERVICES_PROJECT` environment (override the stale `client_id`), either
   in Postman or via `newman run ... -e ...`. All requests must pass. Run this after every
   module in §5, not only at the end, so failures are attributable.
7. **Swagger UI** (business services only) — `http://localhost:<port>/swagger-ui.html`
   renders and `/v3/api-docs` returns the OpenAPI document (validates the springdoc bump).

### 6.3 Final acceptance

All seven modules on Boot 3.5.16 / Spring Cloud 2025.0.3 / Gradle 8.14, all `build test`
runs green, full Postman collection passing against the Docker Compose stack, no
deprecation warnings from `spring-boot-properties-migrator`, and the pre-upgrade image tags
retained until the change has been running in the target environment for an agreed
soak period.
