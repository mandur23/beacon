package com.example.beacon.controller;

import com.example.beacon.entity.SecurityEvent;
import com.example.beacon.repository.SecurityEventRepository;
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

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(ollamaService.getStatus());
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<Map<String, Object>> bootstrap(Authentication authentication) {
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
