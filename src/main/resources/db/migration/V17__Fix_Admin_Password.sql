-- admin 계정의 비밀번호를 올바른 해시값으로 수정
-- 기본 비밀번호: admin1234
-- BCrypt 해시: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

-- 기존 admin 계정 삭제
DELETE FROM users WHERE username = 'admin';

-- 올바른 비밀번호로 admin 계정 재생성
INSERT INTO users (username, password, email, name, role, enabled, mfa_enabled, login_attempts)
VALUES (
    'admin',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'admin@beacon.local',
    '관리자',
    'ADMIN',
    TRUE,
    FALSE,
    0
);
