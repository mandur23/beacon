package com.example.beacon.controller;

import com.example.beacon.entity.Agent;
import com.example.beacon.entity.FirewallRule;
import com.example.beacon.entity.SecurityEvent;
import com.example.beacon.entity.User;
import com.example.beacon.repository.UserRepository;
import com.example.beacon.service.AgentService;
import com.example.beacon.service.FirewallService;
import com.example.beacon.service.SecurityEventService;
import com.example.beacon.service.TrafficAnalysisService;
import com.example.beacon.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

    private final SecurityEventService securityEventService;
    private final TrafficAnalysisService trafficAnalysisService;
    private final UserSessionService userSessionService;
    private final FirewallService firewallService;
    private final UserRepository userRepository;
    private final AgentService agentService;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("page", "dashboard");
        model.addAttribute("pageTitle", "대시보드");

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        long blockedToday = securityEventService.getBlockedEventsCount(todayStart);
        long activeSessions = userSessionService.getActiveSessionCount();
        long unresolvedCount = securityEventService.countUnresolvedEvents();

        List<Map<String, Object>> stats = new ArrayList<>();
        stats.add(kpi("오늘 차단", "🛡", blockedToday, "차단된 이벤트", "#ff3d5a", false));
        stats.add(kpi("활성 세션", "◎", activeSessions, "현재 접속 중", "#00e5ff", false));
        stats.add(kpi("미해결 이벤트", "⚠", unresolvedCount, "즉시 검토 필요", "#ffd166", unresolvedCount > 0));
        stats.add(kpi("보안 점수", "✓", "87", "양호 수준", "#06d6a0", false));
        model.addAttribute("stats", stats);

        List<Integer> hourlyData = securityEventService.getHourlyData();
        model.addAttribute("hourlyData", hourlyData);

        List<Map<String, Object>> recentEvents = securityEventService.getHighRiskEvents(5.0)
                .stream().limit(5).map(this::toEventMap).collect(Collectors.toList());
        model.addAttribute("recentEvents", recentEvents);

        return "pages/dashboard";
    }

    @GetMapping("/threats")
    public String threats(Model model,
                          @RequestParam(defaultValue = "all") String severity,
                          @RequestParam(defaultValue = "") String search) {
        model.addAttribute("page", "threats");
        model.addAttribute("pageTitle", "위협 / 이벤트 로그");
        model.addAttribute("severityCounts", securityEventService.getSeverityCounts());
        model.addAttribute("currentSeverity", severity);
        model.addAttribute("search", search);

        List<Map<String, Object>> events = securityEventService.getEventsWithFilters(severity, search)
                .stream().map(this::toEventMap).collect(Collectors.toList());
        model.addAttribute("events", events);

        return "pages/threats";
    }

    @GetMapping("/network")
    public String network(Model model,
                          @RequestParam(defaultValue = "topology") String tab) {
        model.addAttribute("page", "network");
        model.addAttribute("pageTitle", "네트워크 모니터링");
        model.addAttribute("tab", tab);

        LocalDateTime lastHour = LocalDateTime.now().minusHours(1);
        long bandwidth = trafficAnalysisService.getTotalBytesTransferred(lastHour);
        model.addAttribute("bandwidthLastHour", bandwidth);
        model.addAttribute("protocolStats", trafficAnalysisService.getProtocolStatistics());
        model.addAttribute("metrics", List.of(
                Map.of("label", "대역폭(1시간)", "value", bandwidth, "color", "#00e5ff", "pct", 42),
                Map.of("label", "활성 세션", "value", userSessionService.getActiveSessionCount(), "color", "#06d6a0", "pct", 58),
                Map.of("label", "차단 이벤트", "value", securityEventService.getBlockedEventsCount(lastHour), "color", "#ff3d5a", "pct", 34),
                Map.of("label", "평균 지연", "value", "22ms", "color", "#ffd166", "pct", 27)
        ));
        model.addAttribute("nodes", List.of(
                Map.of("id", "FW-01", "label", "방화벽", "type", "firewall", "status", "ok"),
                Map.of("id", "IDS-01", "label", "침입탐지", "type", "sensor", "status", "ok"),
                Map.of("id", "WEB-01", "label", "웹서버", "type", "server", "status", "ok"),
                Map.of("id", "DB-01", "label", "DB서버", "type", "server", "status", "warn")
        ));
        model.addAttribute("interfaces", List.of(
                Map.of("name", "eth0", "ip", "10.0.0.10", "speed", "1Gbps", "tx", "124MB/s", "rx", "98MB/s", "packets", "143212", "errors", "0", "status", "up"),
                Map.of("name", "eth1", "ip", "172.16.0.10", "speed", "1Gbps", "tx", "74MB/s", "rx", "112MB/s", "packets", "102442", "errors", "1", "status", "up")
        ));

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

        model.addAttribute("policies", List.of(
                Map.of("title", "기본 차단 정책", "desc", "허용되지 않은 외부 포트 접근 차단", "color", "#ff3d5a"),
                Map.of("title", "내부망 보호", "desc", "DMZ → 내부망 접근 최소화", "color", "#00e5ff"),
                Map.of("title", "관리자 접근", "desc", "관리 포트는 화이트리스트만 허용", "color", "#ffd166")
        ));

        return "pages/firewall";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("page", "reports");
        model.addAttribute("pageTitle", "보고서 / 분석");

        LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);
        long blockedLastMonth = securityEventService.getBlockedEventsCount(lastMonth);

        Map<String, Object> reportStats = new LinkedHashMap<>();
        reportStats.put("blocked", blockedLastMonth);
        reportStats.put("score", "87");
        reportStats.put("resolveRate", "96%");
        reportStats.put("responseTime", "12분");
        model.addAttribute("reportStats", reportStats);

        model.addAttribute("severityDistribution", securityEventService.getSeverityCounts());
        model.addAttribute("threatDistribution", List.of(
                Map.of("label", "Brute Force", "pct", 34, "color", "#ff3d5a"),
                Map.of("label", "SQL Injection", "pct", 24, "color", "#ff8c42"),
                Map.of("label", "XSS", "pct", 18, "color", "#ffd166"),
                Map.of("label", "기타", "pct", 24, "color", "#00e5ff")
        ));

        List<Map<String, Object>> incidents = securityEventService.getRecentResolved()
                .stream().map(this::toIncidentMap).collect(Collectors.toList());
        model.addAttribute("incidents", incidents);
        model.addAttribute("monthlyScores", List.of(75, 77, 79, 80, 81, 83, 84, 84, 85, 86, 87, 87));

        return "pages/reports";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
    
    @GetMapping("/agents")
    public String agents(Model model) {
        model.addAttribute("page", "agents");
        model.addAttribute("pageTitle", "에이전트 관리");
        
        agentService.updateAgentStatus();
        
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
        m.put("ip", e.getSourceIp() != null ? e.getSourceIp() : "N/A");
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
        
        return m;
    }
}
