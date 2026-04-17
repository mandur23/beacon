-- 자동 테스트를 위한 계정 생성
-- 초기 비밀번호: testpassword
INSERT INTO users (username, password, email, name, role, enabled, mfa_enabled)
SELECT 'testuser',
       '$2b$10$aWDSqWCiu9Zr3.oIgU2g6eXZOKw5tCUkinZEqwpIKVebCKcW4W3eS', -- Password: admin
       'test@beacon.local',
       '테스트유저',
       'ADMIN',
       TRUE,
       FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'testuser'
);
