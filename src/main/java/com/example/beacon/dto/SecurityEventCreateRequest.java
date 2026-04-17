package com.example.beacon.dto;

import com.example.beacon.entity.EventSource;
import com.example.beacon.entity.SecurityEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 외부에서 SecurityEvent를 생성할 때 사용하는 DTO.
 * 엔티티 직접 노출을 막아 id·createdAt 등 내부 필드 임의 설정을 방지한다.
 * 차단 여부(blocked)는 클라이언트가 아니라 서버의 {@code EventBlockingPolicyService}가 결정한다.
 */
@Data
public class SecurityEventCreateRequest {

    @NotBlank
    private String eventType;

    @NotBlank
    private String severity;

    @NotBlank
    private String sourceIp;

    private String agentName;

    private String destinationIp;

    private String location;

    @NotBlank
    private String protocol;

    @NotNull
    private Integer port;

    private String description;

    private String metadata;

    private Double riskScore = 0.0;

    public SecurityEvent toEntity() {
        EventSource resolvedSource = EventSource.AGENT;
        
        // eventType 기반 판별
        if ("WAZUH_ALERT".equalsIgnoreCase(eventType)) {
            resolvedSource = EventSource.WAZUH;
        } else if (eventType != null && eventType.toUpperCase().startsWith("IDS_")) {
            resolvedSource = EventSource.SURICATA;
        }
        
        // 요약 정보(description/summary)에 [Wazuh] 패턴이 있는 경우 보정
        if (description != null && description.contains("[Wazuh]")) {
            resolvedSource = EventSource.WAZUH;
        }

        return SecurityEvent.builder()
                .eventType(eventType)
                .severity(severity)
                .sourceIp(sourceIp)
                .destinationIp(destinationIp)
                .location(location)
                .protocol(protocol)
                .port(port)
                .status("pending")
                .description(description)
                .agentName(agentName)
                .metadata(metadata)
                .blocked(false)
                .riskScore(riskScore != null ? riskScore : 0.0)
                .source(resolvedSource)
                .build();
    }
}
