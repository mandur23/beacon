package com.example.beacon.controller;

import com.example.beacon.entity.Agent;
import com.example.beacon.entity.FirewallRule;
import com.example.beacon.entity.SecurityEvent;
import com.example.beacon.entity.User;
import com.example.beacon.repository.UserRepository;
import com.example.beacon.service.AgentService;
import com.example.beacon.service.EventBlockingPolicyService;
import com.example.beacon.service.FirewallService;
import com.example.beacon.service.NetworkStatsService;
import com.example.beacon.service.SecurityEventService;
import com.example.beacon.service.TrafficAnalysisService;
import com.example.beacon.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final SecurityEventService  securityEventService;
    private final TrafficAnalysisService trafficAnalysisService;
    private final UserSessionService    userSessionService;
    private final FirewallService             firewallService;
    private final EventBlockingPolicyService  eventBlockingPolicyService;
    private final UserRepository        userRepository;
    private final AgentService          agentService;
    private final NetworkStatsService   networkStatsService;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("page", "dashboard");
        model.addAttribute("pageTitle", "대시보드");

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        long blockedToday = securityEventService.getBlockedEventsCount(todayStart);
        long activeSessions = userSessionService.getActiveSessionCount();
        long unresolvedCount = securityEventService.countUnresolvedEvents();

        List<Map<String, Object>> stats = new ArrayList<>();
        int securityScore = securityEventService.calculateSecurityScore();
        String scoreLabel = securityScore >= 80 ? "양호 수준"
                          : securityScore >= 60 ? "주의 필요"
                          : "위험 수준";
        String scoreColor = securityScore >= 80 ? "#06d6a0"
                          : securityScore >= 60 ? "#ffd166"
                          : "#ff3d5a";

        stats.add(kpi("오늘 차단",    "🛡", blockedToday,    "차단된 이벤트",   "#ff3d5a", false));
        stats.add(kpi("활성 세션",    "◎",  activeSessions,  "현재 접속 중",    "#00e5ff", false));
        stats.add(kpi("미해결 이벤트","⚠",  unresolvedCount, "즉시 검토 필요",  "#ffd166", unresolvedCount > 0));
        stats.add(kpi("보안 점수",    "✓",  securityScore,   scoreLabel,        scoreColor, false));
        model.addAttribute("stats", stats);

        // 위협 분포 및 처리 분석 데이터 추가
        model.addAttribute("hourlyData", securityEventService.getHourlyData());
        model.addAttribute("threatDistribution", securityEventService.getThreatDistribution());
        model.addAttribute("resolveRate", securityEventService.getResolveRate());
        model.addAttribute("avgResponseTime", securityEventService.getAverageResponseTime());

        List<Map<String, Object>> recentEvents = securityEventService.getHighRiskEvents(5.0)
                .stream().limit(10).map(this::toEventMap).collect(Collectors.toList());
        model.addAttribute("recentEvents", recentEvents);

        return "pages/dashboard";
    }

    @GetMapping("/threats")
    public String threats(Model model,
                          @RequestParam(defaultValue = "all") String severity,
                          @RequestParam(defaultValue = "") String search,
                          @RequestParam(defaultValue = "all") String agentName,
                          @RequestParam(defaultValue = "all") String source,
                          @RequestParam(defaultValue = "false") boolean riskOnly,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate selectedDate = date; // null 허용 (전체 조회를 위함)
        int pageSize = 50; // 사용자 요청에 따라 50개씩

        model.addAttribute("page", "threats");
        model.addAttribute("pageTitle", "위협 / 이벤트 로그");
        model.addAttribute("severityCounts", securityEventService.getSeverityCountsForDate(selectedDate));
        model.addAttribute("currentSeverity", severity);
        model.addAttribute("search", search);
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("currentAgent", agentName);
        model.addAttribute("currentSource", source);
        model.addAttribute("riskOnly", riskOnly);

        // 에이전트 목록 (드롭다운 필터용)
        List<String> agentList = agentService.getAllAgents().stream()
                .map(Agent::getAgentName)
                .collect(Collectors.toList());
        model.addAttribute("agentList", agentList);

        org.springframework.data.domain.Page<SecurityEvent> eventPage = securityEventService.getEventsWithFiltersPaged(
                severity, search, agentName, source, riskOnly, selectedDate, 
                org.springframework.data.domain.PageRequest.of(page, pageSize, org.springframework.data.domain.Sort.by("createdAt").descending())
        );

        model.addAttribute("events", eventPage.getContent().stream().map(this::toEventMap).collect(Collectors.toList()));
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", eventPage.getTotalPages());
        model.addAttribute("totalItems", eventPage.getTotalElements());

        return "pages/threats";
    }

    @GetMapping("/network")
    public String network(Model model,
                          @RequestParam(defaultValue = "topology") String tab) {
        model.addAttribute("page", "network");
        model.addAttribute("pageTitle", "네트워크 모니터링");
        model.addAttribute("tab", tab);

        LocalDateTime lastHour = LocalDateTime.now().minusHours(1);
        long bandwidth       = trafficAnalysisService.getTotalBytesTransferred(lastHour);
        long activeSessions  = userSessionService.getActiveSessionCount();
        long blockedLastHour = securityEventService.getBlockedEventsCount(lastHour);
        String avgLatency    = trafficAnalysisService.getAverageLatency(lastHour);
        int    latencyPct    = trafficAnalysisService.getAverageLatencyPct(lastHour);

        // 대역폭 게이지: 1시간 기준 최대 1 GB 대비 백분율
        int bwPct      = (int) Math.min(100, bandwidth * 100L / 1_073_741_824L);
        // 세션 게이지: 최대 100 세션 기준
        int sessionPct = (int) Math.min(100, activeSessions * 100L / 100L);
        // 차단 게이지: 최대 200건 기준
        int blockPct   = (int) Math.min(100, blockedLastHour * 100L / 200L);

        // 1. 프로토콜 분석 데이터 가공 (방어 코드 추가)
        Map<String, Object> rawProtocolStats = trafficAnalysisService.getProtocolStatistics();
        long totalBytesValue = 0;
        if (rawProtocolStats != null) {
            totalBytesValue = rawProtocolStats.values().stream()
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(v -> {
                        Object b = ((Map<String, Object>) v).get("bytes");
                        return b != null ? ((Number) b).longValue() : 0L;
                    }).sum();
        }
        
        final long totalBytes = totalBytesValue;
        List<Map<String, Object>> protocolStatsList = new java.util.ArrayList<>();
        if (rawProtocolStats != null) {
            protocolStatsList = rawProtocolStats.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> val = (Map<String, Object>) entry.getValue();
                    Object bObj = val.get("bytes");
                    long b = bObj != null ? ((Number) bObj).longValue() : 0L;
                    int pct = totalBytes > 0 ? (int) (b * 100 / totalBytes) : 0;
                    
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("name", entry.getKey() != null ? entry.getKey() : "UNKNOWN");
                    map.put("bytes", b);
                    map.put("pct", pct);
                    return map;
                }).sorted((a, b) -> Long.compare((long) b.get("bytes"), (long) a.get("bytes")))
                .limit(5).collect(Collectors.toList());
        }
        
        model.addAttribute("protocolStats", protocolStatsList);
        model.addAttribute("bandwidthLastHour", bandwidth);
        
        List<Map<String, Object>> displayMetrics = new java.util.ArrayList<>();
        displayMetrics.add(Map.of("label", "대역폭(1시간)", "value", bandwidth, "color", "#00e5ff", "pct", bwPct));
        displayMetrics.add(Map.of("label", "활성 세션", "value", activeSessions, "color", "#06d6a0", "pct", sessionPct));
        displayMetrics.add(Map.of("label", "차단 이벤트", "value", blockedLastHour, "color", "#ff3d5a", "pct", blockPct));
        displayMetrics.add(Map.of("label", "평균 지연", "value", avgLatency, "color", "#ffd166", "pct", latencyPct));
        model.addAttribute("metrics", displayMetrics);

        // 2. 토폴로지 노드: 등록된 에이전트 기반
        List<Agent> agents = agentService.getAllAgents();
        List<Map<String, Object>> nodes = agents.stream()
                .map(a -> {
                    String nodeStatus = switch (a.getStatus() != null ? a.getStatus() : "offline") {
                        case "online"  -> "ok";
                        case "error"   -> "error";
                        default        -> "warn";
                    };
                    return Map.<String, Object>of(
                            "id",     "AG-" + a.getId(),
                            "label",  a.getAgentName() != null ? a.getAgentName() : "AG-" + a.getId(),
                            "type",   "server",
                            "status", nodeStatus,
                            "ip",     a.getIpAddress()
                    );
                }).collect(Collectors.toList());
        model.addAttribute("nodes", nodes);

        // 3. 인터페이스: 에이전트의 실제 하드웨어 네트워크 정보 (파이썬 에이전트 수집본 JSON 파싱)
        List<Map<String, Object>> agentInterfaces = agents.stream()
                .map(a -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("name", a.getAgentName() != null ? a.getAgentName() : "Unknown");
                    map.put("ip", a.getIpAddress() != null ? a.getIpAddress() : "-");
                    map.put("status", a.getStatus() != null ? a.getStatus() : "offline");
                    map.put("trafficCount", a.getTotalTrafficLogs() != null ? a.getTotalTrafficLogs() : 0L);
                    
                    // JSON 변환기로 metadata에 들어있는 interfaces 획득
                    List<Map<String, Object>> hwNics = new java.util.ArrayList<>();
                    if (a.getMetadata() != null) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            Map<String, Object> metaMap = mapper.readValue(a.getMetadata(), Map.class);
                            if (metaMap.containsKey("interfaces")) {
                                hwNics = (List<Map<String, Object>>) metaMap.get("interfaces");
                            }
                        } catch (Exception ignored) {}
                    }
                    map.put("hwInterfaces", hwNics);
                    return map;
                }).collect(Collectors.toList());
        model.addAttribute("agentInterfaces", agentInterfaces);

        return "pages/network";
    }

    @GetMapping("/access")
    public String access(Model model) {
        model.addAttribute("page", "access");
        model.addAttribute("pageTitle", "접근 제어");
        model.addAttribute("activeSessions", userSessionService.getActiveSessions());
        model.addAttribute("highRiskSessions", userSessionService.getHighRiskSessions(7.0));

        long total = userRepository.count();
        long active = userRepository.countActiveUsers();
        long noMfa = userRepository.countByMfaEnabledFalseAndEnabledTrue();
        Map<String, Object> userStats = new LinkedHashMap<>();
        userStats.put("total", total);
        userStats.put("active", active);
        userStats.put("noMfa", noMfa);
        userStats.put("offline", total - active);
        model.addAttribute("userStats", userStats);

        List<Map<String, Object>> users = userRepository.findAll()
                .stream().map(this::toUserMap).collect(Collectors.toList());
        model.addAttribute("users", users);

        return "pages/access";
    }

    @GetMapping("/firewall")
    public String firewall(Model model) {
        model.addAttribute("page", "firewall");
        model.addAttribute("pageTitle", "방화벽 / 정책");
        model.addAttribute("ruleStats", firewallService.getRuleStats());

        List<Map<String, Object>> rules = firewallService.getAllRulesOrdered()
                .stream().map(this::toRuleMap).collect(Collectors.toList());
        model.addAttribute("rules", rules);

        // 실시간 차단 이력 데이터 추가
        List<Map<String, Object>> recentBlocked = securityEventService.getRecentResolved()
                .stream().limit(10).map(this::toEventMap).collect(Collectors.toList());
        model.addAttribute("recentBlocked", recentBlocked);

        return "pages/firewall";
    }

    @GetMapping("/policies/event-blocking")
    public String eventBlockingPolicies(Model model) {
        model.addAttribute("page", "event-blocking");
        model.addAttribute("pageTitle", "이벤트 차단 정책");
        model.addAttribute("policies", eventBlockingPolicyService.findAllOrdered());
        return "pages/event-blocking-policies";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("page", "reports");
        model.addAttribute("pageTitle", "보고서 / 분석");

        LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);
        long blockedLastMonth = securityEventService.getBlockedEventsCount(lastMonth);

        Map<String, Object> reportStats = new LinkedHashMap<>();
        reportStats.put("blocked",      blockedLastMonth);
        reportStats.put("score",        securityEventService.calculateSecurityScore());
        reportStats.put("resolveRate",  securityEventService.getResolveRate());
        reportStats.put("responseTime", securityEventService.getAverageResponseTime());
        model.addAttribute("reportStats", reportStats);

        model.addAttribute("severityDistribution", securityEventService.getSeverityCounts());
        model.addAttribute("threatDistribution",   securityEventService.getThreatDistribution());

        List<Map<String, Object>> incidents = securityEventService.getRecentResolved()
                .stream().map(this::toIncidentMap).collect(Collectors.toList());
        model.addAttribute("incidents",    incidents);
        model.addAttribute("monthlyScores", securityEventService.getMonthlyScores(12));

        return "pages/reports";
    }

    @GetMapping("/location")
    public String location(Model model) {
        model.addAttribute("page", "location");
        model.addAttribute("pageTitle", "위치 추적");
        return "pages/location";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
    
    @GetMapping("/agents")
    public String agents(Model model) {
        model.addAttribute("page", "agents");
        model.addAttribute("pageTitle", "에이전트 관리");

        List<Agent> allAgents = agentService.getAllAgents();
        Long onlineCount = agentService.getOnlineAgentCount();
        
        Map<String, Object> agentStats = new LinkedHashMap<>();
        agentStats.put("total", allAgents.size());
        agentStats.put("online", onlineCount);
        agentStats.put("offline", allAgents.size() - onlineCount);
        model.addAttribute("agentStats", agentStats);
        
        List<Map<String, Object>> agents = allAgents.stream()
                .map(this::toAgentMap)
                .collect(Collectors.toList());
        model.addAttribute("agents", agents);
        
        return "pages/agents";
    }

    // ── 변환 헬퍼 ──────────────────────────────────────────────────

    private Map<String, Object> kpi(String label, String icon, Object value, String sub, String color, boolean blink) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("icon", icon);
        m.put("value", value);
        m.put("sub", sub);
        m.put("color", color);
        m.put("blink", blink);
        return m;
    }

    private Map<String, Object> toEventMap(SecurityEvent e) {
        String sev = e.getSeverity() != null ? e.getSeverity() : "info";
        String sevLabel = switch (sev) {
            case "critical" -> "위급";
            case "high"     -> "높음";
            case "medium"   -> "중간";
            case "low"      -> "낮음";
            default         -> "정보";
        };
        String sevColor = switch (sev) {
            case "critical" -> "#ff3d5a";
            case "high"     -> "#ff8c42";
            case "medium"   -> "#ffd166";
            case "low"      -> "#06d6a0";
            default         -> "#00e5ff";
        };
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dbId", e.getId());
        m.put("id", "EVT-" + e.getId());
        m.put("type", e.getEventType() != null ? e.getEventType() : "Unknown");
        m.put("sourceIp", e.getSourceIp() != null ? e.getSourceIp() : "N/A");
        m.put("destinationIp", e.getDestinationIp() != null ? e.getDestinationIp() : "Internal Node");
        m.put("ip", e.getSourceIp() != null ? e.getSourceIp() : "N/A"); // backward compatibility
        m.put("protocol", e.getProtocol() != null ? e.getProtocol() : "N/A");
        m.put("port", e.getPort() != null ? String.valueOf(e.getPort()) : "N/A");
        m.put("location", e.getLocation() != null ? e.getLocation() : "Unknown");
        m.put("severity", sev);
        m.put("severityLabel", sevLabel);
        m.put("severityColor", sevColor);
        m.put("status", e.getStatus() != null ? e.getStatus() : "pending");
        String time = e.getCreatedAt() != null
                ? e.getCreatedAt().toString().replace("T", " ").substring(0, Math.min(16, e.getCreatedAt().toString().length()))
                : "";
        m.put("time", time);
        m.put("source", e.getSource() != null ? e.getSource().name() : "AGENT");
        m.put("agentName", e.getAgentName() != null ? e.getAgentName() : "N/A");
        
        // Ensure metadata is a valid JSON string for frontend rendering. If it's pure string but not json, wrap it.
        String meta = e.getMetadata() != null ? e.getMetadata() : "{}";
        if(!meta.trim().startsWith("{") && !meta.trim().startsWith("[")) {
            meta = "{\"raw_data\":\"" + meta.replace("\"", "\\\"").replace("\n", " ") + "\"}";
        }
        m.put("metadata", meta);
        
        m.put("description", e.getDescription() != null ? e.getDescription() : "");
        m.put("riskScore", e.getRiskScore() != null ? e.getRiskScore() : 0.0);
        return m;
    }

    private Map<String, Object> toRuleMap(FirewallRule r) {
        String action = r.getAction() != null ? r.getAction() : "block";
        String actionLabel = switch (action.toLowerCase()) {
            case "allow"        -> "허용";
            case "block", "deny"-> "차단";
            case "log"          -> "로그";
            default             -> action;
        };
        String actionColor = switch (action.toLowerCase()) {
            case "allow"        -> "#06d6a0";
            case "block", "deny"-> "#ff3d5a";
            case "log"          -> "#ffd166";
            default             -> "#00e5ff";
        };
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("priority", r.getPriority());
        m.put("dbId", r.getId());
        m.put("id", "FW-" + r.getId());
        m.put("name", r.getName());
        m.put("action", action);
        m.put("actionLabel", actionLabel);
        m.put("actionColor", actionColor);
        m.put("src", r.getSourceAddress());
        m.put("dst", r.getDestinationAddress());
        m.put("port", r.getPort());
        m.put("hits", r.getHits());
        m.put("enabled", r.getEnabled());
        m.put("description", r.getDescription() != null ? r.getDescription() : "");
        return m;
    }

    private Map<String, Object> toUserMap(User u) {
        String statusLabel = u.getEnabled() ? "활성" : "비활성";
        String statusColor = u.getEnabled() ? "#06d6a0" : "#ff3d5a";
        String lastLogin = u.getLastLoginAt() != null
                ? u.getLastLoginAt().toString().replace("T", " ").substring(0, Math.min(16, u.getLastLoginAt().toString().length()))
                : "없음";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dbId", u.getId());
        m.put("id", "U-" + u.getId());
        m.put("name", u.getName());
        m.put("role", u.getRole());
        m.put("email", u.getEmail());
        m.put("status", u.getEnabled() ? "active" : "inactive");
        m.put("statusLabel", statusLabel);
        m.put("statusColor", statusColor);
        m.put("mfa", u.getMfaEnabled());
        m.put("lastLogin", lastLogin);
        m.put("loginCount", u.getLoginAttempts());
        m.put("perms", List.of(u.getRole()));
        return m;
    }

    private Map<String, Object> toIncidentMap(SecurityEvent e) {
        String duration = "N/A";
        if (e.getResolvedAt() != null && e.getCreatedAt() != null) {
            long minutes = java.time.Duration.between(e.getCreatedAt(), e.getResolvedAt()).toMinutes();
            duration = minutes + "분";
        }
        String sev = e.getSeverity() != null ? e.getSeverity() : "medium";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "INC-" + e.getId());
        m.put("title", (e.getEventType() != null ? e.getEventType() : "보안 이벤트") + " 감지");
        m.put("severity", sev);
        m.put("date", e.getCreatedAt() != null ? e.getCreatedAt().toLocalDate().toString() : "");
        m.put("response", duration);
        m.put("handler", e.getHandledBy() != null ? e.getHandledBy() : "시스템");
        m.put("status", "처리완료");
        return m;
    }
    
    private Map<String, Object> toAgentMap(Agent a) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        
        String status = a.getStatus() != null ? a.getStatus() : "offline";
        String statusLabel = switch (status) {
            case "online" -> "온라인";
            case "offline" -> "오프라인";
            case "error" -> "오류";
            default -> status;
        };
        String statusColor = switch (status) {
            case "online" -> "#06d6a0";
            case "offline" -> "#ff3d5a";
            case "error" -> "#ff8c42";
            default -> "#4a5570";
        };
        
        String lastHeartbeat = a.getLastHeartbeat() != null 
                ? a.getLastHeartbeat().format(formatter)
                : "없음";
        String registeredAt = a.getRegisteredAt() != null
                ? a.getRegisteredAt().format(formatter)
                : "";
        
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dbId", a.getId());
        m.put("id", "AG-" + a.getId());
        m.put("agentName", a.getAgentName());
        m.put("hostname", a.getHostname());
        m.put("ipAddress", a.getIpAddress());
        m.put("osType", a.getOsType() != null ? a.getOsType() : "Unknown");
        m.put("osVersion", a.getOsVersion() != null ? a.getOsVersion() : "");
        m.put("agentVersion", a.getAgentVersion() != null ? a.getAgentVersion() : "1.0.0");
        m.put("username", a.getUsername() != null ? a.getUsername() : "Unknown");
        m.put("status", status);
        m.put("statusLabel", statusLabel);
        m.put("statusColor", statusColor);
        m.put("lastHeartbeat", lastHeartbeat);
        m.put("registeredAt", registeredAt);
        m.put("totalEvents", a.getTotalEvents());
        m.put("totalTrafficLogs", a.getTotalTrafficLogs());
        m.put("ownerUserId", a.getOwnerUserId());
        m.put("lastFirewallAppliedRevision", a.getLastFirewallAppliedRevision());
        String fwAt = a.getLastFirewallStatusAt() != null
                ? a.getLastFirewallStatusAt().format(formatter)
                : "—";
        m.put("lastFirewallStatusAt", fwAt);
        m.put("metadata", a.getMetadata() != null ? a.getMetadata() : "{}");
        
        return m;
    }
}
