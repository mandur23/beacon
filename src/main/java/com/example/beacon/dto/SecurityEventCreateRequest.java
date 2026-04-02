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
                .metadata(metadata)
                .blocked(false)
                .riskScore(riskScore != null ? riskScore : 0.0)
                .source(EventSource.AGENT)
                .build();
    }
}
