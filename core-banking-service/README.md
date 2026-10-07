# core-banking-service

Spring Boot 3.2 / Java 21 / Gradle module. Persistence is **Spring Data MongoDB** (migration
batch `w1-b01`, board UNT5): the four legacy MySQL tables became the collections
`bankingCoreUser`, `bankingCoreAccount`, `bankingCoreUtilityAccount`, `bankingCoreTransaction`
per `.migration/mapping_spec.json` (numeric legacy ids as `_id`, `BigDecimal` as Decimal128,
references by id, no `@DBRef`).

## Build and test

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.2+13
./gradlew -p core-banking-service test   # from the repo root; or cd core-banking-service && ./gradlew test
```

Tests use Testcontainers (`mongo:7`, single-node replica set so multi-document transactions
work); they need a reachable Docker daemon. `ext['testcontainers.version'] = '1.21.4'` in
`build.gradle` is required for Docker Engine >= 29.

## Configuration

The only datastore property is `spring.data.mongodb.uri`. `application.yml` binds it to the
environment variable **`MMP_RT_B1_TARGET_URI`** (the Atlas principal scoped `readWrite` on one
database; the database name is the URI path). Supply it as an environment variable or as a
property override (`--spring.data.mongodb.uri=...`, `SPRING_DATA_MONGODB_URI`). Never commit
the value.

The service still bootstraps from the Spring Cloud config server (`bootstrap*.yml`), which
serves `core-banking-service*.yml` from the external configuration repository. For this module
that repository must:

- remove `spring.datasource.*`, `spring.jpa.*` and `spring.flyway.*` for `core-banking-service`
  (the JDBC driver, JPA and Flyway are no longer on the classpath);
- optionally set `spring.data.mongodb.uri` itself (config-server properties override
  `application.yml`); the value must be a secret reference, not a literal.

Index creation is done by the application at startup (`config/MongoConfig.java`,
`ensureIndex`, idempotent) and by the loader; the `number`, `identificationNumber` and
`providerName` indexes and the matching repository queries use collation
`{locale: "en", strength: 2}` to keep MySQL's case-insensitive lookups.

`src/main/resources/db/migration/*.sql` are kept only as the source DDL/seed referenced by
`.migration/fixtures/w1-b01.json` (fixture rebuild); nothing executes them at runtime.

## One-off data load

`tools/load_core_banking_mongo.py` (run with the recon venv) loads the four tables from the
read-only MySQL principal named by `MMP_RT_SRC_DSN` into the database named on the command
line and allow-listed in `.migration/allowed_targets.json`. It creates the indexes first,
upserts by legacy id (idempotent, never drops) and prints per-field parity and `dbStats`.
