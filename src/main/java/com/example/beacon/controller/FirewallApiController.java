package com.example.beacon.controller;

import com.example.beacon.dto.FirewallRuleRequest;
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

    @PostMapping
    public ResponseEntity<Map<String, Object>> createRule(@RequestBody FirewallRuleRequest req) {
        FirewallRule rule = firewallService.createRule(req);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "규칙이 추가되었습니다.",
                "id", rule.getId()
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateRule(@PathVariable Long id, @RequestBody FirewallRuleRequest req) {
        FirewallRule rule = firewallService.updateRule(id, req);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "규칙이 수정되었습니다.",
                "id", rule.getId()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable Long id) {
        firewallService.deleteRule(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "규칙이 삭제되었습니다."
        ));
    }

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
