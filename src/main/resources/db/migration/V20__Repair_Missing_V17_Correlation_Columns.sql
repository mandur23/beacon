-- V17이 flyway_schema_history에만 기록되고 실제 컬럼이 없는 DB 복구용 (멱등, MySQL 5.7+ 호환)

SET @db := DATABASE();

SET @exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'security_events' AND column_name = 'principal_ip'
);
SET @ddl := IF(@exists = 0,
    'ALTER TABLE security_events ADD COLUMN principal_ip VARCHAR(45) NULL AFTER handled_by',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'security_events' AND column_name = 'incident_key'
);
SET @ddl := IF(@exists = 0,
    'ALTER TABLE security_events ADD COLUMN incident_key VARCHAR(100) NULL AFTER principal_ip',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'security_events' AND column_name = 'incident_type'
);
SET @ddl := IF(@exists = 0,
    'ALTER TABLE security_events ADD COLUMN incident_type VARCHAR(100) NULL AFTER incident_key',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'security_events' AND column_name = 'correlation_status'
);
SET @ddl := IF(@exists = 0,
    'ALTER TABLE security_events ADD COLUMN correlation_status VARCHAR(50) NULL AFTER incident_type',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @db AND table_name = 'security_events' AND index_name = 'idx_principal_ip'
);
SET @ddl := IF(@exists = 0,
    'CREATE INDEX idx_principal_ip ON security_events (principal_ip)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @db AND table_name = 'security_events' AND index_name = 'idx_incident_key'
);
SET @ddl := IF(@exists = 0,
    'CREATE INDEX idx_incident_key ON security_events (incident_key)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @db AND table_name = 'security_events' AND index_name = 'idx_incident_type'
);
SET @ddl := IF(@exists = 0,
    'CREATE INDEX idx_incident_type ON security_events (incident_type)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
