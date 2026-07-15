-- Add a creation timestamp to transactions to support ordered, date-filtered statement exports.

ALTER TABLE `banking_core_transaction`
    ADD COLUMN `timestamp` datetime DEFAULT NULL;
