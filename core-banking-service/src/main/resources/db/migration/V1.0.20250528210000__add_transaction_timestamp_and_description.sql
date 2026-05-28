-- Add created_at timestamp and description columns to banking_core_transaction

ALTER TABLE `banking_core_transaction`
    ADD COLUMN `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN `description` VARCHAR(255) DEFAULT NULL;

-- Backfill existing rows with current timestamp
UPDATE `banking_core_transaction` SET `created_at` = CURRENT_TIMESTAMP WHERE `created_at` IS NULL;
