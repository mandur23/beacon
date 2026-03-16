package com.example.beacon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FirewallRuleRequest {
    private String name;
    private String action;
    private String sourceAddress;
    private String destinationAddress;
    private String port;
    private String priority;  // 프론트에서 문자열로 전달
    private String description;
}
