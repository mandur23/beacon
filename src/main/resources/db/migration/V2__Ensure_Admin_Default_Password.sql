-- admin 계정이 없을 경우에만 초기 계정을 생성한다.
-- 초기 비밀번호는 반드시 최초 로그인 후 변경해야 합니다.
-- UPDATE 로 비밀번호를 덮어쓰는 코드는 보안상 제거되었습니다.

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
