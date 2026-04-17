package com.example.beacon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrafficLogRequest {
    private String sourceIp;
    private String destinationIp;
    private Integer sourcePort;
    private Integer destinationPort;
    private String protocol;
    private Long bytesTransferred;
    private Long packetsTransferred;
    private Integer duration;
    private String rawData;
    private String agentName;
}
