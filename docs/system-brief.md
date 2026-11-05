# System Brief: Internet Banking Platform

*Audience: CTO / board. This brief covers only what is in this repository; inferences are labelled **(inference)**.*

## Purpose
This is a reference ("concept") internet-banking backend. It came from a tutorial series ([README.md](../README.md#L1-L5)). Customers can register, view accounts, transfer money between accounts and pay utility bills. A simulated "core banking" ledger sits underneath ([README.md](../README.md#L67-L75)). A notification service is described but marked "PENDING Development" ([README.md](../README.md#L74)). **(Inference)** Treat it as a demonstrator. It is not a production system: the bundled ledger is a "dummy banking core" ([README.md](../README.md#L75)).

## Architecture
- **Seven Spring Boot microservices**, all in Java 21 / Spring Boot 3.2.4, each built with Gradle ([core-banking-service/build.gradle](../core-banking-service/build.gradle#L3-L17)):
  - API gateway ([SecurityConfiguration.java](../internet-banking-api-gateway/src/main/java/com/javatodev/finance/configuration/security/SecurityConfiguration.java))
  - Eureka service registry ([build.gradle](../internet-banking-service-registry/build.gradle#L25))
  - Config server ([application.yml](../internet-banking-config-server/src/main/resources/application.yml))
  - User, fund-transfer and utility-payment services
  - Core banking ledger ([TransactionService.java](../core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java))
- **Service-to-service calls** use OpenFeign over Eureka discovery ([BankingCoreFeignClient.java](../internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/rest/client/BankingCoreFeignClient.java#L14-L21)).
- **Tracing** uses Micrometer/Brave reporting to Zipkin ([build.gradle](../core-banking-service/build.gradle#L34-L37)).
- **Deployment:** one Docker Compose file on a single bridge network with fixed IPs ([docker-compose.yml](../docker-compose/docker-compose.yml#L134-L141)). Keycloak runs in `start-dev` mode ([docker-compose.yml](../docker-compose/docker-compose.yml#L24)). There are no Kubernetes manifests, although the README lists Kubernetes ([README.md](../README.md#L100)). There is no CI pipeline: `.github/` holds only [FUNDING.yml](../.github/FUNDING.yml).

## Data stores
- **MySQL 8.4** holds the core-banking data, with a Flyway-managed schema ([mysql/Dockerfile](../docker-compose/mysql/Dockerfile#L1), [build.gradle](../core-banking-service/build.gradle#L50-L52)):
  - Tables: users, accounts, utility accounts and transactions ([V1…base_table_structure.sql](../core-banking-service/src/main/resources/db/migration/V1.0.20210427174638__create_base_table_structure.sql#L3-L29), [V1…transaction_table.sql](../core-banking-service/src/main/resources/db/migration/V1.0.20210429210839__create_transaction_table.sql#L3))
  - Seed data ships as a migration ([temp_data.sql](../core-banking-service/src/main/resources/db/migration/V1.0.20210427174721__temp_data.sql))
  - The user, fund-transfer and payment services also use MySQL connectors ([build.gradle](../internet-banking-fund-transfer-service/build.gradle#L47))
- **PostgreSQL 15** is Keycloak's identity store ([docker-compose.yml](../docker-compose/docker-compose.yml#L35-L44)).
- **(Inference)** Datasource settings are not in this repo. They load at runtime from the external config repository (see Integrations).

## Integrations
- **Inbound:** REST through the gateway. Clients need a Keycloak-issued JWT, checked via JWKS. Two path groups are open without a token: user registration and actuator endpoints ([SecurityConfiguration.java](../internet-banking-api-gateway/src/main/java/com/javatodev/finance/configuration/security/SecurityConfiguration.java#L24-L37)).
- **Identity propagation:** the gateway passes the caller's identity to downstream services in an `X-Auth-Id` header. If there is no principal, it sends "SYSTEM USER" ([GatewayConfiguration.java](../internet-banking-api-gateway/src/main/java/com/javatodev/finance/configuration/GatewayConfiguration.java#L14-L23)). Downstream services accept this header as-is ([AppAuthUserFilter.java](../internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/filter/AppAuthUserFilter.java#L23-L27)).
- **Outbound:**
  - The user service manages accounts in Keycloak through the Admin Client ([build.gradle](../internet-banking-user-service/build.gradle#L46)).
  - All services pull configuration from a third-party public GitHub repo ([config-server application.yml](../internet-banking-config-server/src/main/resources/application.yml#L8)).
- **Secrets and config:** these live in the Keycloak realm import ([realm-export.json](../docker-compose/keycloak/realm-export.json)), the Compose environment ([docker-compose.yml](../docker-compose/docker-compose.yml#L21-L23)), the MySQL image ([mysql/Dockerfile](../docker-compose/mysql/Dockerfile#L2)) and the README ([README.md](../README.md#L62)). Values are not reproduced here.

## Top 3 operational risks

**1. Security perimeter depends on one gateway, and credentials are committed. Severity: High**
- **Why it matters:** Downstream services have no Spring Security dependency ([user-service build.gradle](../internet-banking-user-service/build.gradle)). They trust `X-Auth-Id` as sent ([AppAuthUserFilter.java](../internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/filter/AppAuthUserFilter.java#L23-L27)), and their ports are published directly on the host ([docker-compose.yml](../docker-compose/docker-compose.yml#L92-L123)). Anyone who can reach those ports could move money while posing as any user, with no token check. Client secrets and passwords sit in version control (files listed above).
- **Mitigation:** Close the service ports. Require JWT validation or mTLS on every service. Rotate the committed secrets and move them to a vault.

**2. Ledger integrity (money movement). Severity: High**
- **Why it matters:** "Available balance" subtracts the amount from the *already-updated* actual balance, so it counts each debit twice ([TransactionService.java](../core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java#L90-L91), [L99-L100](../core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java#L99-L100)). Account rows have no optimistic or pessimistic locking; `@Version` exists only in the edge services' audit base ([AuditAware.java](../internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/model/dto/AuditAware.java#L42)). **(Inference)** Two concurrent transfers could overdraw an account. A transfer is written as PENDING, then the core is called with no compensation or idempotency step ([FundTransferService.java](../internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/FundTransferService.java#L36-L42)). Any failure leaves records that need manual reconciliation.
- **Mitigation:** Fix the balance formula. Add row locking or versioning on accounts, plus idempotency keys and a reconciliation job.

**3. Delivery and operability maturity. Severity: Medium**
- **Why it matters:**
  - Runtime config depends on a third-party GitHub repo outside the organisation's control ([application.yml](../internet-banking-config-server/src/main/resources/application.yml#L8)). That is a supply-chain risk and a single point of failure.
  - There is no CI ([.github/](../.github/FUNDING.yml)).
  - Only core-banking has real unit tests ([TransactionServiceTest.java](../core-banking-service/src/test/java/com/javatodev/finance/service/TransactionServiceTest.java)). The other services have only context-load tests ([e.g.](../internet-banking-fund-transfer-service/src/test/java/com/javatodev/finance/InternetBankingFundTransferServiceApplicationTests.java)).
  - Hosting is single-host Compose with Keycloak in dev mode ([docker-compose.yml](../docker-compose/docker-compose.yml#L24)).
- **Mitigation:** Fork the config into an internal repo, add CI with tests, and plan an HA deployment (for example Kubernetes, with Keycloak in production mode).
