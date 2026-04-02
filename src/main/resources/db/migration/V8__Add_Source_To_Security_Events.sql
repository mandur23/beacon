-- SecurityEvent 수집 경로 구분 필드 추가
-- 가능한 값: SURICATA, SYSLOG, AGENT, MANUAL
ALTER TABLE security_events
    ADD COLUMN source VARCHAR(20) NULL AFTER risk_score;
