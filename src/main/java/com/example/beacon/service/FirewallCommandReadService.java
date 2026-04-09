package com.example.beacon.service;

import com.example.beacon.dto.firewall.FirewallAgentCommandMessage;
import com.example.beacon.dto.firewall.FirewallCommandsPollResponse;
import com.example.beacon.entity.FirewallAgentCommand;
import com.example.beacon.repository.FirewallAgentCommandRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FirewallCommandReadService {

    private final FirewallAgentCommandRepository firewallAgentCommandRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public FirewallCommandsPollResponse findNewCommands(long agentId, long sinceCommandId) {
        var page = PageRequest.of(0, 50);
        List<FirewallAgentCommand> rows = firewallAgentCommandRepository
                .findByAgentIdAndIdGreaterThanOrderByIdAsc(agentId, sinceCommandId, page);

        if (rows.isEmpty()) {
            return FirewallCommandsPollResponse.builder()
                    .commands(List.of())
                    .lastCommandId(sinceCommandId)
                    .build();
        }

        List<FirewallAgentCommandMessage> messages = new ArrayList<>();
        long lastId = sinceCommandId;
        for (FirewallAgentCommand row : rows) {
            Map<String, Object> payload = parsePayload(row.getPayload());
            messages.add(FirewallAgentCommandMessage.builder()
                    .commandId(row.getCommandId())
                    .revision(row.getRevision())
                    .action(row.getAction())
                    .payload(payload)
                    .build());
            lastId = row.getId();
        }

        return FirewallCommandsPollResponse.builder()
                .commands(messages)
                .lastCommandId(lastId)
                .build();
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }
}
