package com.example.beacon.service;

import com.example.beacon.config.BeaconFirewallProperties;
import com.example.beacon.repository.FirewallAgentCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 오래된 방화벽 명령 행을 주기적으로 삭제해 테이블·롱폴 조회 비용을 제한한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FirewallAgentCommandCleanupService {

    private final FirewallAgentCommandRepository firewallAgentCommandRepository;
    private final BeaconFirewallProperties beaconFirewallProperties;

    /** 매일 새벽 3시 (서버 로컬 타임존) */
    @Scheduled(cron = "${beacon.firewall.cleanup-cron:0 0 3 * * ?}")
    @Transactional
    public void purgeExpiredCommands() {
        int days = beaconFirewallProperties.getCommandRetentionDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        int removed = firewallAgentCommandRepository.deleteByCreatedAtBefore(cutoff);
        if (removed > 0) {
            log.info("[Firewall B] 오래된 명령 {}건 삭제 (보관 {}일 이전)", removed, days);
        }
    }
}
