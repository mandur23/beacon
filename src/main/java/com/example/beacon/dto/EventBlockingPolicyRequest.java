package com.example.beacon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EventBlockingPolicyRequest {

    @NotBlank
    private String name;

    /** * = 전체, FILE_* = 접두사, FILE_MODIFIED = 정확히 일치 */
    @NotBlank
    private String eventTypePattern;

    /** 비우면 심각도 무관 */
    private String severity;

    /** 비우면 소스 IP 무관 */
    private String sourceIpPrefix;

    @NotNull
    private Boolean blocked;

    @NotNull
    private Integer priority;

    @NotNull
    private Boolean enabled;
}
