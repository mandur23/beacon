package com.example.beacon.controller;

import com.example.beacon.dto.SecurityEventCreateRequest;
import com.example.beacon.entity.SecurityEvent;
import com.example.beacon.service.SecurityEventService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/security-events")
@RequiredArgsConstructor
public class SecurityEventController {

    private final SecurityEventService securityEventService;

    @GetMapping
    public ResponseEntity<Page<SecurityEvent>> getEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(securityEventService.getEvents(pageable));
    }

    @GetMapping("/severity/{severity}")
    public ResponseEntity<Page<SecurityEvent>> getEventsBySeverity(
            @PathVariable String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(securityEventService.getEventsBySeverity(severity, pageable));
    }

    @GetMapping("/range")
    public ResponseEntity<List<SecurityEvent>> getEventsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(securityEventService.getEventsByDateRange(start, end));
    }

    @GetMapping("/stats/blocked")
    public ResponseEntity<Map<String, Object>> getBlockedEventsCount(
            @RequestParam(defaultValue = "24") int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Long count = securityEventService.getBlockedEventsCount(since);
        return ResponseEntity.ok(Map.of("count", count, "since", since, "hours", hours));
    }

    @GetMapping("/unresolved")
    public ResponseEntity<List<SecurityEvent>> getUnresolvedEvents() {
        return ResponseEntity.ok(securityEventService.getUnresolvedEvents());
    }

    @GetMapping("/stats/severity")
    public ResponseEntity<Map<String, Long>> getSeverityCounts() {
        return ResponseEntity.ok(securityEventService.getSeverityCounts());
    }

    @GetMapping("/high-risk")
    public ResponseEntity<List<SecurityEvent>> getHighRiskEvents(
            @RequestParam(defaultValue = "7.0") Double minScore) {
        return ResponseEntity.ok(securityEventService.getHighRiskEvents(minScore));
    }

    /**
     * 에이전트가 전송한 보안 이벤트를 수신한다.
     * 정책 평가 → 방화벽 규칙 동기화 → 이벤트 저장 오케스트레이션은
     * SecurityEventService.createAgentEvent() 가 단일 트랜잭션으로 처리한다.
     */
    @PostMapping
    public ResponseEntity<SecurityEvent> createEvent(
            @Valid @RequestBody SecurityEventCreateRequest request) {
        return ResponseEntity.ok(securityEventService.createAgentEvent(request));
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<SecurityEvent> resolveEvent(
            @PathVariable Long id,
            @RequestParam String handledBy) {
        return ResponseEntity.ok(securityEventService.resolveEvent(id, handledBy));
    }
}
