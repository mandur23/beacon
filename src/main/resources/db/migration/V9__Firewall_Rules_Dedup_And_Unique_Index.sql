-- 방화벽 차단 규칙 중복 정리 및 유니크 인덱스 추가
--
-- 대상: action = 'block' AND enabled = 1 인 활성 차단 규칙만.
-- allow / log / 비활성 규칙은 건드리지 않는다.

-- 1단계: 같은 (source_address, destination_address, port) 조합의 활성 block 규칙이
--        여러 건일 때, 가장 오래된 id 1건에 hits를 합산한다.
UPDATE firewall_rules fr
JOIN (
    SELECT
        MIN(id)                  AS keep_id,
        source_address,
        destination_address,
        port,
        SUM(hits)                AS total_hits
    FROM firewall_rules
    WHERE action = 'block'
      AND enabled = 1
    GROUP BY source_address, destination_address, port
    HAVING COUNT(*) > 1
) d ON fr.id = d.keep_id
SET fr.hits = d.total_hits;

-- 2단계: 동일 조합의 중복 활성 block 규칙 중 id가 더 큰 것(나중에 생성된 것)을 삭제한다.
DELETE fr
FROM firewall_rules fr
JOIN firewall_rules fr2
    ON  fr.source_address      = fr2.source_address
    AND fr.destination_address = fr2.destination_address
    AND fr.port                = fr2.port
    AND fr.action              = 'block'
    AND fr2.action             = 'block'
    AND fr.enabled             = 1
    AND fr2.enabled            = 1
    AND fr.id > fr2.id;

-- 3단계: 이후 같은 조합의 활성 규칙이 중복 생성되지 않도록 유니크 인덱스를 추가한다.
-- (source_address, action, enabled, destination_address, port) 5열 조합으로 구성한다.
-- enabled 포함으로, 비활성화된 규칙과 활성 규칙이 같은 키를 공유해도 충돌하지 않는다.
ALTER TABLE firewall_rules
    ADD UNIQUE INDEX uk_fw_rule_effective_match
        (source_address, action, enabled, destination_address, port);
