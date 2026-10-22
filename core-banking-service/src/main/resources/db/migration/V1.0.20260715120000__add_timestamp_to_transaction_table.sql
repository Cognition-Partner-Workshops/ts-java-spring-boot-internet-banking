-- Add a timestamp column to support account transaction history
-- (ordering most-recent-first and inclusive date-period filtering).

ALTER TABLE `banking_core_transaction`
    ADD COLUMN `timestamp` datetime DEFAULT NULL;
