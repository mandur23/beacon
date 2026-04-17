-- 보안 이벤트 테이블에 에이전트 이름 컬럼 추가
ALTER TABLE security_events ADD COLUMN agent_name VARCHAR(100);

-- 에이전트 이름 검색 최적화를 위한 인덱스 추가
CREATE INDEX idx_agent_name ON security_events (agent_name);
