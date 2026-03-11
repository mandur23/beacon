package com.example.beacon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentRegisterRequest {
    private String agentName;
    private String hostname;
    private String ipAddress;
    private String osType;
    private String osVersion;
    private String agentVersion;
    private String username;
    private String metadata;
}
