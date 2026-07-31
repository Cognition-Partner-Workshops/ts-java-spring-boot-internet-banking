# Business Capability Map

## 1. Microservice Inventory

| # | Service | Port | Technology | Primary Role |
|---|---------|------|-----------|--------------|
| 1 | `core-banking-service` | 8092 | Spring Boot, JPA, MySQL | Accounts, users, and transaction processing engine |
| 2 | `internet-banking-fund-transfer-service` | 8084 | Spring Boot, JPA, Feign | Fund transfer orchestration and record-keeping |
| 3 | `internet-banking-utility-payment-service` | 8085 | Spring Boot, JPA, Feign | Utility bill payment orchestration and record-keeping |
| 4 | `internet-banking-user-service` | 8083 | Spring Boot, JPA, Feign, Keycloak | Internet banking user registration and management |
| 5 | `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway, OAuth2 | API routing, authentication, and request filtering |
| 6 | `internet-banking-service-registry` | 8081 | Spring Cloud Netflix Eureka | Service discovery and health monitoring |
| 7 | `internet-banking-config-server` | 8090 | Spring Cloud Config | Externalized configuration management |

> The README mentions a **Notification service** (RabbitMQ consumer) but it is not implemented in the codebase.

---

## 2. Business Capability Mapping

### 2.1 Capability-to-Service Matrix

| Business Capability | core-banking | fund-transfer | utility-payment | user-service | api-gateway | service-registry | config-server |
|---------------------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Account Management** | **PRIMARY** | | | | | | |
| **Account Inquiry** | **PRIMARY** | reads via Feign | reads via Feign | | | | |
| **Fund Transfer Processing** | **PRIMARY** | **PRIMARY** | | | | | |
| **Utility Payment Processing** | **PRIMARY** | | **PRIMARY** | | | | |
| **Transaction Recording** | **PRIMARY** | **PRIMARY** | **PRIMARY** | | | | |
| **Balance Management** | **PRIMARY** | | | | | | |
| **User Administration (Core)** | **PRIMARY** | | | reads via Feign | | | |
| **User Registration (IB)** | | | | **PRIMARY** | | | |
| **User Authentication** | | | | **PRIMARY** (Keycloak) | validates JWT | | |
| **User Authorization** | | | | | **PRIMARY** | | |
| **Identity Management** | | | | **PRIMARY** (Keycloak) | | | |
| **API Routing** | | | | | **PRIMARY** | | |
| **Service Discovery** | | | | | | **PRIMARY** | |
| **Configuration Management** | | | | | | | **PRIMARY** |
| **Notification / Messaging** | | | | | | | |
| **Audit Trail** | | has `AuditAware` | has `AuditAware` | | | | |

### 2.2 Detailed Capability Descriptions

#### Account Management
- **Owner:** `core-banking-service`
- **Functions:** Bank account CRUD, utility account lookup, account status management
- **Entities:** `BankAccountEntity` (number, type, status, balances), `UtilityAccountEntity` (provider)
- **APIs:** `GET /api/v1/account/bank-account/{number}`, `GET /api/v1/account/util-account/{name}`

#### Fund Transfer Processing
- **Split across:** `internet-banking-fund-transfer-service` (orchestration) + `core-banking-service` (execution)
- **Orchestrator functions:** Accept request, create PENDING record, delegate to core, update to SUCCESS
- **Core functions:** Validate balance, debit sender, credit receiver, record two transaction entries
- **APIs:** `POST /api/v1/transfer` (fund-transfer service) → `POST /api/v1/transaction/fund-transfer` (core)

#### Utility Payment Processing
- **Split across:** `internet-banking-utility-payment-service` (orchestration) + `core-banking-service` (execution)
- **Orchestrator functions:** Accept request, create PROCESSING record, delegate to core, update to SUCCESS
- **Core functions:** Validate balance, resolve utility provider, debit payer, record transaction
- **APIs:** `POST /api/v1/utility-payment` (utility-payment service) → `POST /api/v1/transaction/util-payment` (core)

#### Transaction Recording
- **Core banking:** Persists `TransactionEntity` with amount, type, reference, and account FK
- **Fund transfer service:** Persists `FundTransferEntity` with status, reference, and account numbers
- **Utility payment service:** Persists `UtilityPaymentEntity` with status, provider, and transaction ID
- **Issue:** Transaction state is duplicated across services with no single source of truth

#### User Administration
- **Core banking:** Manages `banking_core_user` — first name, last name, email, identification number, linked accounts
- **User service:** Manages internet banking users — links to Keycloak, stores `authId`, status (PENDING/APPROVED)
- **User service reads from core** via Feign to validate user identity during registration

#### Authentication & Authorization
- **Keycloak** is the identity provider (OAuth2/OIDC)
- **User service** handles Keycloak user provisioning (create, update, enable/disable)
- **API gateway** validates JWT tokens via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
- **API gateway** injects `X-Auth-Id` header into proxied requests for downstream services

---

## 3. Capability Overlaps

### 3.1 Dual Transaction Recording

**Services:** `core-banking-service` + `fund-transfer-service` + `utility-payment-service`

All three services independently persist transaction records:
- Core banking saves `TransactionEntity` (the authoritative ledger)
- Fund transfer service saves `FundTransferEntity` (its own copy)
- Utility payment service saves `UtilityPaymentEntity` (its own copy)

**Risk:** Data inconsistency if one write succeeds and the other fails. No reconciliation mechanism exists.

### 3.2 Dual User Management

**Services:** `core-banking-service` + `internet-banking-user-service`

User data is stored in two separate databases:
- `banking_core_user` table in core banking (name, email, ID, linked accounts)
- `banking_user` table in user service (name, email, authId, status)

**Risk:** User profile updates in one system are not propagated to the other. Core banking user data is copied into user service at registration time and never synchronized again.

### 3.3 Duplicated DTO/Model Classes

**Services:** All payment-related services

`FundTransferRequest`, `UtilityPaymentRequest`, `FundTransferResponse`, `UtilityPaymentResponse` are defined independently in:
- `internet-banking-fund-transfer-service` (model.dto.request package)
- `internet-banking-utility-payment-service` (model.rest.request package)
- `core-banking-service` (model.dto.request package)

The classes have slightly different structures (e.g., fund-transfer-service's `FundTransferRequest` has `authID`; core-banking's does not).

### 3.4 Duplicated Infrastructure Code

**Services:** `fund-transfer-service` + `utility-payment-service`

Both services contain identical copies of:
- `AuditAware` (mapped superclass with created/modified timestamps)
- `CustomFeignClientConfiguration`
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder`
- `GlobalExceptionHandler` / `ErrorResponse` / `SimpleBankingGlobalException`
- `TransactionStatus` enum
- `BaseMapper` interface

---

## 4. Capability Gaps

### 4.1 Notification & Messaging
**Status:** Not implemented  
**Impact:** The README describes a notification service that consumes RabbitMQ messages, but no such service exists. Neither the fund transfer nor utility payment service publishes messages to any queue.  
**Business Need:** Transaction confirmations, payment alerts, OTP delivery, account activity notifications.

### 4.2 Transaction History & Statement Generation
**Status:** Not implemented  
**Impact:** No API to retrieve a consolidated transaction history for an account. The core banking `TransactionEntity` stores records but there is no read endpoint for transaction history.  
**Business Need:** Account statements, transaction search, export for regulatory reporting.

### 4.3 Payment Scheduling
**Status:** Not implemented  
**Impact:** All payments are immediate. No support for future-dated, recurring, or standing order payments.  
**Business Need:** Scheduled bill payments, recurring transfers, salary disbursements.

### 4.4 Fee & Charge Management
**Status:** Not implemented  
**Impact:** No mechanism to apply transaction fees, interchange fees, or service charges.  
**Business Need:** Revenue generation, regulatory compliance for fee disclosure.

### 4.5 Fraud Detection & Risk Management
**Status:** Not implemented  
**Impact:** No velocity checks, unusual pattern detection, geographic restrictions, or beneficiary whitelisting.  
**Business Need:** Regulatory compliance (AML/CFT), customer protection, loss prevention.

### 4.6 Beneficiary Management
**Status:** Not implemented  
**Impact:** Users must enter account details for every transfer. No saved payee or beneficiary list.  
**Business Need:** Improved UX, reduced errors, payee verification.

### 4.7 Multi-Currency & FX
**Status:** Not implemented  
**Impact:** No currency field on any payload. All amounts are implicitly same-currency.  
**Business Need:** International transfers, currency conversion, FX rate management.

### 4.8 Reconciliation & Settlement
**Status:** Not implemented  
**Impact:** No reconciliation between the three separate transaction stores (core + fund-transfer + utility-payment).  
**Business Need:** Financial integrity, audit compliance, end-of-day balancing.

### 4.9 Reporting & Analytics
**Status:** Not implemented  
**Impact:** No aggregation, dashboards, or regulatory reporting endpoints.  
**Business Need:** Management reporting, regulatory submissions, business intelligence.

---

## 5. Service Boundary Assessment

### 5.1 Current Alignment

| Aspect | Assessment |
|--------|-----------|
| **Business domain alignment** | **Mixed.** Core banking is a monolithic bounded context containing accounts, users, AND transaction processing. The fund-transfer and utility-payment services are thin orchestrators that delegate most work to core banking. |
| **Data ownership** | **Violated.** Transaction data is owned by both the orchestrator services AND core banking. User data is split between user-service and core banking with no clear owner. |
| **Coupling** | **Tight.** Fund-transfer and utility-payment services have synchronous Feign dependencies on core banking. If core banking is down, both services fail completely. |
| **Cohesion** | **Low for core-banking.** It handles accounts, users, AND transactions — three distinct bounded contexts. **Low for orchestrators** — they are thin pass-through layers with minimal business logic. |
| **Independent deployability** | **Partially.** Infrastructure services (gateway, registry, config) can deploy independently. Business services have tight runtime coupling via Feign. |

### 5.2 Boundary Classification

```
TECHNICAL LAYER SPLIT (current):
┌─────────────────────────────────────────────────────────────────────┐
│  API Gateway (routing + auth)                                       │
├─────────────┬─────────────────────┬─────────────────────────────────┤
│ User Service│ Fund Transfer Svc   │ Utility Payment Svc             │
│ (orchestr.) │ (orchestrator)      │ (orchestrator)                  │
├─────────────┴─────────────────────┴─────────────────────────────────┤
│  Core Banking Service (accounts + users + transactions)             │
├─────────────────────────────────────────────────────────────────────┤
│  Service Registry + Config Server (infrastructure)                  │
└─────────────────────────────────────────────────────────────────────┘
```

The current decomposition is primarily by **technical layer** (orchestration vs. execution) rather than by **business capability**. The three orchestrator services are thin proxies to a monolithic core, which means the system has the operational complexity of microservices without the autonomy benefits.
