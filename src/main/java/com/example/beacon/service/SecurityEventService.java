package com.example.beacon.service;

import com.example.beacon.dto.EventBlockingPolicyRequest;
import com.example.beacon.dto.SecurityEventCreateRequest;
import com.example.beacon.entity.EventSource;
import com.example.beacon.entity.SecurityEvent;
import com.example.beacon.exception.ResourceNotFoundException;
import com.example.beacon.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityEventService {
    private static final Duration LOW_SIGNAL_ALERT_AGGREGATION_WINDOW = Duration.ofMinutes(5);
    private static final Duration BIOMETRIC_AGGREGATION_WINDOW = Duration.ofMinutes(1);
    
    private final SecurityEventRepository securityEventRepository;
    private final AgentService agentService;
    private final FirewallService firewallService;
    private final FirewallHitsBatchService firewallHitsBatchService;
    private final EventBlockingPolicyService eventBlockingPolicyService;
    private final SseAlertService sseAlertService;
    private final EventCorrelationService eventCorrelationService;

    @Transactional
    public SecurityEvent createEvent(SecurityEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("SecurityEvent must not be null");
        }
        normalizeInboundEvent(event);
        if (!shouldPersistEvent(event)) {
            event.setCorrelationStatus("SKIPPED_STORAGE");
            return event;
        }
        Optional<SecurityEvent> aggregated = tryAggregateEvent(event);
        if (aggregated.isPresent()) {
            return aggregated.get();
        }
        // blocked=true 로 결정된 이벤트는 상태값도 "차단됨"으로 정합성 유지
        if (Boolean.TRUE.equals(event.getBlocked())
                && (event.getStatus() == null
                || event.getStatus().isBlank()
                || "pending".equalsIgnoreCase(event.getStatus())
                || "탐지됨".equals(event.getStatus()))) {
            event.setStatus("차단됨");
        }

        SecurityEvent saved = securityEventRepository.save(event);
        saved = correlateSafely(saved);

        broadcastHighSeverityAlert(saved);

        if (event.getSourceIp() != null) {
            try {
                agentService.incrementEventCountByIp(event.getSourceIp());
            } catch (Exception e) {
                log.debug("Failed to increment agent event count: {}", e.getMessage());
            }
            // 메모리 카운터에만 기록, 3초 주기 배치로 DB flush
            firewallHitsBatchService.recordHit(event.getSourceIp());
        }

        return saved;
    }
    
    /**
     * 에이전트 API를 통해 수신된 이벤트를 저장한다.
     * 컨트롤러에서 분산되어 있던 오케스트레이션을 단일 트랜잭션으로 통합한다:
     *  1. EventBlockingPolicy 매칭으로 blocked 여부 결정
     *  2. blocked=true 이면 방화벽 차단 규칙 자동 등록 (REQUIRES_NEW)
     *  3. 이벤트 저장 (createEvent)
     */
    @Transactional
    public SecurityEvent createAgentEvent(SecurityEventCreateRequest request) {
        // 데이터 수신 시 자동으로 에이전트 생존 신고(Heartbeat) 갱신
        if (request.getAgentName() != null && !request.getAgentName().isBlank()) {
            agentService.updateHeartbeat(request.getAgentName());
        }
        
        SecurityEvent event = request.toEntity();
        normalizeInboundEvent(event);
        boolean blocked = eventBlockingPolicyService.resolveBlocked(event);
        event.setBlocked(blocked);

        if (blocked && event.getSourceIp() != null && !event.getSourceIp().isBlank()) {
            try {
                String reason = "정책 자동 차단: " + event.getEventType()
                        + " [severity=" + event.getSeverity() + "]";
                firewallService.createBlockRuleForIp(event.getSourceIp(), reason, "system-policy");
            } catch (Exception e) {
                log.warn("Auto block rule failed for ip={}, event will still be stored: {}",
                        event.getSourceIp(), e.getMessage());
            }
        }

        return createEvent(event);
    }

    private void normalizeInboundEvent(SecurityEvent event) {
        if (event == null) {
            return;
        }

        preserveOriginalJudgement(event);

        if (isEnginePriorityEvent(event)) {
            return;
        }

        if (isIgnorableMiscSignal(event)) {
            event.setSeverity("LOW");
            event.setRiskScore(15.0);
            if (event.getStatus() == null || event.getStatus().isBlank()) {
                event.setStatus("pending");
            }
            if (event.getBlocked() == null) {
                event.setBlocked(false);
            }
        }

        if (isExternalIpLookupSignal(event)) {
            event.setSeverity("LOW");
            event.setRiskScore(25.0);
            if (event.getStatus() == null || event.getStatus().isBlank()) {
                event.setStatus("pending");
            }
            if (event.getBlocked() == null) {
                event.setBlocked(false);
            }
        }

        if (isBenignTextFileChange(event)) {
            event.setSeverity("LOW");
            event.setRiskScore(10.0);
            if (event.getStatus() == null || event.getStatus().isBlank()) {
                event.setStatus("pending");
            }
            if (event.getBlocked() == null) {
                event.setBlocked(false);
            }
        }
    }

    private boolean isExternalIpLookupSignal(SecurityEvent event) {
        String eventType = safeLower(event.getEventType());
        String description = safeLower(event.getDescription());
        String metadata = safeLower(event.getMetadata());

        boolean idsLikeEvent = eventType.contains("alert");
        boolean externalLookupDescription = description.contains("external ip lookup")
                || description.contains("device retrieving external ip address detected")
                || description.contains("ip-api.com");
        boolean externalLookupMetadata = metadata.contains("external ip lookup")
                || metadata.contains("device retrieving external ip address detected")
                || metadata.contains("ip-api.com")
                || metadata.contains("\"signature_severity\":[\"informational\"]")
                || metadata.contains("\"signature_severity\": [\"informational\"]");

        return idsLikeEvent && (externalLookupDescription || externalLookupMetadata);
    }

    private boolean isIgnorableMiscSignal(SecurityEvent event) {
        String eventType = safeLower(event.getEventType());
        String description = safeLower(event.getDescription());
        String metadata = safeLower(event.getMetadata());

        boolean idsLikeEvent = eventType.contains("alert");
        boolean miscNoise = description.contains("misc activity")
                || description.contains("argotunnel")
                || metadata.contains("misc activity")
                || metadata.contains("argotunnel");

        return idsLikeEvent && miscNoise;
    }

    private boolean isBenignTextFileChange(SecurityEvent event) {
        String eventType = safeLower(event.getEventType());
        String description = safeLower(event.getDescription());

        if (!(eventType.equals("file_created") || eventType.equals("file_modified"))) {
            return false;
        }

        boolean textLike = description.contains(".txt")
                || description.contains(".md")
                || description.contains(".rtf");
        boolean userLocation = description.contains("\\desktop\\")
                || description.contains("\\downloads\\")
                || description.contains("\\documents\\");
        boolean startupLocation = description.contains("\\startup\\")
                || description.contains("\\start menu\\programs\\startup\\");

        return textLike && userLocation && !startupLocation;
    }

    private void preserveOriginalJudgement(SecurityEvent event) {
        if (event.getOriginalSeverity() == null || event.getOriginalSeverity().isBlank()) {
            event.setOriginalSeverity(event.getSeverity());
        }
        if (event.getOriginalRiskScore() == null || event.getOriginalRiskScore() <= 0) {
            event.setOriginalRiskScore(event.getRiskScore());
        }
        if (event.getOriginalSource() == null || event.getOriginalSource().isBlank()) {
            event.setOriginalSource(event.getSource() != null ? event.getSource().name() : null);
        }
    }

    private boolean isEnginePriorityEvent(SecurityEvent event) {
        String eventType = safeLower(event.getEventType());
        String originalSeverity = safeLower(event.getOriginalSeverity());

        if ("wazuh_alert".equals(eventType)) {
            Integer ruleLevel = extractWazuhRuleLevel(event.getMetadata());
            return (ruleLevel != null && ruleLevel >= 10)
                    || "high".equals(originalSeverity)
                    || "critical".equals(originalSeverity);
        }

        if (eventType.contains("alert")) {
            return "high".equals(originalSeverity) || "critical".equals(originalSeverity);
        }

        return false;
    }

    private Integer extractWazuhRuleLevel(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }

        String marker = "\"level\":";
        int start = metadata.indexOf(marker);
        if (start < 0) {
            return null;
        }

        start += marker.length();
        while (start < metadata.length() && Character.isWhitespace(metadata.charAt(start))) {
            start++;
        }

        int end = start;
        while (end < metadata.length() && Character.isDigit(metadata.charAt(end))) {
            end++;
        }

        if (end == start) {
            return null;
        }

        try {
            return Integer.parseInt(metadata.substring(start, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean shouldPersistEvent(SecurityEvent event) {
        String eventType = safeLower(event.getEventType());

        if (eventType.isBlank()) {
            return true;
        }

        if (eventType.equals("ids_alert")) {
            return false;
        }

        return !eventType.equals("ids_http")
                && !eventType.equals("ids_fileinfo")
                && !eventType.equals("ids_quic")
                && !eventType.equals("http")
                && !eventType.equals("fileinfo")
                && !eventType.equals("quic");
    }

    private Optional<SecurityEvent> tryAggregateEvent(SecurityEvent incomingEvent) {
        if (incomingEvent == null) {
            return Optional.empty();
        }

        if (isExternalIpLookupSignal(incomingEvent)) {
            LocalDateTime since = LocalDateTime.now().minus(LOW_SIGNAL_ALERT_AGGREGATION_WINDOW);
            return securityEventRepository.findRecentAlertAggregateCandidates(
                            incomingEvent.getEventType(),
                            incomingEvent.getSourceIp(),
                            incomingEvent.getDestinationIp(),
                            incomingEvent.getDescription(),
                            since)
                    .stream()
                    .findFirst()
                    .map(existing -> mergeRepeatedEvent(existing, incomingEvent));
        }

        if ("biometric_anomaly".equals(safeLower(incomingEvent.getEventType()))) {
            LocalDateTime since = LocalDateTime.now().minus(BIOMETRIC_AGGREGATION_WINDOW);
            return securityEventRepository.findRecentTypeBySourceIp(
                            incomingEvent.getEventType(),
                            incomingEvent.getSourceIp(),
                            since)
                    .stream()
                    .findFirst()
                    .map(existing -> mergeRepeatedEvent(existing, incomingEvent));
        }

        return Optional.empty();
    }

    private SecurityEvent mergeRepeatedEvent(SecurityEvent existingEvent, SecurityEvent incomingEvent) {
        int repeatCount = existingEvent.getRepeatCount() != null && existingEvent.getRepeatCount() > 0
                ? existingEvent.getRepeatCount()
                : 1;
        existingEvent.setRepeatCount(repeatCount + 1);
        existingEvent.setLastSeenAt(LocalDateTime.now());

        if (existingEvent.getRiskScore() == null
                || (incomingEvent.getRiskScore() != null && existingEvent.getRiskScore() < incomingEvent.getRiskScore())) {
            existingEvent.setRiskScore(incomingEvent.getRiskScore());
        }
        if ((existingEvent.getMetadata() == null || existingEvent.getMetadata().isBlank())
                && incomingEvent.getMetadata() != null && !incomingEvent.getMetadata().isBlank()) {
            existingEvent.setMetadata(incomingEvent.getMetadata());
        }
        return securityEventRepository.save(existingEvent);
    }

    private SecurityEvent correlateSafely(SecurityEvent saved) {
        try {
            return eventCorrelationService.correlate(saved);
        } catch (Exception e) {
            log.warn("Event correlation failed for event id={}: {}", saved.getId(), e.getMessage(), e);
            if (saved.getCorrelationStatus() == null || saved.getCorrelationStatus().isBlank()) {
                saved.setCorrelationStatus("CORRELATION_ERROR");
            }
            return securityEventRepository.save(saved);
        }
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private void broadcastHighSeverityAlert(SecurityEvent saved) {
        if (saved == null) {
            return;
        }
        String severity = saved.getSeverity();
        if (severity == null
                || (!"HIGH".equalsIgnoreCase(severity) && !"CRITICAL".equalsIgnoreCase(severity))) {
            return;
        }
        String srcIp = saved.getSourceIp() != null ? saved.getSourceIp() : "Unknown";
        String desc = saved.getDescription() != null ? saved.getDescription() : saved.getEventType();
        String alertMsg = String.format("%s: 공격자 IP %s - %s", severity.toUpperCase(), srcIp, desc);
        if (saved.getIncidentType() != null && !saved.getIncidentType().isBlank()) {
            alertMsg = alertMsg + " | Incident=" + saved.getIncidentType();
        }
        try {
            sseAlertService.broadcast(
                    saved.getEventType(),
                    severity,
                    alertMsg,
                    saved.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to broadcast SSE alert: {}", e.getMessage());
        }
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
        event.setBlocked(true);
        event.setResolvedAt(LocalDateTime.now());
        event.setHandledBy(handledBy);
        return securityEventRepository.save(event);
    }

    @Transactional
    public SecurityEvent whitelistEvent(Long eventId, String handledBy) {
        SecurityEvent event = securityEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("SecurityEvent", eventId));
        event.setStatus("예외처리");
        event.setResolvedAt(LocalDateTime.now());
        event.setHandledBy(handledBy);
        SecurityEvent saved = securityEventRepository.save(event);

        // 예외 차단 정책 등록 (동일 이벤트 유형, IP에 대해 차단안함 예외 정책 우선 추가)
        EventBlockingPolicyRequest policyReq = new EventBlockingPolicyRequest();
        policyReq.setName("오탐 예외 - " + (event.getEventType() != null ? event.getEventType() : "이벤트"));
        policyReq.setEventTypePattern(event.getEventType() != null ? event.getEventType() : "*");
        policyReq.setSeverity(event.getSeverity());
        policyReq.setSourceIpPrefix(event.getSourceIp());
        policyReq.setBlocked(false); // 탐지만 하고 차단 우회
        policyReq.setPriority(1); // 최우선순위로 처리
        policyReq.setEnabled(true);
        try {
            eventBlockingPolicyService.create(policyReq);
        } catch (Exception e) {
            log.warn("Failed to create whitelist blocking policy for event id={}: {}", eventId, e.getMessage());
        }

        if (event.getSourceIp() != null && !event.getSourceIp().isBlank()) {
            try {
                firewallService.deleteBlockRuleForIp(event.getSourceIp(), handledBy);
                firewallService.createAllowRuleForIp(event.getSourceIp(), "오탐 예외 등록 - " + handledBy, handledBy);
            } catch (Exception e) {
                log.warn("Failed to update firewall rules for whitelist event id={}, ip={}: {}",
                        eventId, event.getSourceIp(), e.getMessage());
            }
        }

        return saved;
    }

    /**
     * 관리자가 위협 화면에서 수동으로 IP를 차단할 때 호출한다.
     * 단일 트랜잭션 안에서:
     *  1. sourceIp 유효성을 DB 쓰기 전에 검증해 불필요한 롤백을 방지한다.
     *  2. 이벤트 상태를 "차단됨"으로 변경하고 source=MANUAL을 DB에 저장한다.
     *  3. 해당 IP의 방화벽 차단 규칙을 생성한다(이미 있으면 재사용).
     * 규칙 생성이 실패하면 이벤트 상태 변경도 함께 롤백된다.
     *
     * @throws IllegalArgumentException 이벤트의 sourceIp가 없거나 비어 있는 경우 (→ 400)
     */
    @Transactional
    public SecurityEvent blockEventManually(Long eventId, String handledBy) {
        SecurityEvent event = securityEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("SecurityEvent", eventId));

        // DB 쓰기 전 사전 검증: 레거시·비정상 데이터 방어
        if (event.getSourceIp() == null || event.getSourceIp().isBlank()) {
            throw new IllegalArgumentException(
                    "이벤트 #" + eventId + "의 소스 IP가 없어 방화벽 규칙을 등록할 수 없습니다.");
        }

        event.setStatus("차단됨");
        event.setResolvedAt(LocalDateTime.now());
        event.setHandledBy(handledBy);
        event.setSource(EventSource.MANUAL);
        SecurityEvent saved = securityEventRepository.save(event);

        String reason = "이벤트 #" + eventId + " 수동 차단 (" + saved.getEventType() + ")";
        firewallService.createBlockRuleForIp(saved.getSourceIp(), reason, handledBy);

        return saved;
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

    /** 지정한 날짜(자정~다음날 자정) 구간의 이벤트만 페이징 조회 */
    @Transactional(readOnly = true)
    public List<SecurityEvent> getEventsWithFiltersForDate(String severity, String search, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return securityEventRepository.findWithFiltersAndDateRange(severity, search, start, end);
    }

    /** 고급 필터와 페이지네이션을 지원하는 조회 (date가 null이면 날짜 필터 무시) */
    @Transactional(readOnly = true)
    public Page<SecurityEvent> getEventsWithFiltersPaged(String severity, String search,
                                                         String agentName, String source,
                                                         boolean riskOnly, LocalDate date,
                                                         Pageable pageable) {
        LocalDateTime start = date != null ? date.atStartOfDay() : null;
        LocalDateTime end = date != null ? date.plusDays(1).atStartOfDay() : null;
        return securityEventRepository.findWithAdvancedFilters(
                severity, search, agentName, source, riskOnly, start, end, pageable);
    }

    /** 지정한 날짜 구간의 severity별 건수 (date가 null이면 전체 통계) */
    @Transactional(readOnly = true)
    public Map<String, Long> getSeverityCountsForDate(LocalDate date) {
        LocalDateTime start = date != null ? date.atStartOfDay() : null;
        LocalDateTime end = date != null ? date.plusDays(1).atStartOfDay() : null;
        List<Object[]> results = securityEventRepository.countBySeverityInDateRange(start, end);
        Map<String, Long> counts = new java.util.HashMap<>();
        for (Object[] result : results) {
            counts.put((String) result[0], (Long) result[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public List<Integer> getHourlyData() {
        return getHourlyData(1);
    }

    @Transactional(readOnly = true)
    public List<Integer> getHourlyData(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
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
        return countUnresolvedEvents(1);
    }

    @Transactional(readOnly = true)
    public Long countUnresolvedEvents(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return securityEventRepository.countUnresolvedEvents(since);
    }

    /**
     * 보안 점수 (0~100).
     * 미해결 이벤트의 severity 가중치 합산으로 감점:
     *   critical -30 (최대 -90), high -15 (최대 -45),
     *   medium -3 (최대 -15), low -1 (최대 -5)
     * 단일 GROUP BY 쿼리로 4번의 개별 쿼리를 대체한다.
     */
    @Transactional(readOnly = true)
    public int calculateSecurityScore() {
        return calculateSecurityScore(1);
    }

    @Transactional(readOnly = true)
    public int calculateSecurityScore(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : securityEventRepository.countUnresolvedGroupedBySeverity(since)) {
            counts.put(((String) row[0]).toLowerCase(), ((Number) row[1]).longValue());
        }
        long critical = counts.getOrDefault("critical", 0L);
        long high     = counts.getOrDefault("high",     0L);
        long medium   = counts.getOrDefault("medium",   0L);
        long low      = counts.getOrDefault("low",      0L);

        double penalty = Math.min(critical * 30, 90)
                       + Math.min(high     * 15, 45)
                       + Math.min(medium   *  3, 15)
                       + Math.min(low      *  1,  5);

        return (int) Math.max(0, Math.min(100, 100 - penalty));
    }

    /** 전체 이벤트 대비 해결된 이벤트 비율 (문자열, ex. "96%") */
    @Transactional(readOnly = true)
    public String getResolveRate() {
        return getResolveRate(1);
    }

    @Transactional(readOnly = true)
    public String getResolveRate(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        long total = securityEventRepository.countEventsSince(since);
        if (total == 0) return "100%";
        long resolved = securityEventRepository.countResolvedEvents(since);
        int rate = (int) Math.round((double) resolved / total * 100.0);
        return rate + "%";
    }

    /** 해결된 이벤트의 평균 처리 시간 (문자열, ex. "12분" / "1시간 5분") */
    @Transactional(readOnly = true)
    public String getAverageResponseTime() {
        return getAverageResponseTime(1);
    }

    @Transactional(readOnly = true)
    public String getAverageResponseTime(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Double avgMinutes = securityEventRepository.averageResolutionMinutes(since);
        if (avgMinutes == null) return "N/A";
        long mins = Math.round(avgMinutes);
        if (mins < 60) return mins + "분";
        return (mins / 60) + "시간 " + (mins % 60) + "분";
    }

    /**
     * 최근 N일 이벤트 유형별 비율 목록.
     * 상위 3개 유형을 개별 항목으로, 나머지는 "기타"로 묶어 반환한다.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getThreatDistribution() {
        return getThreatDistribution(1);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getThreatDistribution(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
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
     * 단일 GROUP BY 쿼리로 N×3번의 개별 쿼리를 대체한다.
     * 해당 월의 severity별 이벤트 수로 감점:
     *   critical -5 (최대 -30), high -2 (최대 -20), medium -1 (최대 -10)
     */
    @Transactional(readOnly = true)
    public List<Integer> getMonthlyScores(int months) {
        LocalDateTime since = LocalDateTime.now()
                .minusMonths(months - 1L)
                .withDayOfMonth(1)
                .toLocalDate()
                .atStartOfDay();

        // [yearMonth(YYYYMM), severity, count] 형태로 한 번에 조회
        List<Object[]> rows = securityEventRepository.countBySeverityGroupedByMonth(since);

        // yearMonth → severity → count 으로 중첩 맵 구성
        Map<String, Map<String, Long>> byMonth = new HashMap<>();
        for (Object[] row : rows) {
            String ym       = (String) row[0];
            String severity = ((String) row[1]).toLowerCase();
            long   count    = ((Number) row[2]).longValue();
            byMonth.computeIfAbsent(ym, k -> new HashMap<>())
                   .put(severity, count);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMM");
        LocalDateTime now = LocalDateTime.now();
        List<Integer> scores = new ArrayList<>(months);

        for (int i = months - 1; i >= 0; i--) {
            String ym = now.minusMonths(i).format(fmt);
            Map<String, Long> counts = byMonth.getOrDefault(ym, Map.of());
            long critical = counts.getOrDefault("critical", 0L);
            long high     = counts.getOrDefault("high",     0L);
            long medium   = counts.getOrDefault("medium",   0L);

            double penalty = Math.min(critical * 5, 30)
                           + Math.min(high     * 2, 20)
                           + Math.min(medium   * 1, 10);

            scores.add((int) Math.max(0, Math.min(100, 100 - penalty)));
        }

        return scores;
    }
}
