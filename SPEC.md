# SPEC — Switch persistence from MySQL to PostgreSQL

## Context

The internet-banking microservices system currently persists relational data in
**MySQL 8.4**. Four services carry a JDBC/JPA persistence layer and depend on the
MySQL driver:

- `core-banking-service` — owns the schema; uses Flyway migrations
  (`flyway-core` + `flyway-mysql`) and `mysql-connector-j`.
- `internet-banking-user-service` — `mysql-connector-j`.
- `internet-banking-fund-transfer-service` — `mysql-connector-j`.
- `internet-banking-utility-payment-service` — `mysql-connector-j`.

MySQL is provisioned for local/integration runs by `docker-compose/` (service
`mysql_core_db`, built from `docker-compose/mysql/`). Keycloak already runs on
PostgreSQL, so PostgreSQL is a familiar, already-vetted engine in this stack.

The **runtime datasource URL, driver class, and Hibernate dialect are NOT stored
in this repository** — they are served by the Spring Cloud Config Server from the
external git repo
`https://github.com/JavatoDev-com/internet-banking-microservices-configurations`
(see each module's `bootstrap*.yml` and the config server's `application.yml`).
This SPEC covers everything owned by *this* repo; the external config change is
called out as a required companion step (see DESIGN.md §"External configuration").

## Goal

Replace MySQL with PostgreSQL as the relational database for the banking
microservices, keeping the existing schema, seed data, and application behavior
unchanged.

## User stories

1. **As an operator**, I can start the stack with `docker-compose up` and get a
   PostgreSQL instance (instead of MySQL) that hosts the banking databases, so no
   MySQL container is required.
2. **As a developer**, the services compile and package against the PostgreSQL
   JDBC driver (no MySQL connector on the classpath).
3. **As the core-banking service**, Flyway applies the baseline schema and seed
   data successfully against PostgreSQL on a fresh database.
4. **As a maintainer**, the documentation (README, tech stack) reflects
   PostgreSQL rather than MySQL.

## Acceptance criteria (testable)

- **AC1 — No MySQL on the classpath.** No `build.gradle` in the repo references
  `com.mysql:mysql-connector-j` or `org.flywaydb:flyway-mysql`. The PostgreSQL
  driver (`org.postgresql:postgresql`) and, for `core-banking-service`,
  `org.flywaydb:flyway-database-postgresql` are present instead.
- **AC2 — All affected modules build green.** `./gradlew build` succeeds for
  `core-banking-service`, `internet-banking-user-service`,
  `internet-banking-fund-transfer-service`, and
  `internet-banking-utility-payment-service` (unit tests use in-memory H2 and
  remain unchanged and passing).
- **AC3 — Migrations are PostgreSQL-valid.** The three Flyway migration files in
  `core-banking-service/src/main/resources/db/migration/` use PostgreSQL syntax
  (no MySQL backtick quoting, no `AUTO_INCREMENT`, no inline `KEY`, no
  cross-database `banking_core_service.` qualifiers) and apply cleanly on a fresh
  PostgreSQL 15 instance, creating tables `banking_core_user`,
  `banking_core_account`, `banking_core_utility_account`,
  `banking_core_transaction` with the same columns/constraints and the same seed
  rows as before.
- **AC4 — Compose provisions PostgreSQL.** `docker-compose/docker-compose.yml`
  and `docker-compose/docker-compose-support-apps.yml` define a PostgreSQL
  database service (not `mysql_core_db`) exposing port 5432 and creating the four
  application databases; every `wait-for-it.sh` dependency that pointed at
  `mysql_core_db:3306` now points at the PostgreSQL service on `:5432`.
- **AC5 — Docs updated.** README container table and technology stack list
  reference PostgreSQL instead of MySQL.

## Out of scope / non-goals

- Data migration of existing MySQL data into PostgreSQL (dev stack seeds fresh via
  Flyway/init scripts).
- Changing table names, column types, or any application/API behavior.
- The external configuration repo change (documented, but not editable here).
