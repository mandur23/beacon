-- 보관 기간 초과 배치 삭제 시 범위 스캔 비용 완화
CREATE INDEX idx_firewall_agent_commands_created_at ON firewall_agent_commands (created_at);
