package com.example.beacon.service;

import com.example.beacon.dto.FirewallRuleRequest;
import com.example.beacon.entity.FirewallRule;
import com.example.beacon.exception.ResourceNotFoundException;
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
                .orElseThrow(() -> new ResourceNotFoundException("FirewallRule", id));
        rule.setEnabled(!rule.getEnabled());
        return firewallRuleRepository.save(rule);
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
        return firewallRuleRepository.save(rule);
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
        return firewallRuleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        FirewallRule rule = firewallRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FirewallRule", id));
        firewallRuleRepository.delete(rule);
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
