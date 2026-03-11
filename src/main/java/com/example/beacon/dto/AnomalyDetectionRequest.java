package com.example.beacon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnomalyDetectionRequest {
    private Long trafficLogId;
    private String sourceIp;
    private String destinationIp;
    private String protocol;
    private Long bytesTransferred;
    private Long packetsTransferred;
    private Integer duration;
}
