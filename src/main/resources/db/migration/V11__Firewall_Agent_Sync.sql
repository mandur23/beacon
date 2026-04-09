-- 방화벽 desired state 단조 리비전(SSoT 보조) 및 에이전트 방화벽 동기화

CREATE TABLE IF NOT EXISTS firewall_sync_state (
    id BIGINT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO firewall_sync_state (id, revision) VALUES (1, 0)
ON DUPLICATE KEY UPDATE id = id;

ALTER TABLE agents
    ADD COLUMN owner_user_id BIGINT NULL,
    ADD COLUMN last_firewall_applied_revision BIGINT NULL,
    ADD COLUMN last_firewall_status_at DATETIME NULL,
    ADD COLUMN firewall_status_json TEXT NULL,
    ADD CONSTRAINT fk_agents_owner_user FOREIGN KEY (owner_user_id) REFERENCES users (id);

CREATE INDEX idx_agents_owner_user ON agents (owner_user_id);

CREATE TABLE IF NOT EXISTS firewall_agent_commands (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    command_id CHAR(36) NOT NULL,
    revision BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    payload TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_firewall_agent_commands_agent FOREIGN KEY (agent_id) REFERENCES agents (id),
    UNIQUE KEY uk_firewall_agent_commands_command (command_id),
    KEY idx_firewall_agent_commands_agent_seq (agent_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
