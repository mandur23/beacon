package com.example.beacon.service;

import com.example.beacon.entity.SecurityEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UDP syslog 포트로 수신된 Suricata EVE JSON 메시지를 처리한다.
 *
 * Suricata alert.severity 매핑:
 *   1 → HIGH  (riskScore 90)
 *   2 → MEDIUM (riskScore 60)
 *   3 → LOW   (riskScore 30)
 *   그 외 → INFO (riskScore 10)
 *
 * stats / flow / stats 등 보안과 무관한 이벤트는 저장하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyslogEventHandler {

    /** syslog 헤더 뒤의 JSON 블록 추출. DOTALL 로 멀티라인 페이로드도 대응. */
    private static final Pattern JSON_PATTERN = Pattern.compile("(\\{.*})", Pattern.DOTALL);

    /** 보안과 무관한 Suricata event_type — 저장 생략 */
    private static final java.util.Set<String> SKIP_EVENT_TYPES = java.util.Set.of(
            "stats", "flow", "netflow", "fileinfo", "packetinfo"
    );

    private final SecurityEventService securityEventService;
    private final ObjectMapper objectMapper;

    @ServiceActivator(inputChannel = "syslogChannel")
    public void handle(Message<byte[]> message) {
        String raw = new String(message.getPayload(), StandardCharsets.UTF_8).trim();
        log.debug("[Syslog] received: {}", raw);

        String json = extractJson(raw);
        if (json == null) {
            log.warn("[Syslog] JSON 페이로드를 찾을 수 없습니다: {}", raw);
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(json);

            // 보안과 무관한 이벤트 타입은 저장하지 않음
            String eventType = root.path("event_type").asText("").toLowerCase();
            if (SKIP_EVENT_TYPES.contains(eventType)) {
                log.debug("[Syslog] 비보안 이벤트 타입 스킵: {}", eventType);
                return;
            }

            SecurityEvent event = mapToSecurityEvent(root, json);
            securityEventService.createEvent(event);
            log.info("[Syslog] SecurityEvent 저장 완료 - type={}, src={}", event.getEventType(), event.getSourceIp());
        } catch (Exception e) {
            log.error("[Syslog] 메시지 처리 중 오류 발생: {}", raw, e);
        }
    }

    // -----------------------------------------------------------------------

    /**
     * Syslog 헤더(RFC 3164 / RFC 5424)를 제거하고 첫 번째 JSON 블록을 추출한다.
     */
    private String extractJson(String raw) {
        Matcher m = JSON_PATTERN.matcher(raw);
        return m.find() ? m.group(1) : null;
    }

    private SecurityEvent mapToSecurityEvent(JsonNode root, String rawJson) {
        String eventType = textOrDefault(root, "event_type", "unknown");
        String srcIp     = textOrDefault(root, "src_ip",     "0.0.0.0");
        String destIp    = textOrDefault(root, "dest_ip",    null);
        String proto     = textOrDefault(root, "proto",      "UDP").toUpperCase();
        int    destPort  = root.path("dest_port").asInt(0);

        JsonNode alertNode = root.path("alert");
        int suricataSeverity = alertNode.path("severity").asInt(3);
        String signature = alertNode.path("signature").asText("");
        String category  = alertNode.path("category").asText("");

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
                .build();
    }

    private String mapSeverity(int suricataSeverity) {
        return switch (suricataSeverity) {
            case 1  -> "HIGH";
            case 2  -> "MEDIUM";
            case 3  -> "LOW";
            default -> "INFO";
        };
    }

    private double mapRiskScore(int suricataSeverity) {
        return switch (suricataSeverity) {
            case 1  -> 90.0;
            case 2  -> 60.0;
            case 3  -> 30.0;
            default -> 10.0;
        };
    }

    private String buildDescription(String eventType, String signature, String category) {
        StringBuilder sb = new StringBuilder("[").append(eventType).append("]");
        if (!signature.isBlank()) sb.append(" ").append(signature);
        if (!category.isBlank())  sb.append(" | ").append(category);
        return sb.toString();
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? defaultValue : v.asText();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
