package com.example.beacon.service;

import com.example.beacon.entity.EventSource;
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
 * EVE JSON 파싱/매핑 로직은 SuricataEventMapper에 위임한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyslogEventHandler {

    /** syslog 헤더 뒤의 JSON 블록 추출. DOTALL 로 멀티라인 페이로드도 대응. */
    private static final Pattern JSON_PATTERN = Pattern.compile("(\\{.*})", Pattern.DOTALL);

    private final SecurityEventService securityEventService;
    private final SuricataEventMapper  suricataEventMapper;
    private final ObjectMapper         objectMapper;

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

            String eventType = root.path("event_type").asText("").toLowerCase();
            if (SuricataEventMapper.SKIP_EVENT_TYPES.contains(eventType)) {
                log.debug("[Syslog] 비보안 이벤트 타입 스킵: {}", eventType);
                return;
            }

            SecurityEvent event = suricataEventMapper.mapToSecurityEvent(root, json, EventSource.SYSLOG);
            securityEventService.createEvent(event);
            log.info("[Syslog] SecurityEvent 저장 완료 - type={}, src={}", event.getEventType(), event.getSourceIp());
        } catch (Exception e) {
            log.error("[Syslog] 메시지 처리 중 오류 발생: {}", raw, e);
        }
    }

    /**
     * Syslog 헤더(RFC 3164 / RFC 5424)를 제거하고 첫 번째 JSON 블록을 추출한다.
     */
    private String extractJson(String raw) {
        Matcher m = JSON_PATTERN.matcher(raw);
        return m.find() ? m.group(1) : null;
    }
}
