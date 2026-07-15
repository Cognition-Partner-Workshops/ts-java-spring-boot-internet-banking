-- Add a time dimension to the transaction ledger to support point-in-time balances.

ALTER TABLE `banking_core_transaction`
    ADD COLUMN `timestamp` datetime DEFAULT NULL;
