package com.example.beacon.controller;

import com.example.beacon.dto.AgentHeartbeatRequest;
import com.example.beacon.dto.AgentRegisterRequest;
import com.example.beacon.entity.Agent;
import com.example.beacon.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {
    
    private final AgentService agentService;
    
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerAgent(@RequestBody AgentRegisterRequest request) {
        try {
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
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Agent registered successfully");
            response.put("agentId", agent.getId());
            response.put("agentName", agent.getAgentName());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to register agent: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(@RequestBody AgentHeartbeatRequest request) {
        try {
            agentService.updateHeartbeat(request.getAgentName());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Heartbeat received");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to process heartbeat: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
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
        
        Map<String, Object> response = new HashMap<>();
        response.put("total", allAgents.size());
        response.put("online", onlineCount);
        response.put("offline", allAgents.size() - onlineCount);
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAgent(@PathVariable Long id) {
        try {
            agentService.deleteAgent(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Agent deleted successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to delete agent: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
