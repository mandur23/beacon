-- 기본 관리자 계정의 로그인 정보를 보정한다.
-- username: admin / password: admin1234 (BCrypt)

UPDATE users
SET password = '$2b$10$aWDSqWCiu9Zr3.oIgU2g6eXZOKw5tCUkinZEqwpIKVebCKcW4W3eS',
    enabled = TRUE,
    mfa_enabled = FALSE,
    role = 'ADMIN'
WHERE username = 'admin';

INSERT INTO users (username, password, email, name, role, enabled, mfa_enabled)
SELECT 'admin',
       '$2b$10$aWDSqWCiu9Zr3.oIgU2g6eXZOKw5tCUkinZEqwpIKVebCKcW4W3eS',
       'admin@beacon.local',
       '관리자',
       'ADMIN',
       TRUE,
       FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'admin'
);
