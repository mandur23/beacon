package com.example.beacon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseAlertService {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(180_000L); // 3분 타임아웃
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"status\":\"ok\"}"));
        } catch (Exception e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /**
     * 새 위협 이벤트 발생 시 모든 구독자에게 브로드캐스트.
     * severity: "HIGH" / "CRITICAL" 등
     */
    public void broadcast(String eventType, String severity, String message, Long eventId) {
        if (emitters.isEmpty()) return;

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "type",     eventType,
                    "severity", severity,
                    "message",  message,
                    "eventId",  eventId != null ? eventId : 0,
                    "ts",       System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.warn("SSE payload serialization failed", e);
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("alert").data(payload));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }
}
