package com.example.beacon.controller;

import com.example.beacon.entity.SecurityEvent;
import com.example.beacon.entity.TrafficLog;
import com.example.beacon.repository.SecurityEventRepository;
import com.example.beacon.repository.TrafficLogRepository;
import com.example.beacon.service.OllamaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Llama 3B 기반 AI 어시스턴트 API.
 *
 *  - GET  /api/ai/status      : 모델 준비 상태(설치/다운로드/READY) 조회
 *  - GET  /api/ai/catalog     : 무료 로컬 모델 추천 목록 조회
 *  - POST /api/ai/bootstrap   : 자동 설치/모델 다운로드 수동 트리거
 *  - POST /api/ai/summary     : 최근 N일 보안 이벤트 위험 요약 생성
 *  - POST /api/ai/chat        : 보안 이벤트 컨텍스트 포함 자유 질의
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    private static final int MAX_CHAT_MESSAGE_LENGTH = 2000;
    private static final int MAX_HISTORY_ITEMS = 20;
    private static final int MAX_HISTORY_CONTENT_LENGTH = 2000;
    private static final int MAX_HISTORY_CONTEXT_TURNS = 12;

    private final OllamaService ollamaService;
    private final SecurityEventRepository securityEventRepository;
    private final TrafficLogRepository trafficLogRepository;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(ollamaService.getStatus());
    }

    @GetMapping("/catalog")
    public ResponseEntity<Map<String, Object>> catalog() {
        return ResponseEntity.ok(Map.of(
                "models", ollamaService.getFreeModelCatalog(),
                "currentModel", ollamaService.getStatus().get("model")
        ));
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<Map<String, Object>> bootstrap(
            @RequestBody(required = false) Map<String, Object> req,
            Authentication authentication
    ) {
        if (req != null && req.get("model") != null) {
            String requestedModel = String.valueOf(req.get("model"));
            if (!ollamaService.selectModel(requestedModel)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "지원되지 않거나 현재 적용할 수 없는 모델입니다.",
                        "hint", "GET /api/ai/catalog 에서 제공된 tag 값만 사용하세요."
                ));
            }
        }
        ollamaService.triggerBootstrap(isAdmin(authentication));
        return ResponseEntity.accepted().body(ollamaService.getStatus());
    }

    /**
     * 최근 N일(기본 7일) 동안의 보안 이벤트를 종합해 LLM이 위험 요약 보고서를 작성한다.
     */
    @PostMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(@RequestBody(required = false) Map<String, Object> req) {
        int days = parseDays(req, 7);

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);
        List<SecurityEvent> events = securityEventRepository.findByDateRange(start, end);

        String context = buildSecurityContext(events, days);

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content",
                        "당신은 한국어로 답변하는 보안 관제(SOC) 분석 어시스턴트입니다. " +
                        "주어진 통계 데이터만으로 위험을 객관적으로 평가하고, 추측·가공된 사실은 제시하지 않습니다. " +
                        "출력은 반드시 한국어 마크다운 형식으로 다음 구조를 따르세요:\n\n" +
                        "## 핵심 요약\n- (한 줄)\n\n" +
                        "## 주요 위험 Top 5\n1. **(위험 명)** — 영향/근거 — 권장 조치\n\n" +
                        "## 즉시 권장 조치\n- 항목 (담당)\n\n" +
                        "## 추가 모니터링 항목\n- 항목"),
                Map.of("role", "user", "content",
                        "다음은 최근 " + days + "일간 Beacon SOC가 수집한 보안 이벤트 통계 데이터입니다.\n" +
                        "이 데이터만 근거로 위험 분석 보고서를 작성해 주세요.\n\n" + context)
        );

        String response = ollamaService.chat(messages);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", response);
        body.put("eventCount", events.size());
        body.put("days", days);
        body.put("generatedAt", LocalDateTime.now().toString());
        body.put("model", ollamaService.getStatus().get("model"));
        return ResponseEntity.ok(body);
    }

    /**
     * 채팅 형식 자유 질의. includeContext=true 면 최근 7일 이벤트 컨텍스트가 시스템 프롬프트에 함께 주입된다.
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> req) {
        String userMessage = String.valueOf(Optional.ofNullable(req.get("message")).orElse("")).trim();
        if (userMessage.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message가 비어 있습니다"));
        }
        if (userMessage.length() > MAX_CHAT_MESSAGE_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of("error", "message 길이는 2000자를 초과할 수 없습니다"));
        }

        boolean includeContext = Boolean.TRUE.equals(req.get("includeContext"));
        List<Map<String, String>> history = parseHistory(req.get("history"));

        StringBuilder system = new StringBuilder()
                .append("당신은 한국어로 답하는 'Beacon' 통합 보안 관제 플랫폼의 AI 어시스턴트입니다.\n")
                .append("- 답변은 간결하고 분석가가 바로 행동 가능한 형태로 제시합니다.\n")
                .append("- 데이터에 없는 사실은 단정하지 않습니다 (모르면 '데이터 부족'이라고 명시).\n")
                .append("- 보안/네트워크/방화벽/위협 인텔리전스 도메인에 특화되어 있습니다.");

        if (includeContext) {
            LocalDateTime end = LocalDateTime.now();
            List<SecurityEvent> events = securityEventRepository.findByDateRange(end.minusDays(7), end);
            system.append("\n\n[참고: 최근 7일 보안 이벤트 통계]\n").append(buildSecurityContext(events, 7));
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system.toString()));

        // 최근 12턴만 컨텍스트로 사용 (토큰 절약)
        int historyLimit = Math.min(history.size(), MAX_HISTORY_CONTEXT_TURNS);
        for (int i = history.size() - historyLimit; i < history.size(); i++) {
            Map<String, String> h = history.get(i);
            String role = h.get("role");
            String content = h.get("content");
            if (role != null && content != null && ("user".equals(role) || "assistant".equals(role))) {
                messages.add(Map.of("role", role, "content", content));
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        String reply = ollamaService.chat(messages);
        return ResponseEntity.ok(Map.of(
                "reply", reply,
                "model", ollamaService.getStatus().get("model")
        ));
    }

    /**
     * 트래픽 이상 패턴 AI 분석.
     *
     * 요청 파라미터 (모두 선택):
     *   hours      - 분석 기간(시간 단위, 기본 24, 최대 168)
     *   minScore   - 이상 점수 최솟값(기본 0.5)
     *   agentName  - 특정 에이전트 필터(없으면 전체)
     *
     * 반환:
     *   analysis   - LLM 분석 텍스트 (한국어 마크다운)
     *   stats       - 컨텍스트에 사용된 집계 통계 요약
     *   trafficCount / anomalyCount / hours / model / generatedAt
     */
    @PostMapping("/traffic-analysis")
    public ResponseEntity<Map<String, Object>> trafficAnalysis(
            @RequestBody(required = false) Map<String, Object> req
    ) {
        int hours = parseHours(req, 24);
        double minScore = parseMinScore(req, 0.5);
        String agentFilter = req != null && req.get("agentName") instanceof String s && !s.isBlank() ? s.trim() : null;

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        LocalDateTime now = LocalDateTime.now();
        List<TrafficLog> recentLogs = trafficLogRepository.findByDateRangeAndOptionalAgent(since, now, agentFilter);
        List<TrafficLog> anomalies = trafficLogRepository.findAnomalousTrafficSince(minScore, since, agentFilter);

        String context = buildTrafficContext(recentLogs, anomalies, hours, agentFilter);

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content",
                        "당신은 한국어로 답변하는 네트워크 트래픽 이상 탐지 전문 분석가입니다.\n" +
                        "제공된 트래픽 통계와 이상 로그만을 근거로 분석하며, 데이터에 없는 내용은 추측하지 않습니다.\n" +
                        "출력은 반드시 한국어 마크다운 형식으로 아래 구조를 따르세요:\n\n" +
                        "## 트래픽 이상 요약\n" +
                        "- (한 줄 핵심 결론)\n\n" +
                        "## 주요 이상 패턴 (Top 5)\n" +
                        "1. **패턴명** — 근거 지표 — 위험도(높음/중간/낮음) — 권장 조치\n\n" +
                        "## 프로토콜/포트 이상 징후\n" +
                        "- 항목\n\n" +
                        "## 의심 출발지 IP\n" +
                        "| IP | 횟수 | 총 바이트 | 평균 이상점수 | 비고 |\n" +
                        "|---|---|---|---|---|\n\n" +
                        "## 즉시 권장 조치\n" +
                        "- 항목 (담당/우선순위)\n\n" +
                        "## 추가 모니터링 권고\n" +
                        "- 항목"),
                Map.of("role", "user", "content",
                        "다음은 Beacon SOC가 수집한 최근 " + hours + "시간 트래픽 데이터입니다. " +
                        (agentFilter != null ? "에이전트 필터: " + agentFilter + ". " : "") +
                        "이상 패턴과 위협을 분석해 주세요.\n\n" + context)
        );

        String analysis = ollamaService.chat(messages);

        Map<String, Object> statsSnapshot = buildTrafficStatsSummary(recentLogs, anomalies);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("analysis", analysis);
        body.put("stats", statsSnapshot);
        body.put("trafficCount", recentLogs.size());
        body.put("anomalyCount", anomalies.size());
        body.put("hours", hours);
        body.put("agentFilter", agentFilter);
        body.put("model", ollamaService.getStatus().get("model"));
        body.put("generatedAt", LocalDateTime.now().toString());
        return ResponseEntity.ok(body);
    }

    private String buildTrafficContext(List<TrafficLog> logs, List<TrafficLog> anomalies, int hours, String agentFilter) {
        if (logs.isEmpty() && anomalies.isEmpty()) {
            return "(최근 " + hours + "시간 내 수집된 트래픽 데이터 없음)";
        }

        // 프로토콜 분포
        Map<String, Long> byProtocol = logs.stream()
                .collect(Collectors.groupingBy(t -> Optional.ofNullable(t.getProtocol()).orElse("UNKNOWN"), Collectors.counting()));

        // 출발지 IP별 총 바이트
        Map<String, Long> bytesBySrcIp = logs.stream()
                .collect(Collectors.groupingBy(t -> Optional.ofNullable(t.getSourceIp()).orElse("-"),
                        Collectors.summingLong(t -> Optional.ofNullable(t.getBytesTransferred()).orElse(0L))));

        // 목적지 포트 빈도
        Map<String, Long> byDestPort = logs.stream()
                .collect(Collectors.groupingBy(t -> String.valueOf(t.getDestinationPort()), Collectors.counting()));

        // 에이전트별 이상 건수
        Map<String, Long> anomalyByAgent = anomalies.stream()
                .collect(Collectors.groupingBy(t -> Optional.ofNullable(t.getAgentName()).orElse("unknown"), Collectors.counting()));

        // 전체 트래픽 크기
        long totalBytes = logs.stream().mapToLong(t -> Optional.ofNullable(t.getBytesTransferred()).orElse(0L)).sum();
        long totalPackets = logs.stream().mapToLong(t -> Optional.ofNullable(t.getPacketsTransferred()).orElse(0L)).sum();
        double avgDuration = logs.stream().mapToInt(t -> Optional.ofNullable(t.getDuration()).orElse(0)).average().orElse(0);
        double avgAnomalyScore = anomalies.stream().mapToDouble(t -> Optional.ofNullable(t.getAnomalyScore()).orElse(0.0)).average().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append("=== 기간 집계 (최근 ").append(hours).append("시간")
                .append(agentFilter != null ? ", 에이전트=" + agentFilter : "").append(") ===\n");
        sb.append("- 총 트래픽 로그: ").append(logs.size()).append("건\n");
        sb.append("- 이상 탐지 로그: ").append(anomalies.size()).append("건");
        if (!logs.isEmpty()) {
            sb.append(" (").append(String.format("%.1f%%", anomalies.size() * 100.0 / logs.size())).append(")");
        }
        sb.append("\n");
        sb.append("- 총 전송량: ").append(humanBytes(totalBytes))
                .append(" / 패킷: ").append(totalPackets).append("개\n");
        sb.append("- 평균 연결 지속시간: ").append(String.format("%.1f", avgDuration)).append("ms\n");
        sb.append("- 이상 평균 점수: ").append(String.format("%.3f", avgAnomalyScore)).append("\n");
        sb.append("- 프로토콜 분포: ").append(topNFormatted(byProtocol, 6)).append("\n");
        sb.append("- 목적지 포트 Top5: ").append(topNFormatted(byDestPort, 5)).append("\n");
        sb.append("- 출발지 IP별 전송량 Top5: ").append(topNFormattedBytes(bytesBySrcIp, 5)).append("\n");
        sb.append("- 에이전트별 이상 건수: ").append(topNFormatted(anomalyByAgent, 5)).append("\n");

        if (!anomalies.isEmpty()) {
            sb.append("\n=== 고이상점수 트래픽 샘플 (최대 10건) ===\n");
            anomalies.stream()
                    .sorted(Comparator.comparingDouble((TrafficLog t) -> Optional.ofNullable(t.getAnomalyScore()).orElse(0.0)).reversed())
                    .limit(10)
                    .forEach(t -> {
                        sb.append("- score=").append(String.format("%.3f", Optional.ofNullable(t.getAnomalyScore()).orElse(0.0)))
                                .append(" | ").append(safe(t.getProtocol()))
                                .append(" | src=").append(safe(t.getSourceIp())).append(":").append(t.getSourcePort())
                                .append(" -> dst=").append(safe(t.getDestinationIp())).append(":").append(t.getDestinationPort())
                                .append(" | ").append(humanBytes(Optional.ofNullable(t.getBytesTransferred()).orElse(0L)))
                                .append(" | dur=").append(Optional.ofNullable(t.getDuration()).orElse(0)).append("ms")
                                .append(" | agent=").append(safe(t.getAgentName()))
                                .append(" | reason=").append(safe(t.getAnomalyReason()))
                                .append("\n");
                    });
        }

        return sb.toString();
    }

    private Map<String, Object> buildTrafficStatsSummary(List<TrafficLog> logs, List<TrafficLog> anomalies) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalLogs", logs.size());
        stats.put("anomalyCount", anomalies.size());
        stats.put("anomalyRate", logs.isEmpty() ? 0 : String.format("%.1f%%", anomalies.size() * 100.0 / logs.size()));
        stats.put("totalBytes", logs.stream().mapToLong(t -> Optional.ofNullable(t.getBytesTransferred()).orElse(0L)).sum());
        stats.put("avgAnomalyScore", anomalies.stream().mapToDouble(t -> Optional.ofNullable(t.getAnomalyScore()).orElse(0.0)).average().orElse(0));
        Map<String, Long> byProtocol = logs.stream()
                .collect(Collectors.groupingBy(t -> Optional.ofNullable(t.getProtocol()).orElse("UNKNOWN"), Collectors.counting()));
        stats.put("protocolDistribution", byProtocol);
        return stats;
    }

    private int parseHours(Map<String, Object> req, int defaultHours) {
        if (req == null) return defaultHours;
        Object v = req.get("hours");
        if (v instanceof Number n) return Math.max(1, Math.min(168, n.intValue()));
        if (v instanceof String s) {
            try { return Math.max(1, Math.min(168, Integer.parseInt(s))); }
            catch (NumberFormatException ignored) { }
        }
        return defaultHours;
    }

    private double parseMinScore(Map<String, Object> req, double defaultScore) {
        if (req == null) return defaultScore;
        Object v = req.get("minScore");
        if (v instanceof Number n) return Math.max(0.0, Math.min(1.0, n.doubleValue()));
        if (v instanceof String s) {
            try { return Math.max(0.0, Math.min(1.0, Double.parseDouble(s))); }
            catch (NumberFormatException ignored) { }
        }
        return defaultScore;
    }

    private String topNFormattedBytes(Map<String, Long> map, int n) {
        if (map == null || map.isEmpty()) return "(없음)";
        return map.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(n)
                .map(e -> e.getKey() + "(" + humanBytes(e.getValue()) + ")")
                .collect(Collectors.joining(", "));
    }

    private String humanBytes(long bytes) {
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int idx = 0;
        while (value >= 1024 && idx < units.length - 1) {
            value /= 1024.0;
            idx++;
        }
        return String.format("%.1f%s", value, units[idx]);
    }

    private List<Map<String, String>> parseHistory(Object rawHistory) {
        if (!(rawHistory instanceof List<?> historyList)) {
            return List.of();
        }
        List<Map<String, String>> sanitized = new ArrayList<>();
        for (Object item : historyList) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object rawRole = map.get("role");
            Object rawContent = map.get("content");
            if (!(rawRole instanceof String role) || !(rawContent instanceof String content)) {
                continue;
            }
            String normalizedRole = role.trim();
            if (!"user".equals(normalizedRole) && !"assistant".equals(normalizedRole)) {
                continue;
            }
            String normalizedContent = content.trim();
            if (normalizedContent.isEmpty()) {
                continue;
            }
            if (normalizedContent.length() > MAX_HISTORY_CONTENT_LENGTH) {
                normalizedContent = normalizedContent.substring(0, MAX_HISTORY_CONTENT_LENGTH);
            }
            sanitized.add(Map.of("role", normalizedRole, "content", normalizedContent));
            if (sanitized.size() >= MAX_HISTORY_ITEMS) {
                break;
            }
        }
        return sanitized;
    }

    // ─────────────────────────────────────────────────────────────
    //  내부 유틸
    // ─────────────────────────────────────────────────────────────

    private int parseDays(Map<String, Object> req, int defaultDays) {
        if (req == null) return defaultDays;
        Object v = req.get("days");
        if (v instanceof Number n) return Math.max(1, Math.min(30, n.intValue()));
        if (v instanceof String s) {
            try { return Math.max(1, Math.min(30, Integer.parseInt(s))); }
            catch (NumberFormatException ignored) { }
        }
        return defaultDays;
    }

    /**
     * 컨텍스트 윈도우를 아끼기 위해 원시 이벤트 리스트가 아니라
     * '집계 통계 + 고위험 샘플'만 LLM에 넘긴다.
     */
    private String buildSecurityContext(List<SecurityEvent> events, int days) {
        if (events.isEmpty()) {
            return "(최근 " + days + "일간 수집된 이벤트 없음)";
        }

        Map<String, Long> bySeverity = events.stream().collect(Collectors.groupingBy(
                e -> Optional.ofNullable(e.getSeverity()).orElse("info").toLowerCase(),
                Collectors.counting()
        ));
        Map<String, Long> byType = events.stream().collect(Collectors.groupingBy(
                e -> Optional.ofNullable(e.getEventType()).orElse("Unknown"),
                Collectors.counting()
        ));
        Map<String, Long> bySource = events.stream()
                .filter(e -> e.getSourceIp() != null && !e.getSourceIp().isEmpty())
                .collect(Collectors.groupingBy(SecurityEvent::getSourceIp, Collectors.counting()));
        Map<String, Long> byLocation = events.stream()
                .filter(e -> e.getLocation() != null && !e.getLocation().isEmpty())
                .collect(Collectors.groupingBy(SecurityEvent::getLocation, Collectors.counting()));

        long blocked = events.stream().filter(e -> Boolean.TRUE.equals(e.getBlocked())).count();
        long unresolved = events.stream()
                .filter(e -> e.getStatus() != null && (e.getStatus().equals("pending") || e.getStatus().equals("조사중")))
                .count();

        StringBuilder sb = new StringBuilder();
        sb.append("- 분석 기간: 최근 ").append(days).append("일\n");
        sb.append("- 총 이벤트: ").append(events.size()).append("건\n");
        sb.append("- 차단됨: ").append(blocked).append("건 / 미해결: ").append(unresolved).append("건\n");
        sb.append("- 심각도 분포: ").append(formatMap(bySeverity)).append("\n");
        sb.append("- 이벤트 유형 Top5: ").append(topNFormatted(byType, 5)).append("\n");
        sb.append("- 출발지 IP Top5: ").append(topNFormatted(bySource, 5)).append("\n");
        sb.append("- 출발지 국가/지역 Top5: ").append(topNFormatted(byLocation, 5)).append("\n");

        sb.append("\n[고위험 이벤트 샘플 (최대 10건)]\n");
        events.stream()
                .filter(e -> "critical".equalsIgnoreCase(e.getSeverity()) || "high".equalsIgnoreCase(e.getSeverity()))
                .sorted(Comparator.comparing(SecurityEvent::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .forEach(e -> sb.append("- [").append(e.getSeverity()).append("] ")
                        .append(safe(e.getEventType())).append(" / src=").append(safe(e.getSourceIp()))
                        .append(" / port=").append(e.getPort() != null ? e.getPort() : "-")
                        .append(" / risk=").append(e.getRiskScore() != null ? e.getRiskScore() : 0)
                        .append(" / ").append(e.getCreatedAt())
                        .append("\n"));

        return sb.toString();
    }

    private String topNFormatted(Map<String, Long> map, int n) {
        if (map == null || map.isEmpty()) return "(없음)";
        return map.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(n)
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
    }

    private String formatMap(Map<String, Long> map) {
        if (map == null || map.isEmpty()) return "(없음)";
        return map.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private String safe(String s) {
        return s == null ? "-" : s;
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
