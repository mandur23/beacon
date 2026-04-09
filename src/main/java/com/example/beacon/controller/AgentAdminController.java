package com.example.beacon.controller;

import com.example.beacon.dto.AssignAgentOwnerRequest;
import com.example.beacon.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/agents")
@RequiredArgsConstructor
public class AgentAdminController {

    private final AgentService agentService;

    @PutMapping("/{id}/owner")
    public ResponseEntity<Map<String, Object>> assignOwner(
            @PathVariable Long id,
            @Valid @RequestBody AssignAgentOwnerRequest body) {
        agentService.setOwnerUser(id, body.getUserId());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "에이전트 소유자가 갱신되었습니다."
        ));
    }
}
