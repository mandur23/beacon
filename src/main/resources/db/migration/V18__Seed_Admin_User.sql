-- admin 계정이 없을 때만 생성한다.
-- 초기 비밀번호: admin1234 (운영 환경에서는 반드시 변경하세요)
INSERT INTO users (username, password, email, name, role, enabled, mfa_enabled, login_attempts)
SELECT 'admin',
       '$2a$12$y.6.SwCXAdQAmnKH9ISmJO83ya/yFL.h4xHYyoXDuZMtf/wGtz.bi',
       'admin@beacon.local',
       '관리자',
       'ADMIN',
       TRUE,
       FALSE,
       0
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE username = 'admin'
);
