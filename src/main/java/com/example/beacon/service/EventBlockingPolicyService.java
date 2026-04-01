package com.example.beacon.service;

import com.example.beacon.dto.EventBlockingPolicyRequest;
import com.example.beacon.entity.EventBlockingPolicy;
import com.example.beacon.entity.SecurityEvent;
import com.example.beacon.exception.ResourceNotFoundException;
import com.example.beacon.repository.EventBlockingPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventBlockingPolicyService {

    private final EventBlockingPolicyRepository repository;

    @Transactional(readOnly = true)
    public List<EventBlockingPolicy> findAllOrdered() {
        return repository.findAllByOrderByPriorityAscIdAsc();
    }

    /**
     * API로 수신한 보안 이벤트에 적용. 우선순위(priority) 오름차순으로 첫 일치 규칙의 blocked 값을 사용.
     * 일치 규칙이 없으면 false (정보만).
     */
    @Transactional(readOnly = true)
    public boolean resolveBlocked(SecurityEvent event) {
        List<EventBlockingPolicy> rules = repository.findByEnabledTrueOrderByPriorityAscIdAsc();
        String eventType = event.getEventType() != null ? event.getEventType() : "";
        String severity = event.getSeverity() != null ? event.getSeverity() : "";
        String sourceIp = event.getSourceIp() != null ? event.getSourceIp() : "";
        for (EventBlockingPolicy p : rules) {
            if (matches(p, eventType, severity, sourceIp)) {
                return Boolean.TRUE.equals(p.getBlocked());
            }
        }
        return false;
    }

    static boolean matches(EventBlockingPolicy p, String eventType, String severity, String sourceIp) {
        if (!matchesEventType(p.getEventTypePattern(), eventType)) {
            return false;
        }
        if (!isBlank(p.getSeverity()) && !p.getSeverity().equalsIgnoreCase(severity)) {
            return false;
        }
        if (!isBlank(p.getSourceIpPrefix()) && !sourceIp.startsWith(p.getSourceIpPrefix())) {
            return false;
        }
        return true;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static boolean matchesEventType(String pattern, String eventType) {
        if (pattern == null || pattern.isBlank()) {
            return true;
        }
        String p = pattern.trim();
        if ("*".equals(p)) {
            return true;
        }
        if (p.endsWith("*")) {
            String prefix = p.substring(0, p.length() - 1);
            if (prefix.isEmpty()) {
                return true;
            }
            return eventType.toUpperCase().startsWith(prefix.toUpperCase());
        }
        return p.equalsIgnoreCase(eventType);
    }

    @Transactional
    public EventBlockingPolicy create(EventBlockingPolicyRequest req) {
        EventBlockingPolicy e = EventBlockingPolicy.builder()
                .name(req.getName().trim())
                .eventTypePattern(trimOrDefault(req.getEventTypePattern(), "*"))
                .severity(blankToNull(req.getSeverity()))
                .sourceIpPrefix(blankToNull(req.getSourceIpPrefix()))
                .blocked(req.getBlocked())
                .priority(req.getPriority())
                .enabled(req.getEnabled())
                .build();
        return repository.save(e);
    }

    @Transactional
    public EventBlockingPolicy update(Long id, EventBlockingPolicyRequest req) {
        EventBlockingPolicy e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventBlockingPolicy", id));
        e.setName(req.getName().trim());
        e.setEventTypePattern(trimOrDefault(req.getEventTypePattern(), "*"));
        e.setSeverity(blankToNull(req.getSeverity()));
        e.setSourceIpPrefix(blankToNull(req.getSourceIpPrefix()));
        e.setBlocked(req.getBlocked());
        e.setPriority(req.getPriority());
        e.setEnabled(req.getEnabled());
        return repository.save(e);
    }

    @Transactional
    public void delete(Long id) {
        EventBlockingPolicy e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventBlockingPolicy", id));
        repository.delete(e);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String trimOrDefault(String s, String def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        return s.trim();
    }
}
