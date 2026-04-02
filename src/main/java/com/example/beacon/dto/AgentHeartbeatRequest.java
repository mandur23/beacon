package com.example.beacon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentHeartbeatRequest {

    @NotBlank(message = "agentName은 필수입니다")
    @Size(max = 100)
    private String agentName;
}
