package com.example.beacon.service;

import com.example.beacon.dto.firewall.FirewallCommandsPollResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FirewallLongPollService {

    private static final long POLL_TIMEOUT_MS = 55_000L;
    private static final long SLEEP_MS = 1_000L;

    private final FirewallCommandReadService firewallCommandReadService;

    /**
     * 새 명령이 생기거나 타임아웃(약 55초)까지 1초 간격으로 폴링한다.
     */
    public FirewallCommandsPollResponse longPoll(long agentId, long sinceCommandId) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            FirewallCommandsPollResponse chunk = firewallCommandReadService.findNewCommands(agentId, sinceCommandId);
            if (!chunk.getCommands().isEmpty()) {
                return chunk;
            }
            try {
                Thread.sleep(SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return firewallCommandReadService.findNewCommands(agentId, sinceCommandId);
            }
        }
        return firewallCommandReadService.findNewCommands(agentId, sinceCommandId);
    }
}
