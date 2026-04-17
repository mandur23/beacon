package com.example.beacon.service;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final FirewallService firewallService;
    private final FirewallHitsBatchService firewallHitsBatchService;
    private final EventBlockingPolicyService eventBlockingPolicyService;

    @Transactional
    public SecurityEvent createEvent(SecurityEvent event) {
        // blocked=true 로 결정된 이벤트는 상태값도 "차단됨"으로 정합성 유지
        if (Boolean.TRUE.equals(event.getBlocked())
                && (event.getStatus() == null
                || event.getStatus().isBlank()
                || "pending".equalsIgnoreCase(event.getStatus())
                || "탐지됨".equals(event.getStatus()))) {
            event.setStatus("차단됨");
        }

        SecurityEvent saved = securityEventRepository.save(event);

        if (event.getSourceIp() != null) {
            try {
                agentService.incrementEventCountByIp(event.getSourceIp());
            } catch (Exception e) {
                log.warn("Failed to increment agent event count: {}", e.getMessage());
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
        SecurityEvent event = request.toEntity();
        boolean blocked = eventBlockingPolicyService.resolveBlocked(event);
        event.setBlocked(blocked);

        if (blocked && event.getSourceIp() != null && !event.getSourceIp().isBlank()) {
            String reason = "정책 자동 차단: " + event.getEventType()
                    + " [severity=" + event.getSeverity() + "]";
            firewallService.createBlockRuleForIp(event.getSourceIp(), reason, "system-policy");
        }

        return createEvent(event);
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
     * 단일 GROUP BY 쿼리로 4번의 개별 쿼리를 대체한다.
     */
    @Transactional(readOnly = true)
    public int calculateSecurityScore() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : securityEventRepository.countUnresolvedGroupedBySeverity()) {
            counts.put(((String) row[0]).toLowerCase(), ((Number) row[1]).longValue());
        }
        long critical = counts.getOrDefault("critical", 0L);
        long high     = counts.getOrDefault("high",     0L);
        long medium   = counts.getOrDefault("medium",   0L);
        long low      = counts.getOrDefault("low",      0L);

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
