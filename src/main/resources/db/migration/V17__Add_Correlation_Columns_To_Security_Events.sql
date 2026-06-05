ALTER TABLE security_events
    ADD COLUMN principal_ip VARCHAR(45) NULL AFTER handled_by,
    ADD COLUMN incident_key VARCHAR(100) NULL AFTER principal_ip,
    ADD COLUMN incident_type VARCHAR(100) NULL AFTER incident_key,
    ADD COLUMN correlation_status VARCHAR(50) NULL AFTER incident_type;

CREATE INDEX idx_principal_ip ON security_events (principal_ip);
CREATE INDEX idx_incident_key ON security_events (incident_key);
CREATE INDEX idx_incident_type ON security_events (incident_type);
