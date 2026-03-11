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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(trafficAnalysisService.getTrafficLogs(pageable));
    }
    
    @GetMapping("/anomalous")
    public ResponseEntity<Page<TrafficLog>> getAnomalousTraffic(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
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
    
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeTrafficPattern(@RequestBody List<TrafficLog> logs) {
        return ResponseEntity.ok(trafficAnalysisService.analyzeTrafficPattern(logs));
    }
}
