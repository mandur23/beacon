-- 에이전트 등 API로 들어오는 보안 이벤트의 차단 여부를 서버(웹) 정책으로 결정한다.
CREATE TABLE IF NOT EXISTS event_blocking_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    event_type_pattern VARCHAR(255) NOT NULL DEFAULT '*',
    severity VARCHAR(20) NULL,
    source_ip_prefix VARCHAR(64) NULL,
    blocked TINYINT(1) NOT NULL DEFAULT 0,
    priority INT NOT NULL DEFAULT 100,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ebp_enabled_priority (enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
