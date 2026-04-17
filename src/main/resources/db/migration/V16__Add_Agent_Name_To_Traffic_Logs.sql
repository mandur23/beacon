-- 트래픽 로그 테이블에 에이전트 이름 컬럼 추가 (이상 탐지 필드는 이미 V1에 존재하므로 제외)
ALTER TABLE traffic_logs ADD COLUMN agent_name VARCHAR(100);

-- 검색 최적화를 위한 인덱스 추가
CREATE INDEX idx_agent_name ON traffic_logs (agent_name);
