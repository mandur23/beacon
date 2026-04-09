package com.example.beacon.dto.firewall;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FirewallStatusReportRequest {

    @NotNull
    private Long lastAppliedRevision;

    private List<String> errors;

    private List<String> localRuleIds;
}
