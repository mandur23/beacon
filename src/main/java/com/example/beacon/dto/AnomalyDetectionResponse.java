package com.example.beacon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnomalyDetectionResponse {
    private Boolean isAnomaly;
    private Double anomalyScore;
    private String anomalyReason;
}
