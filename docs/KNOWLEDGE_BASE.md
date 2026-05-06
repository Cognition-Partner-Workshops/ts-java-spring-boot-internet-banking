# Application Knowledge Base

## 1. Architecture Overview

### System Architecture

This is a **Java 21 / Spring Boot 3.2.4** microservices-based internet banking application consisting of 6 services communicating via REST (OpenFeign) and orchestrated through Spring Cloud.

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Clients (Postman/UI)                         │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│              API Gateway (Spring Cloud Gateway) :8082                │
│         OAuth2 Resource Server + JWT Validation (Keycloak)           │
└───────┬──────────────┬──────────────┬──────────────┬────────────────┘
        │              │              │              │
        ▼              ▼              ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐
│ User Service │ │Fund Transfer │ │   Utility    │ │  Core Banking    │
│    :8083     │ │  Service     │ │  Payment Svc │ │   Service :8092  │
│              │ │    :8084     │ │    :8085     │ │                  │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────────────────┘
       │                │                │                    │
       │                └────────────────┼────────────────────┘
       │                                 │         (Feign calls to core)
       ▼                                 ▼
┌──────────────┐                ┌──────────────┐
│   Keycloak   │                │    MySQL     │
│    :8080     │                │    :3306     │
└──────────────┘                └──────────────┘
```

### Services

| Service | Port | Role |
|---------|------|------|
| **internet-banking-config-server** | 8090 | Centralized configuration (Spring Cloud Config backed by Git) |
| **internet-banking-service-registry** | 8081 | Service discovery (Netflix Eureka Server) |
| **internet-banking-api-gateway** | 8082 | API routing, OAuth2/JWT security, header propagation |
| **internet-banking-user-service** | 8083 | User registration, management, Keycloak integration |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer processing, audit trail |
| **internet-banking-utility-payment-service** | 8085 | Utility payment processing |
| **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions |

### Communication Patterns

- **Synchronous REST via OpenFeign**: All inter-service calls use Spring Cloud OpenFeign clients with Eureka-based service discovery.
- **API Gateway Routing**: Spring Cloud Gateway routes requests to downstream services using path-based routing with JWT token validation.
- **Header Propagation**: The gateway extracts the authenticated user's principal name and propagates it as `X-Auth-Id` header to downstream services.
- **Custom Feign Configuration**: Fund Transfer and Utility Payment services use `CustomFeignClientConfiguration` to forward `X-Auth-Id` headers in Feign requests.

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Service Registry | Netflix Eureka | Service discovery and health monitoring |
| Config Server | Spring Cloud Config | Centralized configuration from Git repository |
| API Gateway | Spring Cloud Gateway | Request routing, security, rate limiting |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication, user management |
| Database | MySQL 8.4.0 | Persistent storage for all business services |
| Distributed Tracing | Zipkin 3 | Request tracing across services |
| Tracing Bridge | Micrometer Tracing (Brave) | Trace ID propagation |

---

## 2. Data Model Documentation

### Core Banking Service

#### `banking_core_user`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO) | Primary key |
| first_name | VARCHAR(255) | User's first name |
| last_name | VARCHAR(255) | User's last name |
| email | VARCHAR(255) | User email address |
| identification_number | VARCHAR(255) | National ID / identification number |

#### `banking_core_account`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO) | Primary key |
| number | VARCHAR(255) | Account number (e.g., 100015003000) |
| type | VARCHAR(255) ENUM | SAVINGS_ACCOUNT |
| status | VARCHAR(255) ENUM | ACTIVE |
| actual_balance | DECIMAL(19,2) | Actual account balance |
| available_balance | DECIMAL(19,2) | Available balance |
| user_id | BIGINT (FK → banking_core_user.id) | Account owner |

#### `banking_core_utility_account`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO) | Primary key |
| number | VARCHAR(255) | Utility provider account number |
| provider_name | VARCHAR(255) | Provider name (VODAFONE, VERIZON, etc.) |

#### `banking_core_transaction`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO) | Primary key |
| amount | DECIMAL(19,2) | Transaction amount |
| transaction_type | VARCHAR(30) | FUND_TRANSFER or UTILITY_PAYMENT |
| reference_number | VARCHAR(50) | Reference (destination account or utility ref) |
| transaction_id | VARCHAR(50) | UUID transaction identifier |
| account_id | BIGINT (FK → banking_core_account.id) | Associated account |

### Internet Banking User Service

#### `user`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO) | Primary key |
| auth_id | VARCHAR(255) | Keycloak user ID |
| identification | VARCHAR(255) | National ID linking to core banking |
| status | VARCHAR(255) ENUM | PENDING / APPROVED |
| created_by | VARCHAR(255) | Audit: creator |
| created_date | DATETIME | Audit: creation timestamp |
| last_modified_by | VARCHAR(255) | Audit: last modifier |
| last_modified_date | DATETIME | Audit: last modification timestamp |

### Internet Banking Fund Transfer Service

#### `fund_transfer`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO) | Primary key |
| from_account | VARCHAR(255) | Source account number |
| to_account | VARCHAR(255) | Destination account number |
| amount | DECIMAL(19,2) | Transfer amount |
| transaction_reference | VARCHAR(255) | Core banking transaction ID |
| status | VARCHAR(255) ENUM | PENDING / SUCCESS |
| created_by / created_date / last_modified_by / last_modified_date | Various | Audit fields |

### Internet Banking Utility Payment Service

#### `utility_payment`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO) | Primary key |
| provider_id | BIGINT | Utility provider ID |
| amount | DECIMAL(19,2) | Payment amount |
| reference_number | VARCHAR(255) | Customer reference |
| account | VARCHAR(255) | Source bank account |
| transaction_id | VARCHAR(255) | Core banking transaction ID |
| status | VARCHAR(255) ENUM | PROCESSING / SUCCESS |
| created_by / created_date / last_modified_by / last_modified_date | Various | Audit fields |

### Database Topology

- **4 separate MySQL databases** on a single MySQL instance:
  - `banking_core_service` — Core banking tables
  - `banking_core_user_service` — User service tables
  - `banking_core_fund_transfer_service` — Fund transfer tables
  - `banking_core_utility_payment_service` — Utility payment tables

---

## 3. API Surface Map

### Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | Path: account_number | `BankAccount` (number, type, status, availableBalance, actualBalance) |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider | Path: account_name | `UtilityAccount` (id, number, providerName) |
| GET | `/api/v1/user/{identification}` | Get user by ID number | Path: identification | `User` (firstName, lastName, email, identificationNumber, accounts[]) |
| GET | `/api/v1/user` | List users (paginated) | Query: page, size, sort | `List<User>` |
| POST | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| POST | `/api/v1/transaction/util-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |

### Internet Banking User Service (`:8083`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/bank-users/register` | Register new user | `{firstName, lastName, email, password, identification}` | `User` |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user status | Path: id, Body: `{status}` | `User` |
| GET | `/api/v1/bank-users` | List users (paginated) | Query: page, size, sort | `List<User>` |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | Path: id | `User` |

### Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| GET | `/api/v1/transfer` | List transfers (paginated) | Query: page, size, sort | `List<FundTransfer>` |

### Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| GET | `/api/v1/utility-payment` | List payments (paginated) | Query: page, size, sort | `List<UtilityPayment>` |

### API Gateway Routes (`:8082`)

| Route Prefix | Target Service |
|--------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

---

## 4. Key Business Logic Inventory

### Fund Transfer Rules

1. **Balance Validation**: Before transfer, the system validates `actualBalance >= transferAmount` and `actualBalance > 0`
2. **Atomic Transfer**: Debit from source → Credit to destination in a single `@Transactional` method
3. **Audit Trail**: Two `TransactionEntity` records are created per transfer (debit and credit)
4. **Transaction ID**: UUID generated per transfer, stored in both service databases
5. **Status Tracking**: Fund Transfer service maintains local PENDING → SUCCESS lifecycle
6. **Feign Delegation**: The Fund Transfer service delegates actual balance updates to Core Banking via Feign

### Utility Payment Processing

1. **Balance Validation**: Same as fund transfers (checks available balance)
2. **Provider Validation**: Utility provider must exist in `banking_core_utility_account`
3. **Single Debit**: Only the payer's account is debited (no credit to utility provider in this system)
4. **Status Tracking**: PROCESSING → SUCCESS lifecycle

### User Management

1. **Dual Registration**: User is created both in Keycloak (auth) and local database (profile)
2. **Email Uniqueness**: Checked against Keycloak before registration
3. **Core Banking Validation**: User must exist in core banking (by identification number) before internet banking registration
4. **Email Match**: Email provided at registration must match the one in core banking
5. **Approval Workflow**: User starts as PENDING, admin can APPROVE (enables Keycloak account + email verification)
6. **Keycloak Sync**: On `readUsers()`, user data is enriched with live Keycloak data

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version**: 23.0.7
- **Connection**: User service connects via `keycloak-admin-client:24.0.4`
- **Auth Flow**: Client credentials grant (`client_credentials`)
- **Configuration**: `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret`
- **Operations**: Create user, update user, search by email, read by auth ID
- **Realm Import**: Pre-configured realm exported as JSON, auto-imported on container start
- **Gateway Integration**: JWT tokens validated at gateway using JWK Set URI

### RabbitMQ (Message Broker)

- **Status**: Referenced in README as planned for notification service
- **Current Implementation**: Not yet integrated in code — notification service is marked as "PENDING Development"

### Zipkin (Distributed Tracing)

- **Version**: 3
- **Port**: 9411
- **Integration**: Via `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`
- **Coverage**: All services (API Gateway, User, Fund Transfer, Utility Payment, Core Banking)
- **Trace Propagation**: Automatic via Brave/Micrometer across Feign calls

### Database Connections

- **Engine**: MySQL 8.4.0
- **User**: `javatodev_development` / `oPItyPticIAt`
- **Databases**: 4 separate databases per service (database-per-service pattern)
- **Schema Management**: Flyway migrations (core banking service only)
- **ORM**: Spring Data JPA with Hibernate

### External Configuration

- **Spring Cloud Config Server**: Fetches configuration from `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Search Path**: `configuration/`
- **Profile Activation**: Docker profile via `-Dspring.profiles.active=docker`

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool**: Gradle (per-service `build.gradle`, no multi-project build)
- **Java Version**: 21 (Eclipse Temurin 21.0.2_13-jre-alpine in Docker)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- **Plugins**: `spring-boot`, `dependency-management`, `gradle-git-properties`

### Docker Deployment

- **Base Image**: `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern**: Fat JAR copied into container
- **Startup**: `wait-for-it.sh` script ensures dependency ordering (registry → config → mysql → app)
- **Network**: Custom bridge network (`172.25.0.0/16`) with fixed IP addresses

### Docker Compose

Two compose files:
1. `docker-compose.yml` — Full stack (all services + infrastructure)
2. `docker-compose-support-apps.yml` — Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Registry)

### Container Startup Order

```
MySQL → Keycloak DB → Keycloak
            ↓
Config Server → Service Registry → API Gateway → Business Services
```

### CI/CD

- No CI pipeline files detected (no `.github/workflows/`, no `Jenkinsfile`, no `gitlab-ci.yml`)
- Build is manual: `./gradlew build` per service, then `docker build`
