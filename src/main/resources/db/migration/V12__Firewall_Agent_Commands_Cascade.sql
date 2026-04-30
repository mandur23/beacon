-- 에이전트 삭제 시 대기 중인 방화벽 명령 행도 함께 제거
ALTER TABLE firewall_agent_commands
    DROP FOREIGN KEY fk_firewall_agent_commands_agent;

ALTER TABLE firewall_agent_commands
    ADD CONSTRAINT fk_firewall_agent_commands_agent
    FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE;
