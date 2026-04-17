package com.example.beacon.controller;

import com.example.beacon.dto.AgentHeartbeatRequest;
import com.example.beacon.dto.AgentRegisterRequest;
import com.example.beacon.entity.Agent;
import com.example.beacon.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {
    
    private final AgentService agentService;
    
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerAgent(@Valid @RequestBody AgentRegisterRequest request) {
        Agent agent = agentService.registerOrUpdateAgent(
                request.getAgentName(),
                request.getHostname(),
                request.getIpAddress(),
                request.getOsType(),
                request.getOsVersion(),
                request.getAgentVersion(),
                request.getUsername(),
                request.getMetadata()
        );
        return ResponseEntity.ok(Map.of(
                "success",   true,
                "message",   "Agent registered successfully",
                "agentId",   agent.getId(),
                "agentName", agent.getAgentName()
        ));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(@Valid @RequestBody AgentHeartbeatRequest request) {
        agentService.disconnectAgent(request.getAgentName());
        return ResponseEntity.ok(Map.of("success", true, "message", "Agent disconnected successfully"));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(@Valid @RequestBody AgentHeartbeatRequest request) {
        agentService.updateHeartbeat(request.getAgentName(), request.getMetadata());
        return ResponseEntity.ok(Map.of("success", true, "message", "Heartbeat received"));
    }
    
    @GetMapping
    public ResponseEntity<List<Agent>> getAllAgents() {
        return ResponseEntity.ok(agentService.getAllAgents());
    }
    
    @GetMapping("/online")
    public ResponseEntity<List<Agent>> getOnlineAgents() {
        return ResponseEntity.ok(agentService.getOnlineAgents());
    }
    
    @GetMapping("/offline")
    public ResponseEntity<List<Agent>> getOfflineAgents() {
        return ResponseEntity.ok(agentService.getOfflineAgents());
    }
    
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getAgentCounts() {
        List<Agent> allAgents = agentService.getAllAgents();
        Long onlineCount = agentService.getOnlineAgentCount();
        return ResponseEntity.ok(Map.of(
                "total",   allAgents.size(),
                "online",  onlineCount,
                "offline", allAgents.size() - onlineCount
        ));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAgent(@PathVariable Long id) {
        agentService.deleteAgent(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Agent deleted successfully"));
    }
}
