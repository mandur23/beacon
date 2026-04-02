-- security_events 조회 패턴 기준 복합 인덱스 추가
--
-- 기존 단일 컬럼 인덱스: idx_severity, idx_created_at, idx_source_ip
-- 추가 인덱스는 아래 주요 쿼리 패턴을 커버한다.

-- 1. 위협 화면: 날짜 범위 + severity 필터 (findWithFiltersAndDateRange)
--    WHERE severity = ? AND created_at >= ? AND created_at < ?
ALTER TABLE security_events
    ADD INDEX idx_severity_created_at (severity, created_at);

-- 2. 미해결 이벤트 조회 (findUnresolvedEvents, countUnresolvedEvents)
--    WHERE status = 'pending' OR status = '조사중'
ALTER TABLE security_events
    ADD INDEX idx_status (status);

-- 3. 해결된 이벤트 + 처리 시간 산출 (averageResolutionMinutes, findTop20ByResolvedAtIsNotNull)
--    WHERE resolved_at IS NOT NULL ORDER BY resolved_at DESC
ALTER TABLE security_events
    ADD INDEX idx_resolved_at (resolved_at);

-- 4. 출처별 필터링 — V8 에서 추가된 source 컬럼 (향후 대시보드 출처별 집계용)
--    WHERE source = ? AND created_at >= ?
ALTER TABLE security_events
    ADD INDEX idx_source_created_at (source, created_at);

-- 5. 차단 이벤트 집계 (countBlockedEventsSince)
--    WHERE blocked = true AND created_at > ?
ALTER TABLE security_events
    ADD INDEX idx_blocked_created_at (blocked, created_at);
