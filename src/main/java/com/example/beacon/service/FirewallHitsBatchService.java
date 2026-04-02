package com.example.beacon.service;

import com.example.beacon.repository.FirewallRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Suricata/Syslog 이벤트 유입 시마다 방화벽 규칙 hits를 매건 UPDATE 하는 대신,
 * 메모리에 IP별 카운터를 누적한 뒤 3초마다 단일 배치 UPDATE로 flush 한다.
 *
 * <p>효과:
 * <ul>
 *   <li>대량 이벤트 유입 시 DB 쓰기 횟수를 1/N 으로 감소</li>
 *   <li>flush 주기 동안 최대 3초 이내의 집계 지연만 발생</li>
 * </ul>
 *
 * <p>정확도:
 * {@link AtomicLong#getAndSet(long)} 으로 flush 시 카운터를 원자적으로 드레인하므로
 * flush 중 새로 들어온 hits는 다음 주기에 반영된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirewallHitsBatchService {

    private final FirewallRuleRepository firewallRuleRepository;

    /** IP → 누적 hits 카운터. 여러 스레드에서 동시에 접근하므로 ConcurrentHashMap 사용. */
    private final ConcurrentHashMap<String, AtomicLong> pendingHits = new ConcurrentHashMap<>();

    /**
     * 이벤트 처리 스레드에서 호출. O(1) 메모리 연산만 수행한다.
     *
     * @param ip 방화벽 규칙에 매칭할 소스 IP
     */
    public void recordHit(String ip) {
        if (ip == null || ip.isBlank()) return;
        pendingHits.computeIfAbsent(ip, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 3초마다 누적된 hits를 DB에 bulk UPDATE 한다.
     * 해당 IP의 활성 block 규칙이 없으면 UPDATE 는 0건으로 무시된다.
     */
    @Scheduled(fixedDelay = 3_000)
    @Transactional
    public void flush() {
        if (pendingHits.isEmpty()) return;

        // 현재 누적값을 드레인 (getAndSet(0) — 이후 increment 는 다음 주기 처리)
        Map<String, Long> snapshot = new HashMap<>();
        pendingHits.forEach((ip, counter) -> {
            long count = counter.getAndSet(0);
            if (count > 0) snapshot.put(ip, count);
        });

        if (snapshot.isEmpty()) return;

        int totalUpdated = 0;
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            totalUpdated += firewallRuleRepository.addHitsBySourceIp(entry.getKey(), entry.getValue());
        }
        log.debug("[FirewallHits] flush 완료 - {}개 IP, {}개 규칙 업데이트", snapshot.size(), totalUpdated);
    }
}
