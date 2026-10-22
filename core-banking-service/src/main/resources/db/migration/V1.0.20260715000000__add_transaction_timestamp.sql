-- Add a timestamp to banking_core_transaction to support period-based statement summaries.

ALTER TABLE `banking_core_transaction`
    ADD COLUMN `transaction_timestamp` datetime DEFAULT NULL;
