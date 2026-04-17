package com.example.beacon.service;

import com.example.beacon.entity.Agent;
import com.example.beacon.entity.FirewallAgentCommand;
import com.example.beacon.entity.TrafficLog;
import com.example.beacon.exception.ResourceNotFoundException;
import com.example.beacon.repository.AgentRepository;
import com.example.beacon.repository.FirewallAgentCommandRepository;
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
    private final AgentService agentService;
    private final AgentRepository agentRepository;
    private final FirewallAgentCommandRepository commandRepository;

    @Transactional
    public void scheduleProcessKill(String agentName, Integer pid, String processName) throws Exception {
        Agent agent = agentRepository.findByAgentName(agentName)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", agentName));

        Map<String, Object> payload = new HashMap<>();
        payload.put("pid", pid);
        payload.put("process_name", processName);

        FirewallAgentCommand command = FirewallAgentCommand.builder()
                .agentId(agent.getId())
                .commandId(java.util.UUID.randomUUID().toString())
                .revision(System.currentTimeMillis())
                .action("KILL_PROCESS")
                .payload(objectMapper.writeValueAsString(payload))
                .build();

        commandRepository.save(command);
        log.info("Process kill command scheduled: agent={}, pid={}, proc={}", agentName, pid, processName);
    }

    /**
     * 트래픽 로그를 저장하고, sourceIp로 식별되는 에이전트의
     * totalTrafficLogs 카운터를 1 증가시킨다.
     * 에이전트 연동 실패는 로그만 남기고 저장 결과에 영향을 주지 않는다.
     */
    @Transactional
    public TrafficLog logTraffic(TrafficLog trafficLog) {
        // [작업 3] 실시간 이상 탐지 규칙 엔진 (포트 및 패턴 기반)
        if (isAnomalousPattern(trafficLog)) {
            trafficLog.setIsAnomaly(true);
            trafficLog.setAnomalyScore(9.5);
            trafficLog.setAnomalyReason("위험 포트 감지 또는 비정상 패턴 (Rule-based Detection)");
        }

        TrafficLog saved = trafficLogRepository.save(trafficLog);
        
        // 에이전트 카운터 업데이트 로직 유지
        if (trafficLog.getAgentName() != null) {
            try {
                agentService.incrementTrafficCount(trafficLog.getAgentName());
            } catch (Exception e) {
                log.warn("Failed to increment agent traffic count for agentName={}: {}",
                        trafficLog.getAgentName(), e.getMessage());
            }
        } else if (trafficLog.getSourceIp() != null) {
            try {
                agentService.incrementTrafficCountByIp(trafficLog.getSourceIp());
            } catch (Exception e) {
                log.warn("Failed to increment agent traffic count for ip={}: {}",
                        trafficLog.getSourceIp(), e.getMessage());
            }
        }

        return saved;
    }

    private boolean isAnomalousPattern(TrafficLog log) {
        int dPort = log.getDestinationPort();
        int sPort = log.getSourcePort();
        
        // 1. 알려진 공격/악성 포트 블랙리스트
        List<Integer> backdoors = List.of(4444, 6667, 31337, 81, 1337);
        if (backdoors.contains(dPort) || backdoors.contains(sPort)) return true;
        
        // 2. 비정상적인 대용량 전송 (단건 100MB 초과)
        if (log.getBytesTransferred() > 100 * 1024 * 1024) return true;
        
        return false;
    }
    
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getProcessStats(String agentName) {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        List<Object[]> results = trafficLogRepository.getProcessTrafficStats(agentName, since);
        
        return results.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            String rawProcess = (String) r[0];
            // JSON_EXTRACT 결과인 "process.exe"에서 따옴표 제거
            map.put("processName", rawProcess != null ? rawProcess.replace("\"", "") : "Unknown");
            map.put("totalBytes", r[1]);
            return map;
        }).toList();
    }
    
    @Transactional(readOnly = true)
    public Page<TrafficLog> getTrafficLogs(String agentName, Pageable pageable) {
        if (agentName != null && !agentName.isEmpty()) {
            return trafficLogRepository.findByAgentName(agentName, pageable);
        }
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
                .orElseThrow(() -> new ResourceNotFoundException("TrafficLog", trafficId));
        traffic.setIsAnomaly(true);
        traffic.setAnomalyScore(score);
        traffic.setAnomalyReason(reason);
        return trafficLogRepository.save(traffic);
    }
    
    /**
     * 기간 내 평균 연결 지속 시간을 "Xms" / "Xs" 형식 문자열로 반환한다.
     * 데이터 없을 경우 "N/A" 반환.
     */
    @Transactional(readOnly = true)
    public String getAverageLatency(LocalDateTime since) {
        Double avg = trafficLogRepository.averageDurationSince(since);
        if (avg == null) return "N/A";
        long ms = Math.round(avg);
        return ms < 1000 ? ms + "ms" : String.format("%.1fs", ms / 1000.0);
    }

    /**
     * 평균 지연 값을 0~100 % 게이지용 정수로 반환한다.
     * 기준: 100ms = 100%
     */
    @Transactional(readOnly = true)
    public int getAverageLatencyPct(LocalDateTime since) {
        Double avg = trafficLogRepository.averageDurationSince(since);
        if (avg == null) return 0;
        return (int) Math.min(100, Math.round(avg / 100.0 * 100.0));
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
