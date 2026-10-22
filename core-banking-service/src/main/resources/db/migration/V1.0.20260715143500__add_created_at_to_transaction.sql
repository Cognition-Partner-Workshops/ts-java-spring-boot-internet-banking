-- add creation timestamp to support account statement / transaction history

ALTER TABLE `banking_core_transaction`
    ADD COLUMN `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
