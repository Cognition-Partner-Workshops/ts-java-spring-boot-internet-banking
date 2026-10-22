# Decomposition Recommendations

## 1. Executive Summary

The current architecture follows a **layered orchestration pattern** where thin orchestrator services (fund-transfer, utility-payment) delegate nearly all business logic to a monolithic core-banking service. This produces the operational overhead of microservices (network hops, distributed failure modes, duplicated code) without the autonomy benefits. The recommendations below restructure the services around **business capabilities** with clear data ownership.

---

## 2. Current Service Dependency Diagram

```
                    ┌──────────────────────┐
                    │   External Clients    │
                    └──────────┬───────────┘
                               │ HTTPS + JWT
                    ┌──────────▼───────────┐
                    │   API Gateway         │
                    │   (OAuth2 + Routing)  │
                    └──┬───────┬────────┬──┘
                       │       │        │
              ┌────────▼──┐ ┌──▼──────┐ ┌▼───────────────┐
              │ User      │ │ Fund    │ │ Utility Payment │
              │ Service   │ │Transfer │ │ Service         │
              │           │ │ Service │ │                 │
              └─────┬─────┘ └────┬────┘ └───────┬─────────┘
                    │ Feign      │ Feign         │ Feign
                    │            │               │
              ┌─────▼────────────▼───────────────▼─────────┐
              │          Core Banking Service               │
              │  ┌──────────┬─────────────┬──────────────┐ │
              │  │ Accounts │ Users (Core)│ Transactions │ │
              │  └──────────┴─────────────┴──────────────┘ │
              └─────────────────────┬──────────────────────┘
                                    │
                              ┌─────▼─────┐
                              │   MySQL    │
                              └───────────┘

Infrastructure:  Service Registry (Eureka) ◄── all services register
                 Config Server ◄── all services fetch config
                 Keycloak ◄── User Service manages, Gateway validates
```

### Current Issues
- **Single point of failure:** Core banking is a bottleneck — if it's down, nothing works
- **Tight coupling:** All business services depend synchronously on core banking via Feign
- **God service:** Core banking owns accounts, users, AND transactions — three distinct domains
- **Thin orchestrators:** Fund-transfer and utility-payment services add minimal value beyond recording a local copy of the transaction
- **Dual writes:** Transaction data is stored in both orchestrator DBs and core banking DB without consistency guarantees

---

## 3. Recommendations

### 3.1 MERGE: Fund Transfer Service + Utility Payment Service → **Payment Service**

**Business Justification:**  
Fund transfers and utility payments are both payment operations with nearly identical lifecycle patterns (accept → validate → execute → record). They share the same infrastructure code (audit, Feign config, filters, exception handling), the same `TransactionStatus` enum, and the same interaction pattern with core banking. Maintaining them as separate services doubles the deployment, monitoring, and maintenance burden for zero business benefit.

**Technical Approach:**
1. Create a unified `payment-service` with two controllers: `/api/v1/transfer` and `/api/v1/utility-payment`
2. Extract a common `PaymentOrchestrator` base that handles the shared lifecycle (persist PENDING → call core → update SUCCESS/FAILED)
3. Eliminate duplicated code: `AuditAware`, `BaseMapper`, filter chain, exception handling, Feign config exist once
4. Single database with tables for both fund transfers and utility payments
5. Update API Gateway routes to point to the merged service

**Impact:** HIGH | **Feasibility:** HIGH (low risk, mostly code consolidation)

---

### 3.2 SPLIT: Core Banking Service → **Account Service** + **Transaction Service**

**Business Justification:**  
Core banking currently bundles three bounded contexts: Account Management, User Data (core), and Transaction Processing. These have different change frequencies, scaling characteristics, and failure domains. Account lookups are read-heavy and cacheable; transaction processing is write-heavy and must be strongly consistent. Splitting them allows independent scaling and clearer data ownership.

**Technical Approach:**

**Account Service** (extracted from core banking):
- Owns `banking_core_account` and `banking_core_utility_account` tables
- Owns `banking_core_user` table (core user data with linked accounts)
- Exposes: `GET /api/v1/account/bank-account/{number}`, `GET /api/v1/account/util-account/{name}`, `GET /api/v1/user/{id}`
- Read-heavy; can be scaled horizontally with read replicas
- Balance reads are eventually consistent for display; authoritative balance is managed by Transaction Service

**Transaction Service** (extracted from core banking):
- Owns `banking_core_transaction` table
- Owns the balance mutation logic (debit/credit operations)
- Exposes: `POST /api/v1/transaction/fund-transfer`, `POST /api/v1/transaction/util-payment`
- Write-heavy; must be strongly consistent
- Communicates with Account Service to validate accounts exist and read current balances
- Applies all payment validation (balance check, amount limits, duplicate detection)

**Impact:** HIGH | **Feasibility:** MEDIUM (requires careful data migration and API contract management)

---

### 3.3 RESTRUCTURE: User Service → Focus on Identity & Access Management

**Business Justification:**  
The user service currently spans two concerns: (1) identity management (Keycloak provisioning, authentication lifecycle) and (2) user profile data (which duplicates core banking user data). It should focus solely on identity and access management, delegating profile data to the Account Service.

**Technical Approach:**
1. Remove the local `banking_user` profile store from user service
2. During registration, user service creates the Keycloak identity and calls Account Service to link the identity to the core user record
3. User service owns: registration flow, Keycloak CRUD, user status (PENDING/APPROVED), `authId` mapping
4. Profile reads (name, email, accounts) are served by Account Service
5. The user-service local DB retains only the identity mapping table (`auth_id` ↔ `core_user_id` ↔ `status`)

**Impact:** MEDIUM | **Feasibility:** MEDIUM (requires careful migration of the registration flow)

---

### 3.4 ADD: Notification Service (new)

**Business Justification:**  
The architecture design specifies a notification service for transaction confirmations and alerts, but it was never implemented. This is a regulatory requirement for most banking jurisdictions (transaction notifications) and a core user experience feature.

**Technical Approach:**
1. Implement the planned RabbitMQ-based notification service
2. Payment Service publishes events on transaction completion (`PAYMENT_COMPLETED`, `PAYMENT_FAILED`)
3. Notification service consumes events and delivers via email, SMS, or push notification
4. Decouples notification delivery from payment processing (async, non-blocking)
5. Supports notification preferences per user

**Impact:** MEDIUM | **Feasibility:** HIGH (greenfield; RabbitMQ already in the tech stack)

---

### 3.5 ADD: Shared Library for Common Code

**Business Justification:**  
The fund-transfer and utility-payment services contain identical copies of 8+ classes (AuditAware, BaseMapper, exception handling, Feign config, filter chain). Even after merging into a Payment Service, the remaining services (Account, Transaction, User) will need these common patterns.

**Technical Approach:**
1. Create a `banking-common` library module (published as a Maven artifact)
2. Extract: `AuditAware`, `BaseMapper`, `ApiRequestContext/Holder`, `AppAuthUserFilter`, `GlobalExceptionHandler`, `ErrorResponse`, `SimpleBankingGlobalException`, `TransactionStatus`
3. All services depend on `banking-common` instead of maintaining their own copies
4. Version the library to allow independent service upgrades

**Impact:** LOW (quality-of-life) | **Feasibility:** HIGH

---

## 4. Proposed Service Dependency Diagram

```
                    ┌──────────────────────┐
                    │   External Clients    │
                    └──────────┬───────────┘
                               │ HTTPS + JWT
                    ┌──────────▼───────────┐
                    │   API Gateway         │
                    │   (OAuth2 + Routing)  │
                    └──┬────┬──────────┬───┘
                       │    │          │
            ┌──────────▼┐ ┌─▼────────┐ ┌▼──────────────┐
            │ Identity  │ │ Payment  │ │ Notification   │
            │ Service   │ │ Service  │ │ Service        │
            │ (Keycloak │ │ (merged) │ │ (new)          │
            │  + IAM)   │ │          │ │                │
            └─────┬─────┘ └──┬───────┘ └───────▲───────┘
                  │           │                  │ RabbitMQ
                  │ Feign     │ Feign            │ events
                  │           │──────────────────┘
            ┌─────▼───────────▼─────────────────────────┐
            │                                           │
            │  ┌───────────────┐  ┌──────────────────┐  │
            │  │ Account       │  │ Transaction      │  │
            │  │ Service       │◄─│ Service          │  │
            │  │ (accounts,    │  │ (payments,       │  │
            │  │  users, util) │  │  balance ops,    │  │
            │  └───────────────┘  │  ledger)         │  │
            │                     └──────────────────┘  │
            └───────────────────────────────────────────┘

Infrastructure:  Service Registry (Eureka) ◄── all services
                 Config Server ◄── all services
                 Keycloak ◄── Identity Service manages, Gateway validates

Shared: banking-common library (audit, DTOs, exceptions, filters)
```

### Key Changes from Current State
| Current | Proposed | Change Type |
|---------|----------|-------------|
| `fund-transfer-service` + `utility-payment-service` | `payment-service` | **MERGE** |
| `core-banking-service` (monolith) | `account-service` + `transaction-service` | **SPLIT** |
| `user-service` (profile + identity) | `identity-service` (identity only) | **RESTRUCTURE** |
| (not implemented) | `notification-service` | **ADD** |
| Duplicated code in every service | `banking-common` library | **ADD** |
| `api-gateway` | `api-gateway` (unchanged) | — |
| `service-registry` | `service-registry` (unchanged) | — |
| `config-server` | `config-server` (unchanged) | — |

---

## 5. Prioritized Roadmap

| Priority | Recommendation | Impact | Feasibility | Effort | Dependencies |
|----------|---------------|--------|-------------|--------|-------------|
| **P1** | 3.1 — Merge fund-transfer + utility-payment → Payment Service | HIGH | HIGH | 2-3 sprints | None |
| **P2** | 3.5 — Extract shared library (`banking-common`) | LOW | HIGH | 1 sprint | Ideally before P1 |
| **P3** | 3.2 — Split core banking → Account Service + Transaction Service | HIGH | MEDIUM | 4-6 sprints | P1 (so Payment Service calls new APIs) |
| **P4** | 3.4 — Add Notification Service | MEDIUM | HIGH | 2-3 sprints | P1 (Payment Service publishes events) |
| **P5** | 3.3 — Restructure User Service → Identity Service | MEDIUM | MEDIUM | 2-3 sprints | P3 (Account Service must own user profiles) |

### Suggested Execution Order

**Phase 1 — Foundation (Sprints 1-3):**
- Extract `banking-common` shared library (P2)
- Merge fund-transfer + utility-payment into Payment Service (P1)
- Add idempotency, amount validation, and duplicate detection to Payment Service (from gap analysis)

**Phase 2 — Core Decomposition (Sprints 4-9):**
- Split core banking into Account Service + Transaction Service (P3)
- Implement saga/outbox pattern between Payment Service and Transaction Service
- Add currency support, structured status codes, end-to-end tracing

**Phase 3 — Capability Expansion (Sprints 10-12):**
- Implement Notification Service with RabbitMQ (P4)
- Restructure User Service into Identity Service (P5)
- Add transaction history / statement APIs to Account Service

---

## 6. Risk Considerations

| Risk | Mitigation |
|------|-----------|
| Data migration during core-banking split | Use blue-green deployment with dual-write period; reconcile before cutover |
| API contract changes break consumers | Version APIs (`/api/v2/`); maintain backward-compatible `/api/v1/` during transition |
| Increased network latency from more service hops | Add caching for account lookups; use async messaging where possible |
| Distributed transaction complexity | Implement outbox pattern with RabbitMQ; use compensating transactions |
| Operational complexity of more services | Invest in observability: structured logging, distributed tracing (Zipkin already in stack), centralized metrics |
| Team coordination | Align service ownership with team boundaries; each team owns 1-2 services |
