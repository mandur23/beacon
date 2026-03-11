-- 사용자 테이블
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(500) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret VARCHAR(200),
    login_attempts INT NOT NULL DEFAULT 0,
    last_login_at DATETIME,
    locked_until DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 사용자 세션 테이블
CREATE TABLE IF NOT EXISTS user_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_token VARCHAR(500) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    user_agent VARCHAR(500),
    device_type VARCHAR(100),
    location VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    request_count INT NOT NULL DEFAULT 0,
    risk_score DOUBLE DEFAULT 0.0,
    risk_factors TEXT,
    INDEX idx_user_id (user_id),
    INDEX idx_session_token (session_token),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 보안 이벤트 테이블
CREATE TABLE IF NOT EXISTS security_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    source_ip VARCHAR(45) NOT NULL,
    destination_ip VARCHAR(45),
    location VARCHAR(100),
    protocol VARCHAR(20) NOT NULL,
    port INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    description TEXT,
    metadata JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME,
    handled_by VARCHAR(100),
    blocked BOOLEAN NOT NULL DEFAULT TRUE,
    risk_score DOUBLE NOT NULL DEFAULT 0.0,
    INDEX idx_severity (severity),
    INDEX idx_created_at (created_at),
    INDEX idx_source_ip (source_ip)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 트래픽 로그 테이블
CREATE TABLE IF NOT EXISTS traffic_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_ip VARCHAR(45) NOT NULL,
    destination_ip VARCHAR(45) NOT NULL,
    source_port INT NOT NULL,
    destination_port INT NOT NULL,
    protocol VARCHAR(20) NOT NULL,
    bytes_transferred BIGINT NOT NULL,
    packets_transferred BIGINT NOT NULL,
    duration INT NOT NULL,
    is_anomaly BOOLEAN NOT NULL DEFAULT FALSE,
    anomaly_score DOUBLE DEFAULT 0.0,
    anomaly_reason TEXT,
    raw_data JSON,
    timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    session_id BIGINT,
    INDEX idx_timestamp (timestamp),
    INDEX idx_source_ip (source_ip),
    INDEX idx_anomaly (is_anomaly),
    FOREIGN KEY (session_id) REFERENCES user_sessions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 방화벽 규칙 테이블
CREATE TABLE IF NOT EXISTS firewall_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    action VARCHAR(20) NOT NULL,
    source_address VARCHAR(100) NOT NULL,
    destination_address VARCHAR(100) NOT NULL,
    port VARCHAR(50) NOT NULL,
    priority INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    hits BIGINT NOT NULL DEFAULT 0,
    description TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기본 관리자 계정 생성 (비밀번호: admin1234)
INSERT INTO users (username, password, email, name, role, enabled, mfa_enabled)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@beacon.local', '관리자', 'ADMIN', TRUE, FALSE)
ON DUPLICATE KEY UPDATE username = username;
