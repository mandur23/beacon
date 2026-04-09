package com.example.beacon.dto.firewall;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FirewallAgentCommandMessage {

    private String commandId;
    private long revision;
    private String action;
    private Map<String, Object> payload;
}
