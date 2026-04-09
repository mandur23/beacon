package com.example.beacon.service;

import com.example.beacon.config.BeaconFirewallProperties;
import com.example.beacon.config.FirewallChannelMode;
import com.example.beacon.entity.Agent;
import com.example.beacon.entity.FirewallAgentCommand;
import com.example.beacon.firewall.FirewallAgentActions;
import com.example.beacon.repository.AgentRepository;
import com.example.beacon.repository.FirewallAgentCommandRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirewallCommandEnqueueService {

    private final AgentRepository agentRepository;
    private final FirewallAgentCommandRepository firewallAgentCommandRepository;
    private final ObjectMapper objectMapper;
    private final BeaconFirewallProperties beaconFirewallProperties;

    @Transactional
    public void enqueueUpsertRule(long revision, long ruleId) {
        enqueue(revision, FirewallAgentActions.UPSERT_RULE, Map.of("ruleId", ruleId));
    }

    @Transactional
    public void enqueueDeleteRule(long revision, long ruleId) {
        enqueue(revision, FirewallAgentActions.DELETE_RULE, Map.of("ruleId", ruleId));
    }

    private void enqueue(long revision, String action, Map<String, Object> payload) {
        if (beaconFirewallProperties.getChannelMode() == FirewallChannelMode.PULL_ONLY) {
            log.debug("[Firewall B] channel-mode=PULL_ONLY, 푸시 큐 생략, revision={}", revision);
            return;
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("firewall command payload 직렬화 실패", e);
        }

        List<Agent> agents = agentRepository.findAll();
        if (agents.isEmpty()) {
            log.debug("[Firewall B] 등록된 에이전트 없음, revision={}", revision);
            return;
        }

        List<FirewallAgentCommand> batch = new ArrayList<>(agents.size());
        for (Agent a : agents) {
            batch.add(FirewallAgentCommand.builder()
                    .agentId(a.getId())
                    .commandId(UUID.randomUUID().toString())
                    .revision(revision)
                    .action(action)
                    .payload(payloadJson)
                    .build());
        }
        firewallAgentCommandRepository.saveAll(batch);
        log.debug("[Firewall B] enqueued {} to {} agents (batch), revision={}", action, agents.size(), revision);
    }
}
