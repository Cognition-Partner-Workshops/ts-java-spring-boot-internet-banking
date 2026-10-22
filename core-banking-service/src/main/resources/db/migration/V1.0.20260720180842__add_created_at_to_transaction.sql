-- Add created_at timestamp to banking_core_transaction to support
-- transaction history / statement ordering and date-range filtering.

ALTER TABLE `banking_core_transaction`
    ADD COLUMN `created_at` datetime DEFAULT NULL;
