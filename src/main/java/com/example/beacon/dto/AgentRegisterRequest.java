package com.example.beacon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentRegisterRequest {

    @NotBlank(message = "agentName은 필수입니다")
    @Size(max = 100, message = "agentName은 100자 이내여야 합니다")
    private String agentName;

    @NotBlank(message = "hostname은 필수입니다")
    @Size(max = 255)
    private String hostname;

    @NotBlank(message = "ipAddress는 필수입니다")
    @Pattern(regexp = "^[\\d.:a-fA-F]+$", message = "유효한 IPv4/IPv6 주소여야 합니다")
    private String ipAddress;

    @NotBlank(message = "osType은 필수입니다")
    private String osType;

    private String osVersion;

    /** 에이전트 소프트웨어 버전 (예: "1.2.0"). 스키마 불일치 추적용 */
    @NotBlank(message = "agentVersion은 필수입니다")
    @Pattern(regexp = "^\\d+\\.\\d+(\\.\\d+)?(-[\\w.]+)?$",
             message = "agentVersion은 SemVer 형식이어야 합니다 (예: 1.2.0)")
    private String agentVersion;

    private String username;

    private String metadata;
}
