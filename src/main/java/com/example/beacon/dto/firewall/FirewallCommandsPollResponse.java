package com.example.beacon.dto.firewall;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FirewallCommandsPollResponse {

    private List<FirewallAgentCommandMessage> commands;

    /**
     * 다음 롱폴 요청 시 {@code GET .../firewall-commands?since=} 에 넣을 커서.
     * 항상 DB PK({@code firewall_agent_commands.id})이며, {@code commandId}(UUID)와 혼동하면 안 된다.
     * 명령이 없으면 이전 요청의 {@code since}와 동일한 값이 반환된다.
     */
    private long lastCommandId;
}
