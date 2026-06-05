ALTER TABLE security_events
    ADD COLUMN last_seen_at DATETIME NULL AFTER created_at,
    ADD COLUMN repeat_count INT NOT NULL DEFAULT 1 AFTER last_seen_at;

UPDATE security_events
SET last_seen_at = COALESCE(last_seen_at, created_at),
    repeat_count = CASE
        WHEN repeat_count IS NULL OR repeat_count < 1 THEN 1
        ELSE repeat_count
    END;

CREATE INDEX idx_last_seen_at ON security_events (last_seen_at);
