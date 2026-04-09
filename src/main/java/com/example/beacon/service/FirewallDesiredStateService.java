package com.example.beacon.service;

import com.example.beacon.dto.firewall.FirewallDesiredStateResponse;
import com.example.beacon.dto.firewall.FirewallRuleSnapshotDto;
import com.example.beacon.entity.FirewallRule;
import com.example.beacon.repository.FirewallRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FirewallDesiredStateService {

    private final FirewallRuleRepository firewallRuleRepository;
    private final FirewallRevisionService firewallRevisionService;

    private static final Map<String, String> DEFAULT_PROFILES = defaultProfiles();

    private static Map<String, String> defaultProfiles() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("domain", "on");
        m.put("private", "on");
        m.put("public", "on");
        return Map.copyOf(m);
    }

    @Transactional(readOnly = true)
    public FirewallDesiredStateResponse buildDesiredState() {
        long revision = firewallRevisionService.getCurrentRevision();
        List<FirewallRule> enabled = firewallRuleRepository.findAllOrderedByPriority().stream()
                .filter(FirewallRule::getEnabled)
                .collect(Collectors.toList());

        List<FirewallRuleSnapshotDto> rules = enabled.stream()
                .map(this::toSnapshot)
                .collect(Collectors.toList());

        return FirewallDesiredStateResponse.builder()
                .revision(revision)
                .rules(rules)
                .firewallProfiles(DEFAULT_PROFILES)
                .build();
    }

    private FirewallRuleSnapshotDto toSnapshot(FirewallRule r) {
        String port = r.getPort() != null ? r.getPort().trim() : "any";
        String protocol = "any".equalsIgnoreCase(port) ? "any" : port;

        return FirewallRuleSnapshotDto.builder()
                .ruleId(r.getId())
                .action(r.getAction() != null ? r.getAction() : "block")
                .remoteAddresses(List.of(r.getSourceAddress()))
                .direction("inbound")
                .protocol(protocol)
                .enabled(Boolean.TRUE.equals(r.getEnabled()))
                .displayName(r.getName())
                .build();
    }
}
