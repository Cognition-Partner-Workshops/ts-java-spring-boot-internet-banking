# DESIGN — MySQL → PostgreSQL cutover

Implements SPEC.md. Changes are grouped by concern; each maps to acceptance
criteria in the SPEC.

## 1. Dependencies (AC1)

Driver version: `org.postgresql:postgresql:42.7.3` (PostgreSQL 42.7.x is the
current 42.x line; published well over a week ago). Flyway PostgreSQL support:
`org.flywaydb:flyway-database-postgresql:10.12.0` (matches the existing
`flyway-core` version).

| Module | Remove | Add |
| --- | --- | --- |
| `core-banking-service` | `org.flywaydb:flyway-mysql:10.12.0`, `com.mysql:mysql-connector-j:8.4.0` | `org.flywaydb:flyway-database-postgresql:10.12.0`, `org.postgresql:postgresql:42.7.3` |
| `internet-banking-user-service` | `com.mysql:mysql-connector-j:8.4.0` | `org.postgresql:postgresql:42.7.3` |
| `internet-banking-fund-transfer-service` | `com.mysql:mysql-connector-j:8.4.0` | `org.postgresql:postgresql:42.7.3` |
| `internet-banking-utility-payment-service` | `com.mysql:mysql-connector-j:8.4.0` | `org.postgresql:postgresql:42.7.3` |

## 2. Flyway migrations (AC3)

Files in `core-banking-service/src/main/resources/db/migration/` are the baseline
schema. They contain MySQL-only syntax that PostgreSQL rejects. Because these
migrations have never run against PostgreSQL (a fresh engine gets a fresh Flyway
history), the baseline files are rewritten in place to portable PostgreSQL SQL
rather than adding a "fix" migration — the net effect on a fresh PostgreSQL DB is
identical, and no MySQL history exists to preserve.

Transformations applied:

- Identifier quoting: drop MySQL backticks. Identifiers are already lowercase, so
  they need no quoting in PostgreSQL. `number`, `type`, `status` are
  non-reserved and remain unquoted.
- Auto-increment PK: `bigint(20) NOT NULL AUTO_INCREMENT` → `BIGSERIAL`
  (PostgreSQL accepts explicit id values in the seed inserts, matching current
  behavior).
- Integer widths: `bigint(20)` → `bigint` (display width is a MySQL-ism).
- Indexes: MySQL inline `KEY <name>(col)` → separate `CREATE INDEX <name> ON ...`
  statements. Foreign keys keep the same constraint names via
  `CONSTRAINT <name> FOREIGN KEY ... REFERENCES ...`.
- Cross-database qualifiers: `INSERT INTO banking_core_service.banking_core_user`
  → `INSERT INTO banking_core_user` (in PostgreSQL each app database is a
  separate database, not a schema prefix).

Resulting tables/columns/constraints and all seed rows are unchanged.

## 3. Local infrastructure — docker-compose (AC4)

Replace the `mysql_core_db` service (in both `docker-compose.yml` and
`docker-compose-support-apps.yml`) with `postgres_core_db`:

```yaml
postgres_core_db:
  build: postgres            # new docker-compose/postgres/ image
  container_name: postgres_javatodev_app
  environment:
    POSTGRES_USER: root
    POSTGRES_PASSWORD: woVERANKliGharym
  ports:
    - 5432:5432
  volumes:
    - pgdata:/var/lib/postgresql/data
  networks:
    javatodev_ib_network:
      ipv4_address: 172.25.0.9   # IP unchanged
```

- New volume `pgdata` (replaces `mysqldata`).
- `docker-compose/postgres/`: `Dockerfile` (`FROM postgres:15`, copies init SQL to
  `/docker-entrypoint-initdb.d/`) and `init.sql` that creates the
  `javatodev_development` app user and the four databases
  (`banking_core_service`, `banking_core_fund_transfer_service`,
  `banking_core_user_service`, `banking_core_utility_payment_service`) — the
  PostgreSQL equivalent of the old `docker-compose/mysql/privileges.sql`. The old
  `docker-compose/mysql/` directory is removed.
- Every service `entrypoint` `wait-for-it.sh ... mysql_core_db:3306 ...` becomes
  `postgres_core_db:5432`.

## 4. Documentation (AC5)

README: container table row `mysql_javatodev_app | ... | 3306` →
`postgres_javatodev_app | ... | 5432`; technology-stack item `MySQL` →
`PostgreSQL`.

## 5. External configuration (companion change — NOT in this repo)

The live datasource is served by the config server from
`JavatoDev-com/internet-banking-microservices-configurations`
(`configuration/` path). For the cutover to take effect at runtime, that repo's
per-service YAML must change, e.g.:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres_core_db:5432/banking_core_service
    username: javatodev_development
    password: oPItyPticIAt
    driver-class-name: org.postgresql.Driver
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
```

(analogous URLs/databases for the user, fund-transfer, and utility-payment
services). This repo cannot modify that external repo; the change is listed here
and in the PR description as a required follow-up so the delivery lead can action
it.

## 6. Verification (AC2, AC3)

- `./gradlew build` for the four affected modules (unit tests use H2 with Flyway
  disabled, so they are engine-agnostic and unchanged).
- Migrations validated against a real PostgreSQL 15 container: apply the three
  rewritten files and confirm the four tables and seed row counts.
