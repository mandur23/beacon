package com.example.beacon.service;

import com.example.beacon.entity.EventSource;
import com.example.beacon.entity.SecurityEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Suricata EVE JSON → SecurityEvent 매핑 공통 로직.
 * SyslogEventHandler와 EveJsonFileWatcherService 양쪽에서 재사용한다.
 */
@Component
public class SuricataEventMapper {

    public static final Set<String> SKIP_EVENT_TYPES = Set.of(
            "stats", "flow", "netflow", "fileinfo", "packetinfo"
    );

    public SecurityEvent mapToSecurityEvent(JsonNode root, String rawJson, EventSource source) {
        String eventType = textOrDefault(root, "event_type", "unknown");
        String srcIp     = textOrDefault(root, "src_ip",     "0.0.0.0");
        String destIp    = textOrDefault(root, "dest_ip",    null);
        String proto     = textOrDefault(root, "proto",      "UDP").toUpperCase();
        int    destPort  = root.path("dest_port").asInt(0);

        JsonNode alertNode       = root.path("alert");
        int      suricataSeverity = alertNode.path("severity").asInt(3);
        String   signature        = alertNode.path("signature").asText("");
        String   category         = alertNode.path("category").asText("");

        String severity  = mapSeverity(suricataSeverity);
        double riskScore = mapRiskScore(suricataSeverity);

        String description = buildDescription(eventType, signature, category);
        String status = "alert".equalsIgnoreCase(eventType) ? "차단됨" : "탐지됨";

        return SecurityEvent.builder()
                .eventType(truncate(eventType.isEmpty() ? "unknown" : eventType, 100))
                .severity(severity)
                .sourceIp(truncate(srcIp, 45))
                .destinationIp(destIp != null ? truncate(destIp, 45) : null)
                .protocol(truncate(proto, 20))
                .port(destPort)
                .status(truncate(status, 50))
                .description(description)
                .metadata(rawJson)
                .blocked("alert".equalsIgnoreCase(eventType))
                .riskScore(riskScore)
                .source(source)
                .build();
    }

    public String mapSeverity(int suricataSeverity) {
        return switch (suricataSeverity) {
            case 1  -> "HIGH";
            case 2  -> "MEDIUM";
            case 3  -> "LOW";
            default -> "INFO";
        };
    }

    public double mapRiskScore(int suricataSeverity) {
        return switch (suricataSeverity) {
            case 1  -> 90.0;
            case 2  -> 60.0;
            case 3  -> 30.0;
            default -> 10.0;
        };
    }

    public String buildDescription(String eventType, String signature, String category) {
        StringBuilder sb = new StringBuilder("[").append(eventType).append("]");
        if (!signature.isBlank()) sb.append(" ").append(signature);
        if (!category.isBlank())  sb.append(" | ").append(category);
        return sb.toString();
    }

    public String textOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? defaultValue : v.asText();
    }

    public String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
