package com.example.beacon.controller;

import com.example.beacon.entity.FirewallRule;
import com.example.beacon.service.FirewallService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/firewall")
@RequiredArgsConstructor
public class FirewallApiController {

    private final FirewallService firewallService;

    @PostMapping("/toggle/{id}")
    public ResponseEntity<Map<String, Object>> toggleRule(@PathVariable Long id) {
        FirewallRule rule = firewallService.toggleRule(id);
        return ResponseEntity.ok(Map.of(
                "id", rule.getId(),
                "enabled", rule.getEnabled(),
                "message", rule.getEnabled() ? "규칙이 활성화되었습니다" : "규칙이 비활성화되었습니다"
        ));
    }
}
