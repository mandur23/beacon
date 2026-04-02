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

    /**
     * 수동 IP 차단:
     *  - 이벤트 상태 "차단됨" + source=MANUAL 저장
     *  - 방화벽 차단 규칙 자동 등록
     * 두 작업이 단일 트랜잭션으로 묶여 있어 부분 성공 불일치가 발생하지 않는다.
     */
    @PostMapping("/{id}/block")
    public ResponseEntity<Map<String, Object>> blockIp(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "인증이 필요합니다"));
        }
        SecurityEvent event = securityEventService.blockEventManually(id, user.getUsername());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "IP " + event.getSourceIp() + " 방화벽 차단 규칙이 등록되었습니다",
                "firewallRuleCreated", true
        ));
    }

    @PostMapping("/{id}/investigate")
    public ResponseEntity<Map<String, Object>> startInvestigation(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "인증이 필요합니다"));
        }
        SecurityEvent event = securityEventService.markAsInvestigating(id, user.getUsername());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "이벤트 " + event.getId() + " 조사가 시작되었습니다"
        ));
    }
}
