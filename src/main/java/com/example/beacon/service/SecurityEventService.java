package com.example.beacon.service;

import com.example.beacon.entity.SecurityEvent;
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
    
    @Transactional
    public SecurityEvent createEvent(SecurityEvent event) {
        return securityEventRepository.save(event);
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
                .orElseThrow(() -> new RuntimeException("Event not found: " + eventId));
        event.setStatus("차단됨");
        event.setResolvedAt(LocalDateTime.now());
        event.setHandledBy(handledBy);
        return securityEventRepository.save(event);
    }

    @Transactional
    public SecurityEvent markAsInvestigating(Long eventId, String handledBy) {
        SecurityEvent event = securityEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found: " + eventId));
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
}
