-- Align agents schema with Agent entity for Hibernate validation.
-- MySQL does not reliably support ADD COLUMN IF NOT EXISTS across versions,
-- so use a conditional dynamic statement.
SET @has_updated_at := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'agents'
      AND COLUMN_NAME = 'updated_at'
);

SET @ddl := IF(
    @has_updated_at = 0,
    'ALTER TABLE agents ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
