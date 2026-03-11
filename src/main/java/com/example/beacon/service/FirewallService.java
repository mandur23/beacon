package com.example.beacon.service;

import com.example.beacon.entity.FirewallRule;
import com.example.beacon.repository.FirewallRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FirewallService {

    private final FirewallRuleRepository firewallRuleRepository;

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
                .orElseThrow(() -> new RuntimeException("Rule not found: " + id));
        rule.setEnabled(!rule.getEnabled());
        return firewallRuleRepository.save(rule);
    }
}
