package com.example.beacon.controller;

import com.example.beacon.entity.Agent;
import com.example.beacon.repository.SecurityEventRepository;
import com.example.beacon.service.AgentService;
import com.example.beacon.service.NetworkStatsService;
import com.sun.management.OperatingSystemMXBean;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/network")
@RequiredArgsConstructor
public class NetworkTopologyController {

    private final SecurityEventRepository securityEventRepository;
    private final AgentService agentService;
    private final NetworkStatsService networkStatsService;

    private static final int AGENT_MAX_PER_ROW = 5;
    private static final int AGENT_START_Y    = 112;
    private static final int AGENT_ROW_HEIGHT = 18;

    @GetMapping("/topology")
    public ResponseEntity<Map<String, Object>> getTopology() {

        // ── 시스템 메트릭 수집 ─────────────────────────────────────────
        OperatingSystemMXBean os =
                ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        double cpuLoad = os.getCpuLoad();
        int cpu = cpuLoad >= 0 ? (int) Math.round(cpuLoad * 100) : 0;
        int net = networkStatsService.getUsagePercent();

        // ── 최근 보안 이벤트 집계 ─────────────────────────────────────
        LocalDateTime last10min = LocalDateTime.now().minusMinutes(10);
        LocalDateTime last1hour = LocalDateTime.now().minusHours(1);
        long criticalRecent = countBySeverity("critical", last10min);
        long highRecent     = countBySeverity("high",     last10min);
        long totalLastHour  = securityEventRepository.findByDateRange(last1hour, LocalDateTime.now()).size();

        // ── 에이전트 수집 ─────────────────────────────────────────────
        List<Agent> allAgents    = agentService.getAllAgents();
        List<Agent> onlineAgents = agentService.getOnlineAgents();
        long onlineCount  = onlineAgents.size();
        long totalAgents  = allAgents.size();

        // ── 인프라 노드 상태 계산 ─────────────────────────────────────
        String fwStatus  = criticalRecent > 3 ? "error" : criticalRecent > 0 ? "warn" : "ok";
        String idsStatus = totalLastHour > 50 ? "warn" : "ok";
        String webStatus = cpu > 85 ? "warn" : "ok";
        String apiStatus = highRecent > 5 ? "warn" : "ok";
        String dbStatus  = deriveFromAgents(onlineCount, totalAgents);
        String appStatus = deriveFromAgents(onlineCount, totalAgents);
        String dmzStatus = worstOf(webStatus, apiStatus);
        String intStatus = worstOf(dbStatus, appStatus);

        // ── 인프라 노드 목록 ──────────────────────────────────────────
        List<Map<String, Object>> nodes = new ArrayList<>(List.of(
                node("FW",  "방화벽",  50, 50, "firewall", fwStatus,  cpu,
                        "이벤트 " + criticalRecent + "건 (10분)"),
                node("DMZ", "DMZ",    50, 25, "zone",     dmzStatus, net,
                        "네트워크 " + net + "%"),
                node("WEB", "웹서버",  28, 12, "server",   webStatus, cpu,
                        "CPU " + cpu + "%"),
                node("API", "API서버", 72, 12, "server",   apiStatus, 0,
                        highRecent + "건 고위험"),
                node("INT", "내부망",  50, 75, "zone",     intStatus, 0,
                        "에이전트 " + onlineCount + "/" + totalAgents),
                node("DB",  "DB서버",  28, 90, "server",   dbStatus,  0,
                        "에이전트 " + onlineCount + "/" + totalAgents),
                node("APP", "앱서버",  72, 90, "server",   appStatus, 0,
                        "에이전트 " + onlineCount + "/" + totalAgents),
                node("IDS", "IDS",    82, 50, "sensor",   idsStatus, 0,
                        "1시간 " + totalLastHour + "건")
        ));

        // ── 인프라 링크 목록 ──────────────────────────────────────────
        List<Map<String, Object>> links = new ArrayList<>(List.of(
                link("FW",  "DMZ", !"error".equals(fwStatus)),
                link("FW",  "INT", !"error".equals(fwStatus)),
                link("DMZ", "WEB", !"error".equals(dmzStatus)),
                link("DMZ", "API", !"error".equals(dmzStatus)),
                link("INT", "DB",  !"error".equals(intStatus)),
                link("INT", "APP", !"error".equals(intStatus)),
                link("FW",  "IDS", true)
        ));

        // ── 온라인 에이전트 노드 동적 배치 ───────────────────────────
        for (int i = 0; i < onlineAgents.size(); i++) {
            Agent a = onlineAgents.get(i);

            int row     = i / AGENT_MAX_PER_ROW;
            int col     = i % AGENT_MAX_PER_ROW;
            int rowSize = Math.min(AGENT_MAX_PER_ROW,
                                   onlineAgents.size() - row * AGENT_MAX_PER_ROW);

            // 행 내에서 균등 분배 (10~90 범위)
            int x = rowSize == 1
                    ? 50
                    : (int) Math.round(10 + col * 80.0 / (rowSize - 1));
            int y = AGENT_START_Y + row * AGENT_ROW_HEIGHT;

            String agentId    = "AG-" + a.getId();
            String label      = shorten(a.getHostname() != null ? a.getHostname()
                                        : a.getAgentName(), 10);
            String detail     = (a.getIpAddress() != null ? a.getIpAddress() : "")
                              + " · " + (a.getOsType() != null ? a.getOsType() : "");

            nodes.add(node(agentId, label, x, y, "agent", "ok", 0, detail));
            links.add(link("INT", agentId, true));
        }

        // ── 요약 ──────────────────────────────────────────────────────
        long okCount   = nodes.stream().filter(n -> "ok".equals(n.get("status"))).count();
        long warnCount = nodes.stream().filter(n -> "warn".equals(n.get("status"))).count();
        long errCount  = nodes.stream().filter(n -> "error".equals(n.get("status"))).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ok",           okCount);
        summary.put("warn",         warnCount);
        summary.put("error",        errCount);
        summary.put("agentOnline",  onlineCount);
        summary.put("agentTotal",   totalAgents);

        // ── SVG viewBox 높이 동적 계산 ────────────────────────────────
        int rows       = onlineAgents.isEmpty() ? 0
                       : (onlineAgents.size() + AGENT_MAX_PER_ROW - 1) / AGENT_MAX_PER_ROW;
        int viewBoxH   = onlineAgents.isEmpty() ? 100
                       : AGENT_START_Y + rows * AGENT_ROW_HEIGHT + 6;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes",      nodes);
        result.put("links",      links);
        result.put("summary",    summary);
        result.put("viewBoxH",   viewBoxH);
        result.put("hasAgents",  !onlineAgents.isEmpty());
        result.put("timestamp",  System.currentTimeMillis());

        return ResponseEntity.ok(result);
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────

    private long countBySeverity(String severity, LocalDateTime since) {
        try {
            return securityEventRepository
                    .findByDateRange(since, LocalDateTime.now())
                    .stream()
                    .filter(e -> severity.equals(e.getSeverity()))
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }

    private String deriveFromAgents(long onlineCount, long totalAgents) {
        if (totalAgents == 0) return "ok";
        double ratio = (double) onlineCount / totalAgents;
        return ratio >= 0.8 ? "ok" : ratio >= 0.5 ? "warn" : "error";
    }

    private String shorten(String s, int maxLen) {
        if (s == null) return "?";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }

    private String worstOf(String a, String b) {
        if ("error".equals(a) || "error".equals(b)) return "error";
        if ("warn".equals(a)  || "warn".equals(b))  return "warn";
        return "ok";
    }

    private Map<String, Object> node(String id, String label, int x, int y,
                                     String type, String status, int load, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",     id);
        m.put("label",  label);
        m.put("x",      x);
        m.put("y",      y);
        m.put("type",   type);
        m.put("status", status);
        m.put("load",   load);
        m.put("detail", detail);
        return m;
    }

    private Map<String, Object> link(String from, String to, boolean active) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from",   from);
        m.put("to",     to);
        m.put("active", active);
        return m;
    }
}
