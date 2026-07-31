# Payment Payload Gap Analysis

## 1. Executive Summary

This document assesses the current payment payload structures in the Internet Banking microservices against ISO 20022 payment message standards. The analysis reveals significant gaps in standards compliance, validation, and operational safety — the payloads are minimal proof-of-concept structures that lack the mandatory fields, error handling, and security controls required for production payment processing.

---

## 2. Current Payment Payload Structures

### 2.1 Fund Transfer — Request

**Service:** `internet-banking-fund-transfer-service`  
**Endpoint:** `POST /api/v1/transfer`  
**Class:** `FundTransferRequest`

| Field | Type | Description |
|-------|------|-------------|
| `fromAccount` | `String` | Source account number |
| `toAccount` | `String` | Destination account number |
| `amount` | `BigDecimal` | Transfer amount |
| `authID` | `String` | Authentication/authorization ID |

> Note: The core-banking-service has its own `FundTransferRequest` that omits `authID`.

### 2.2 Fund Transfer — Response

**Class:** `FundTransferResponse`

| Field | Type | Description |
|-------|------|-------------|
| `message` | `String` | Status message (e.g., "Fund Transfer Successfully Completed") |
| `transactionId` | `String` | UUID-based transaction reference |

### 2.3 Utility Payment — Request

**Service:** `internet-banking-utility-payment-service`  
**Endpoint:** `POST /api/v1/utility-payment`  
**Class:** `UtilityPaymentRequest`

| Field | Type | Description |
|-------|------|-------------|
| `providerId` | `Long` | Utility provider identifier |
| `amount` | `BigDecimal` | Payment amount |
| `referenceNumber` | `String` | Customer reference / bill reference |
| `account` | `String` | Payer's bank account number |

### 2.4 Utility Payment — Response

**Class:** `UtilityPaymentResponse`

| Field | Type | Description |
|-------|------|-------------|
| `message` | `String` | Status message (e.g., "Utility Payment Successfully Processed") |
| `transactionId` | `String` | UUID-based transaction reference |

### 2.5 Core Banking Transaction Entity

**Class:** `TransactionEntity` (persisted in `banking_core_transaction`)

| Field | Type | Description |
|-------|------|-------------|
| `id` | `Long` | Auto-generated primary key |
| `amount` | `BigDecimal` | Transaction amount (negative for debits) |
| `transactionType` | `TransactionType` | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| `referenceNumber` | `String` | Counter-party account or bill reference |
| `transactionId` | `String` | UUID correlation ID |
| `account` | `BankAccountEntity` | Associated bank account (FK) |

---

## 3. ISO 20022 Comparison

### 3.1 pain.001 — Customer Credit Transfer Initiation

This is the standard message a customer sends to their bank to initiate a payment.

| ISO 20022 Field | Status | Current Mapping | Gap Description |
|-----------------|--------|-----------------|-----------------|
| `MsgId` (Message Identification) | **MISSING** | None | No message-level unique identifier for the initiation request |
| `CreDtTm` (Creation Date Time) | **MISSING** | None | No timestamp on payment request |
| `NbOfTxs` (Number of Transactions) | **MISSING** | None | No batch support — only single transfers |
| `CtrlSum` (Control Sum) | **MISSING** | None | No checksum for batch integrity |
| `InitgPty` (Initiating Party) | **MISSING** | `authID` (partial) | `authID` is a token, not a structured party identifier (Name, ID, Address) |
| `PmtInfId` (Payment Information ID) | **MISSING** | None | No payment information block grouping |
| `PmtMtd` (Payment Method) | **MISSING** | None | Assumed credit transfer; not explicit |
| `BtchBookg` (Batch Booking) | **MISSING** | None | No batch booking preference |
| `ReqdExctnDt` (Requested Execution Date) | **MISSING** | None | All transfers are immediate; no scheduled payment support |
| `Dbtr` (Debtor) | **PARTIAL** | `fromAccount` | Only account number; no debtor name, address, or structured ID |
| `DbtrAcct` (Debtor Account) | **PARTIAL** | `fromAccount` | Plain string; no IBAN format, no currency, no account type |
| `DbtrAgt` (Debtor Agent / Bank) | **MISSING** | None | No BIC/SWIFT code or bank identification |
| `CdtTrfTxInf.PmtId.EndToEndId` | **MISSING** | None | No end-to-end transaction ID from originator |
| `CdtTrfTxInf.PmtId.InstrId` | **MISSING** | None | No instruction identification |
| `CdtTrfTxInf.Amt.InstdAmt` | **PRESENT** | `amount` | Amount present but lacks currency code (assumes single currency) |
| `CdtTrfTxInf.Amt.InstdAmt@Ccy` | **MISSING** | None | No currency attribute — cannot support multi-currency |
| `CdtrAgt` (Creditor Agent / Bank) | **MISSING** | None | No beneficiary bank identification |
| `Cdtr` (Creditor) | **PARTIAL** | `toAccount` | Only account number; no creditor name or address |
| `CdtrAcct` (Creditor Account) | **PARTIAL** | `toAccount` | Plain string; no IBAN, no account type metadata |
| `RmtInf` (Remittance Information) | **MISSING** | None | No payment description, invoice reference, or structured remittance |
| `Purp` (Purpose) | **MISSING** | None | No purpose code (e.g., salary, invoice, rent) |
| `ChrgBr` (Charge Bearer) | **MISSING** | None | No indication of who bears transfer charges |

### 3.2 pain.002 — Customer Payment Status Report

This is the response message sent back to the customer indicating the status of their payment.

| ISO 20022 Field | Status | Current Mapping | Gap Description |
|-----------------|--------|-----------------|-----------------|
| `MsgId` | **MISSING** | None | No message ID on response |
| `CreDtTm` | **MISSING** | None | No response timestamp |
| `OrgnlMsgId` (Original Message ID) | **MISSING** | None | No correlation to original request message |
| `OrgnlMsgNmId` (Original Message Name) | **MISSING** | None | Cannot identify original message type |
| `GrpSts` (Group Status) | **PARTIAL** | `message` | Free-text message instead of structured status code |
| `TxInfAndSts.StsId` | **MISSING** | None | No structured status identification |
| `TxInfAndSts.OrgnlEndToEndId` | **MISSING** | None | No end-to-end ID echo |
| `TxInfAndSts.TxSts` | **PARTIAL** | `message` | `"Fund Transfer Successfully Completed"` — not a coded status (e.g., ACSP, RJCT) |
| `TxInfAndSts.StsRsnInf` | **MISSING** | None | No structured reason code for failures |
| `OrgnlTxRef` (Original Transaction Reference) | **PARTIAL** | `transactionId` | UUID returned but not structured per ISO |

### 3.3 pacs.008 — FI to FI Customer Credit Transfer

This is the inter-bank message. The core banking service's internal transfer logic partially maps to this.

| ISO 20022 Field | Status | Current Mapping | Gap Description |
|-----------------|--------|-----------------|-----------------|
| `GrpHdr.MsgId` | **PARTIAL** | `transactionId` (UUID) | UUID generated but not in ISO format |
| `GrpHdr.CreDtTm` | **MISSING** | None | No creation timestamp on transaction record |
| `GrpHdr.NbOfTxs` | **MISSING** | None | Always single transaction |
| `GrpHdr.SttlmInf` | **MISSING** | None | No settlement method specified |
| `GrpHdr.InstgAgt` | **MISSING** | None | No instructing FI identification |
| `GrpHdr.InstdAgt` | **MISSING** | None | No instructed FI identification |
| `CdtTrfTxInf.PmtId.InstrId` | **MISSING** | None | No instruction ID |
| `CdtTrfTxInf.PmtId.EndToEndId` | **MISSING** | None | No end-to-end ID |
| `CdtTrfTxInf.PmtId.TxId` | **PARTIAL** | `transactionId` | UUID used as transaction ID |
| `CdtTrfTxInf.IntrBkSttlmAmt` | **PRESENT** | `amount` | Amount present but no currency |
| `CdtTrfTxInf.IntrBkSttlmDt` | **MISSING** | None | No settlement date |
| `CdtTrfTxInf.ChrgBr` | **MISSING** | None | No charge bearer |
| `DbtrAgt` | **MISSING** | None | No debtor bank BIC |
| `CdtrAgt` | **MISSING** | None | No creditor bank BIC |
| `Dbtr` | **PARTIAL** | Account entity has `user` FK | Name derivable but not in message |
| `Cdtr` | **MISSING** | None | No creditor party info in message |

---

## 4. Payment Validation Assessment

### 4.1 Amount Limits

| Check | Implemented? | Details |
|-------|-------------|---------|
| Minimum amount (> 0) | **NO** | No validation that `amount > 0`. Negative or zero amounts accepted |
| Maximum single-transaction limit | **NO** | No per-transaction cap enforced |
| Daily cumulative limit | **NO** | No daily transfer limit per account or user |
| Monthly limit | **NO** | No monthly aggregate limit |

**Severity: CRITICAL** — A user could initiate transfers of any magnitude including negative amounts.

### 4.2 Currency Handling

| Check | Implemented? | Details |
|-------|-------------|---------|
| Currency code on request | **NO** | No currency field anywhere in the payload |
| Multi-currency support | **NO** | Implicitly single-currency; no FX rate handling |
| Currency mismatch detection | **NO** | Cannot detect because currency is not tracked |

**Severity: HIGH** — Cannot support multi-currency operations or regulatory currency reporting.

### 4.3 Duplicate Detection

| Check | Implemented? | Details |
|-------|-------------|---------|
| Idempotency key on request | **NO** | No client-supplied idempotency token |
| Duplicate transfer detection | **NO** | Same `fromAccount`/`toAccount`/`amount` can be submitted repeatedly |
| Request deduplication window | **NO** | No time-window-based dedup logic |

**Severity: CRITICAL** — Network retries or user double-clicks will create duplicate transfers.

### 4.4 Idempotency

| Check | Implemented? | Details |
|-------|-------------|---------|
| Idempotency-Key header | **NO** | Not checked or required |
| Request hash dedup | **NO** | No request fingerprinting |
| Safe retry semantics | **NO** | POST endpoint is not idempotent; every call creates a new transfer |

**Severity: CRITICAL** — The fund transfer service saves a PENDING record and then calls core banking synchronously. If the Feign call succeeds but the response is lost, the client retries and creates a second transfer.

### 4.5 Account Validation

| Check | Implemented? | Details |
|-------|-------------|---------|
| Account existence check | **YES** | Core banking throws `EntityNotFoundException` for unknown accounts |
| Account status check | **NO** | Dormant or blocked accounts can still send/receive transfers |
| Self-transfer prevention | **NO** | `fromAccount == toAccount` is not blocked |
| Account ownership verification | **NO** | Any authenticated user can transfer from any account |

**Severity: HIGH** — Account ownership is not enforced at the service level.

### 4.6 Transaction Atomicity

| Check | Implemented? | Details |
|-------|-------------|---------|
| Database transaction boundary | **PARTIAL** | Core banking `TransactionService` is `@Transactional`, but the fund-transfer service is NOT |
| Compensating transaction | **NO** | If core banking call succeeds but local DB save fails, the transfer completes but the fund-transfer service record is lost |
| Saga / outbox pattern | **NO** | No distributed transaction coordination |

**Severity: HIGH** — The fund-transfer and utility-payment services call core banking via Feign inside a non-transactional flow. Partial failures leave inconsistent state.

---

## 5. Gap Severity Summary

| # | Gap | Severity | Business Risk |
|---|-----|----------|---------------|
| 1 | No idempotency / duplicate detection | **CRITICAL** | Duplicate payments; financial loss |
| 2 | No amount validation (min/max/negative) | **CRITICAL** | Unlimited or negative transfers |
| 3 | No account ownership verification | **HIGH** | Unauthorized fund transfers |
| 4 | No currency code in payload | **HIGH** | Cannot support multi-currency; regulatory non-compliance |
| 5 | Missing ISO 20022 party identifiers (Debtor/Creditor name, address, BIC) | **HIGH** | Cannot generate regulatory reports; no STP with correspondent banks |
| 6 | No remittance information | **MEDIUM** | Cannot attach invoice/reference data for reconciliation |
| 7 | No end-to-end transaction tracing ID | **HIGH** | Cannot trace payment lifecycle across services |
| 8 | No scheduled/future-dated payment support | **MEDIUM** | Missing standard banking feature |
| 9 | No structured status codes (pain.002) | **MEDIUM** | Clients cannot programmatically interpret payment outcomes |
| 10 | No settlement date or value date | **MEDIUM** | No cut-off time handling or T+1 settlement |
| 11 | Non-atomic distributed transaction flow | **HIGH** | Inconsistent state between services on partial failure |
| 12 | No charge bearer / fee handling | **LOW** | Cannot distribute transfer fees between parties |
| 13 | No batch payment support | **LOW** | Cannot process salary runs or bulk payments |
| 14 | Account status not validated (dormant/blocked) | **HIGH** | Payments from/to suspended accounts are allowed |
| 15 | No purpose code | **LOW** | Cannot categorize payments for regulatory or analytics purposes |
| 16 | `authID` field not used in core banking flow | **MEDIUM** | The fund-transfer request carries `authID` but it is never validated or forwarded to core banking |

---

## 6. Recommendations Priority

1. **Immediate (Sprint 1):** Add amount validation (positive, max limit), duplicate detection via idempotency key, and account status checks.
2. **Short-term (Sprint 2-3):** Add currency support, structured ISO status codes, end-to-end ID tracing, and account ownership verification.
3. **Medium-term (Sprint 4-6):** Implement saga/outbox pattern for distributed transaction safety, add remittance information fields, and support scheduled payments.
4. **Long-term:** Full ISO 20022 pain.001/pain.002/pacs.008 message structure adoption for interoperability with external payment networks.
