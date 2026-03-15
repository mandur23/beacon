package com.example.beacon.service;

import com.example.beacon.entity.SecurityEvent;
import com.example.beacon.exception.ResourceNotFoundException;
import com.example.beacon.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityEventService {
    
    private final SecurityEventRepository securityEventRepository;
    private final AgentService agentService;
    
    @Transactional
    public SecurityEvent createEvent(SecurityEvent event) {
        SecurityEvent saved = securityEventRepository.save(event);
        
        if (event.getSourceIp() != null) {
            try {
                agentService.incrementEventCountByIp(event.getSourceIp());
            } catch (Exception e) {
                log.warn("Failed to increment agent event count: {}", e.getMessage());
            }
        }
        
        return saved;
    }
    
    @Transactional(readOnly = true)
    public Page<SecurityEvent> getEvents(Pageable pageable) {
        return securityEventRepository.findAll(pageable);
    }
    
    @Transactional(readOnly = true)
    public Page<SecurityEvent> getEventsBySeverity(String severity, Pageable pageable) {
        return securityEventRepository.findBySeverity(severity, pageable);
    }
    
    @Transactional(readOnly = true)
    public List<SecurityEvent> getEventsByDateRange(LocalDateTime start, LocalDateTime end) {
        return securityEventRepository.findByDateRange(start, end);
    }
    
    @Transactional(readOnly = true)
    public Long getBlockedEventsCount(LocalDateTime since) {
        return securityEventRepository.countBlockedEventsSince(since);
    }
    
    @Transactional(readOnly = true)
    public List<SecurityEvent> getUnresolvedEvents() {
        return securityEventRepository.findUnresolvedEvents();
    }
    
    @Transactional(readOnly = true)
    public Map<String, Long> getSeverityCounts() {
        List<Object[]> results = securityEventRepository.countBySeverity();
        Map<String, Long> counts = new HashMap<>();
        for (Object[] result : results) {
            counts.put((String) result[0], (Long) result[1]);
        }
        return counts;
    }
    
    @Transactional(readOnly = true)
    public List<SecurityEvent> getHighRiskEvents(Double minScore) {
        return securityEventRepository.findHighRiskEvents(minScore);
    }
    
    @Transactional
    public SecurityEvent resolveEvent(Long eventId, String handledBy) {
        SecurityEvent event = securityEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("SecurityEvent", eventId));
        event.setStatus("차단됨");
        event.setResolvedAt(LocalDateTime.now());
        event.setHandledBy(handledBy);
        return securityEventRepository.save(event);
    }

    @Transactional
    public SecurityEvent markAsInvestigating(Long eventId, String handledBy) {
        SecurityEvent event = securityEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("SecurityEvent", eventId));
        event.setStatus("조사중");
        event.setHandledBy(handledBy);
        return securityEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<SecurityEvent> getEventsWithFilters(String severity, String search) {
        return securityEventRepository.findWithFilters(severity, search);
    }

    @Transactional(readOnly = true)
    public List<Integer> getHourlyData() {
        LocalDateTime since = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<Object[]> results = securityEventRepository.countByHour(since);
        int[] hourly = new int[24];
        for (Object[] row : results) {
            int hour = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            if (hour >= 0 && hour < 24) hourly[hour] = (int) count;
        }
        List<Integer> list = new ArrayList<>(24);
        for (int v : hourly) list.add(v);
        return list;
    }

    @Transactional(readOnly = true)
    public List<SecurityEvent> getRecentResolved() {
        return securityEventRepository.findTop20ByResolvedAtIsNotNullOrderByResolvedAtDesc();
    }

    @Transactional(readOnly = true)
    public Long countUnresolvedEvents() {
        return securityEventRepository.countUnresolvedEvents();
    }

    /**
     * 보안 점수 (0~100).
     * 미해결 이벤트의 severity 가중치 합산으로 감점:
     *   critical -10 (최대 -40), high -5 (최대 -20),
     *   medium -2 (최대 -15), low -1 (최대 -5)
     */
    @Transactional(readOnly = true)
    public int calculateSecurityScore() {
        long critical = securityEventRepository.countUnresolvedBySeverity("critical");
        long high     = securityEventRepository.countUnresolvedBySeverity("high");
        long medium   = securityEventRepository.countUnresolvedBySeverity("medium");
        long low      = securityEventRepository.countUnresolvedBySeverity("low");

        double penalty = Math.min(critical * 10, 40)
                       + Math.min(high     *  5, 20)
                       + Math.min(medium   *  2, 15)
                       + Math.min(low      *  1,  5);

        return (int) Math.max(0, Math.min(100, 100 - penalty));
    }

    /** 전체 이벤트 대비 해결된 이벤트 비율 (문자열, ex. "96%") */
    @Transactional(readOnly = true)
    public String getResolveRate() {
        long total = securityEventRepository.count();
        if (total == 0) return "100%";
        long resolved = securityEventRepository.countResolvedEvents();
        int rate = (int) Math.round((double) resolved / total * 100.0);
        return rate + "%";
    }

    /** 해결된 이벤트의 평균 처리 시간 (문자열, ex. "12분" / "1시간 5분") */
    @Transactional(readOnly = true)
    public String getAverageResponseTime() {
        Double avgMinutes = securityEventRepository.averageResolutionMinutes();
        if (avgMinutes == null) return "N/A";
        long mins = Math.round(avgMinutes);
        if (mins < 60) return mins + "분";
        return (mins / 60) + "시간 " + (mins % 60) + "분";
    }

    /**
     * 최근 3개월 이벤트 유형별 비율 목록.
     * 상위 3개 유형을 개별 항목으로, 나머지는 "기타"로 묶어 반환한다.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getThreatDistribution() {
        LocalDateTime since = LocalDateTime.now().minusMonths(3);
        List<Object[]> rows = securityEventRepository.countByEventTypeSince(since);

        long total = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        if (total == 0) {
            return List.of(Map.of("label", "데이터 없음", "pct", 100, "color", "#4a5570"));
        }

        String[] colors = {"#ff3d5a", "#ff8c42", "#ffd166", "#00e5ff"};
        List<Map<String, Object>> dist = new ArrayList<>();
        long remaining = total;
        int i = 0;

        for (Object[] row : rows) {
            if (i >= 3) break;
            String type  = (String) row[0];
            long   count = ((Number) row[1]).longValue();
            int    pct   = (int) Math.round((double) count / total * 100.0);
            dist.add(Map.of("label", type, "pct", pct, "color", colors[i]));
            remaining -= count;
            i++;
        }

        if (remaining > 0) {
            int pct = (int) Math.round((double) remaining / total * 100.0);
            dist.add(Map.of("label", "기타", "pct", pct, "color", colors[3]));
        }

        return dist;
    }

    /**
     * 최근 N개월 각 월의 보안 점수 목록 (오래된 달 → 현재 달 순서).
     * 해당 월의 severity별 이벤트 수로 감점:
     *   critical -5 (최대 -30), high -2 (최대 -20), medium -1 (최대 -10)
     */
    @Transactional(readOnly = true)
    public List<Integer> getMonthlyScores(int months) {
        List<Integer> scores = new ArrayList<>(months);
        LocalDateTime now = LocalDateTime.now();

        for (int i = months - 1; i >= 0; i--) {
            LocalDateTime start = now.minusMonths(i)
                    .withDayOfMonth(1).toLocalDate().atStartOfDay();
            LocalDateTime end = start.plusMonths(1);

            long critical = securityEventRepository.countBySeverityBetween("critical", start, end);
            long high     = securityEventRepository.countBySeverityBetween("high",     start, end);
            long medium   = securityEventRepository.countBySeverityBetween("medium",   start, end);

            double penalty = Math.min(critical * 5, 30)
                           + Math.min(high     * 2, 20)
                           + Math.min(medium   * 1, 10);

            scores.add((int) Math.max(0, Math.min(100, 100 - penalty)));
        }

        return scores;
    }
}
