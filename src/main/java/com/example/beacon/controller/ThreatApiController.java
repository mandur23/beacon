package com.example.beacon.controller;

import com.example.beacon.entity.SecurityEvent;
import com.example.beacon.service.SecurityEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/threats")
@RequiredArgsConstructor
public class ThreatApiController {

    private final SecurityEventService securityEventService;

    @PostMapping("/{id}/block")
    public ResponseEntity<Map<String, Object>> blockIp(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "인증이 필요합니다"));
        }
        SecurityEvent event = securityEventService.resolveEvent(id, user.getUsername());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "IP " + event.getSourceIp() + " 차단이 완료되었습니다"
        ));
    }

    @PostMapping("/{id}/investigate")
    public ResponseEntity<Map<String, Object>> startInvestigation(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "인증이 필요합니다"));
        }
        SecurityEvent event = securityEventService.markAsInvestigating(id, user.getUsername());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "이벤트 " + event.getId() + " 조사가 시작되었습니다"
        ));
    }
}
