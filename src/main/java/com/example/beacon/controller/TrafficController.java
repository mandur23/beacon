package com.example.beacon.controller;

import com.example.beacon.dto.TrafficLogRequest;
import com.example.beacon.entity.TrafficLog;
import com.example.beacon.service.TrafficAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/traffic")
@RequiredArgsConstructor
public class TrafficController {
    
    private final TrafficAnalysisService trafficAnalysisService;
    
    @GetMapping
    public ResponseEntity<Page<TrafficLog>> getTrafficLogs(
            @RequestParam(required = false) String agentName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(trafficAnalysisService.getTrafficLogs(agentName, pageable));
    }
    
    @GetMapping("/anomalous")
    public ResponseEntity<Page<TrafficLog>> getAnomalousTraffic(
            @RequestParam(required = false) String agentName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        // 여기도 agentName 필터가 필요할 수 있으나 우선 기본 getTrafficLogs 위주로 작업
        return ResponseEntity.ok(trafficAnalysisService.getAnomalousTraffic(pageable));
    }
    
    @GetMapping("/ip/{sourceIp}")
    public ResponseEntity<List<TrafficLog>> getTrafficByIp(@PathVariable String sourceIp) {
        return ResponseEntity.ok(trafficAnalysisService.getTrafficByIp(sourceIp));
    }
    
    @GetMapping("/range")
    public ResponseEntity<List<TrafficLog>> getTrafficByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        
        return ResponseEntity.ok(trafficAnalysisService.getTrafficByDateRange(start, end));
    }
    
    @GetMapping("/stats/bandwidth")
    public ResponseEntity<Map<String, Object>> getBandwidthStats(
            @RequestParam(defaultValue = "24") int hours) {
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Long totalBytes = trafficAnalysisService.getTotalBytesTransferred(since);
        
        return ResponseEntity.ok(Map.of(
            "totalBytes", totalBytes,
            "totalMB", totalBytes / (1024.0 * 1024.0),
            "totalGB", totalBytes / (1024.0 * 1024.0 * 1024.0),
            "since", since,
            "hours", hours
        ));
    }
    
    @GetMapping("/stats/protocols")
    public ResponseEntity<Map<String, Object>> getProtocolStatistics() {
        return ResponseEntity.ok(trafficAnalysisService.getProtocolStatistics());
    }
    
    @GetMapping("/high-anomaly")
    public ResponseEntity<List<TrafficLog>> getHighAnomalyScoreTraffic(
            @RequestParam(defaultValue = "7.0") Double minScore) {
        
        return ResponseEntity.ok(trafficAnalysisService.getHighAnomalyScoreTraffic(minScore));
    }
    
    @PostMapping
    public ResponseEntity<TrafficLog> logTraffic(@RequestBody TrafficLogRequest request) {
        TrafficLog trafficLog = TrafficLog.builder()
                .sourceIp(request.getSourceIp())
                .destinationIp(request.getDestinationIp())
                .sourcePort(request.getSourcePort())
                .destinationPort(request.getDestinationPort())
                .protocol(request.getProtocol())
                .bytesTransferred(request.getBytesTransferred())
                .packetsTransferred(request.getPacketsTransferred())
                .duration(request.getDuration())
                .rawData(request.getRawData())
                .agentName(request.getAgentName())
                .isAnomaly(false)
                .anomalyScore(0.0)
                .build();
        
        return ResponseEntity.ok(trafficAnalysisService.logTraffic(trafficLog));
    }
    
    @PutMapping("/{id}/mark-anomaly")
    public ResponseEntity<TrafficLog> markAsAnomaly(
            @PathVariable Long id,
            @RequestParam Double score,
            @RequestParam String reason) {
        
        return ResponseEntity.ok(trafficAnalysisService.markAsAnomaly(id, score, reason));
    }
    
    @GetMapping("/stats/processes")
    public ResponseEntity<List<Map<String, Object>>> getProcessStats(
            @RequestParam(required = false) String agentName) {
        return ResponseEntity.ok(trafficAnalysisService.getProcessStats(agentName));
    }

    @PostMapping("/kill-process")
    public ResponseEntity<Map<String, String>> killAgentProcess(
            @RequestParam String agentName,
            @RequestParam Integer pid,
            @RequestParam String processName) {
        
        // FirewallAgentCommand 시스템과 연동하여 명령 큐에 적재
        // 실제 구현은 FirewallService 또는 AgentService의 명령 하달 로직 호출
        try {
            trafficAnalysisService.scheduleProcessKill(agentName, pid, processName);
            return ResponseEntity.ok(Map.of("status", "success", "message", "프로세스 종료 명령이 큐에 적재되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeTrafficPattern(@RequestBody List<TrafficLog> logs) {
        return ResponseEntity.ok(trafficAnalysisService.analyzeTrafficPattern(logs));
    }
}
