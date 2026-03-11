-- agents 테이블 추가
CREATE TABLE IF NOT EXISTS agents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_name VARCHAR(100) NOT NULL UNIQUE,
    hostname VARCHAR(200) NOT NULL,
    ip_address VARCHAR(50) NOT NULL,
    os_type VARCHAR(50),
    os_version VARCHAR(100),
    agent_version VARCHAR(20),
    username VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'offline',
    registered_at DATETIME NOT NULL,
    last_heartbeat DATETIME,
    total_events BIGINT NOT NULL DEFAULT 0,
    total_traffic_logs BIGINT NOT NULL DEFAULT 0,
    metadata TEXT,
    INDEX idx_agent_name (agent_name),
    INDEX idx_status (status),
    INDEX idx_last_heartbeat (last_heartbeat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
