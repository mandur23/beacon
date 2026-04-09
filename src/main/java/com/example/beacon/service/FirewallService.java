package com.example.beacon.service;

import com.example.beacon.dto.FirewallRuleRequest;
import com.example.beacon.entity.FirewallRule;
import com.example.beacon.exception.ResourceNotFoundException;
import com.example.beacon.repository.FirewallRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirewallService {

    private final FirewallRuleRepository firewallRuleRepository;
    private final FirewallRevisionService firewallRevisionService;
    private final FirewallCommandEnqueueService firewallCommandEnqueueService;

    @Transactional(readOnly = true)
    public List<FirewallRule> getAllRulesOrdered() {
        return firewallRuleRepository.findAllOrderedByPriority();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRuleStats() {
        long total = firewallRuleRepository.count();
        long enabled = firewallRuleRepository.countEnabledRules();
        Long totalHits = firewallRuleRepository.getTotalHits();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("enabled", enabled);
        stats.put("disabled", total - enabled);
        stats.put("totalHits", totalHits != null ? totalHits : 0L);
        return stats;
    }

    @Transactional
    public FirewallRule toggleRule(Long id) {
        FirewallRule rule = firewallRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FirewallRule", id));
        rule.setEnabled(!rule.getEnabled());
        FirewallRule saved = firewallRuleRepository.save(rule);
        long rev = firewallRevisionService.bumpRevision();
        firewallCommandEnqueueService.enqueueUpsertRule(rev, saved.getId());
        return saved;
    }

    @Transactional
    public FirewallRule createRule(FirewallRuleRequest req) {
        int priority = parsePriority(req.getPriority(), 100);
        FirewallRule rule = FirewallRule.builder()
                .name(req.getName())
                .action(req.getAction() != null ? req.getAction() : "block")
                .sourceAddress(req.getSourceAddress())
                .destinationAddress(req.getDestinationAddress())
                .port(req.getPort())
                .priority(priority)
                .enabled(true)
                .hits(0L)
                .description(req.getDescription())
                .build();
        FirewallRule saved = firewallRuleRepository.save(rule);
        long rev = firewallRevisionService.bumpRevision();
        firewallCommandEnqueueService.enqueueUpsertRule(rev, saved.getId());
        return saved;
    }

    @Transactional
    public FirewallRule updateRule(Long id, FirewallRuleRequest req) {
        FirewallRule rule = firewallRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FirewallRule", id));
        rule.setName(req.getName());
        rule.setAction(req.getAction() != null ? req.getAction() : "block");
        rule.setSourceAddress(req.getSourceAddress());
        rule.setDestinationAddress(req.getDestinationAddress());
        rule.setPort(req.getPort());
        rule.setPriority(parsePriority(req.getPriority(), rule.getPriority()));
        if (req.getDescription() != null) {
            rule.setDescription(req.getDescription());
        }
        FirewallRule saved = firewallRuleRepository.save(rule);
        long rev = firewallRevisionService.bumpRevision();
        firewallCommandEnqueueService.enqueueUpsertRule(rev, saved.getId());
        return saved;
    }

    @Transactional
    public void deleteRule(Long id) {
        FirewallRule rule = firewallRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FirewallRule", id));
        long ruleId = rule.getId();
        firewallRuleRepository.delete(rule);
        long rev = firewallRevisionService.bumpRevision();
        firewallCommandEnqueueService.enqueueDeleteRule(rev, ruleId);
    }

    /**
     * 특정 소스 IP에 대한 활성 block 규칙이 없으면 새로 생성하고, 이미 있으면 재사용한다.
     *
     * <p><b>동시성 처리</b><br>
     * {@code REQUIRES_NEW}로 독립 트랜잭션을 열어 외부 트랜잭션과 격리한다.
     * 두 요청이 동시에 진입해 유니크 인덱스 위반({@code DataIntegrityViolationException})이
     * 발생해도 독립 트랜잭션이 롤백될 뿐, 외부 트랜잭션은 오염되지 않는다.
     * catch 블록의 재조회도 새 트랜잭션 컨텍스트에서 정상 실행된다.
     *
     * <p><b>원자성 주의</b><br>
     * {@code REQUIRES_NEW}로 이 메서드가 먼저 커밋된 후 호출자 트랜잭션이 롤백되면,
     * 방화벽 규칙은 DB에 남는다. 이는 보안 측면에서 허용 가능한 수준이다
     * (IP 차단이 남는 쪽이 차단 해제보다 안전하다).
     *
     * @param ip        차단할 소스 IP (null·공백 불가)
     * @param reason    규칙 description에 남길 차단 사유
     * @param createdBy 규칙 생성자 (사용자명)
     * @return 생성되거나 재사용된 방화벽 규칙
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FirewallRule createBlockRuleForIp(String ip, String reason, String createdBy) {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("ip must not be blank");
        }

        // 1차 조회: 이미 활성 block 규칙이 있으면 바로 반환 (리비전·B 명령 없음)
        return firewallRuleRepository
                .findFirstBySourceAddressAndActionAndEnabledTrue(ip, "block")
                .orElseGet(() -> {
                    FirewallRule rule = FirewallRule.builder()
                            .name("AUTO-BLOCK " + ip)
                            .action("block")
                            .sourceAddress(ip)
                            .destinationAddress("any")
                            .port("any")
                            .priority(10)
                            .enabled(true)
                            .hits(0L)
                            .description(reason)
                            .createdBy(createdBy)
                            .build();
                    try {
                        FirewallRule saved = firewallRuleRepository.save(rule);
                        long rev = firewallRevisionService.bumpRevision();
                        firewallCommandEnqueueService.enqueueUpsertRule(rev, saved.getId());
                        log.info("[Firewall] 자동 차단 규칙 생성 - ip={}, createdBy={}", ip, createdBy);
                        return saved;
                    } catch (DataIntegrityViolationException e) {
                        // 동시 요청 경합: 독립 트랜잭션이 롤백되므로 재조회가 정상 동작한다
                        log.warn("[Firewall] 동시 삽입 경합 감지, 기존 규칙 재사용 - ip={}", ip);
                        return firewallRuleRepository
                                .findFirstBySourceAddressAndActionAndEnabledTrue(ip, "block")
                                .orElseThrow(() -> new IllegalStateException(
                                        "방화벽 규칙 동시 삽입 충돌 후 재조회 실패 - ip=" + ip, e));
                    }
                });
    }

    /**
     * 이벤트 소스 IP에 매칭되는 활성 block 규칙의 hits를 1 증가시킨다.
     * SecurityEventService.createEvent() 에서 이벤트 저장 후 호출된다.
     */
    @Transactional
    public void incrementHitsForIp(String sourceIp) {
        if (sourceIp == null || sourceIp.isBlank()) return;
        int updated = firewallRuleRepository.incrementHitsBySourceIp(sourceIp);
        if (updated > 0) {
            log.debug("[Firewall] hits 증가 - ip={}, rules={}", sourceIp, updated);
        }
    }

    private static int parsePriority(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
