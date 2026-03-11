package com.example.beacon.service;

import com.example.beacon.entity.TrafficLog;
import com.example.beacon.repository.TrafficLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrafficAnalysisService {
    
    private final TrafficLogRepository trafficLogRepository;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public TrafficLog logTraffic(TrafficLog trafficLog) {
        return trafficLogRepository.save(trafficLog);
    }
    
    @Transactional(readOnly = true)
    public Page<TrafficLog> getTrafficLogs(Pageable pageable) {
        return trafficLogRepository.findAll(pageable);
    }
    
    @Transactional(readOnly = true)
    public Page<TrafficLog> getAnomalousTraffic(Pageable pageable) {
        return trafficLogRepository.findByIsAnomaly(true, pageable);
    }
    
    @Transactional(readOnly = true)
    public List<TrafficLog> getTrafficByIp(String sourceIp) {
        return trafficLogRepository.findBySourceIp(sourceIp);
    }
    
    @Transactional(readOnly = true)
    public List<TrafficLog> getTrafficByDateRange(LocalDateTime start, LocalDateTime end) {
        return trafficLogRepository.findByDateRange(start, end);
    }
    
    @Transactional(readOnly = true)
    public Long getTotalBytesTransferred(LocalDateTime since) {
        Long total = trafficLogRepository.getTotalBytesTransferredSince(since);
        return total != null ? total : 0L;
    }
    
    @Transactional(readOnly = true)
    public Map<String, Object> getProtocolStatistics() {
        List<Object[]> results = trafficLogRepository.getProtocolStatistics();
        Map<String, Object> stats = new HashMap<>();
        
        for (Object[] result : results) {
            String protocol = (String) result[0];
            Long count = (Long) result[1];
            Long bytes = (Long) result[2];
            
            Map<String, Object> protocolStats = new HashMap<>();
            protocolStats.put("count", count);
            protocolStats.put("bytes", bytes);
            stats.put(protocol, protocolStats);
        }
        
        return stats;
    }
    
    @Transactional(readOnly = true)
    public List<TrafficLog> getHighAnomalyScoreTraffic(Double minScore) {
        return trafficLogRepository.findAnomalousTraffic(minScore);
    }
    
    @Transactional
    public TrafficLog markAsAnomaly(Long trafficId, Double score, String reason) {
        TrafficLog traffic = trafficLogRepository.findById(trafficId)
                .orElseThrow(() -> new RuntimeException("Traffic log not found: " + trafficId));
        traffic.setIsAnomaly(true);
        traffic.setAnomalyScore(score);
        traffic.setAnomalyReason(reason);
        return trafficLogRepository.save(traffic);
    }
    
    public Map<String, Object> analyzeTrafficPattern(List<TrafficLog> logs) {
        Map<String, Object> analysis = new HashMap<>();
        
        long totalBytes = logs.stream().mapToLong(TrafficLog::getBytesTransferred).sum();
        long totalPackets = logs.stream().mapToLong(TrafficLog::getPacketsTransferred).sum();
        double avgDuration = logs.stream().mapToInt(TrafficLog::getDuration).average().orElse(0.0);
        long anomalyCount = logs.stream().filter(TrafficLog::getIsAnomaly).count();
        
        analysis.put("totalBytes", totalBytes);
        analysis.put("totalPackets", totalPackets);
        analysis.put("avgDuration", avgDuration);
        analysis.put("anomalyCount", anomalyCount);
        analysis.put("anomalyRate", logs.isEmpty() ? 0.0 : (double) anomalyCount / logs.size());
        
        return analysis;
    }
}
