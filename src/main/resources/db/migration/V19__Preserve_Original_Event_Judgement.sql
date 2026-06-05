ALTER TABLE security_events
    ADD COLUMN original_severity VARCHAR(20) NULL AFTER severity,
    ADD COLUMN original_risk_score DOUBLE NULL AFTER risk_score,
    ADD COLUMN original_source VARCHAR(20) NULL AFTER source;
