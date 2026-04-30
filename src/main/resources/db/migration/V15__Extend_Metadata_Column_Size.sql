-- 보안 이벤트의 메타데이터(파일 목록 등)가 매우 클 경우를 대비하여 LONGTEXT로 확장
ALTER TABLE security_events
    MODIFY COLUMN metadata LONGTEXT;
