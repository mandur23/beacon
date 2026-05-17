package com.example.beacon.controller;

import com.example.beacon.entity.Agent;
import com.example.beacon.entity.TrafficLog;
import com.example.beacon.repository.SecurityEventRepository;
import com.example.beacon.repository.TrafficLogRepository;
import com.example.beacon.service.AgentService;
import com.example.beacon.service.GeoIpService;
import com.example.beacon.service.NetworkStatsService;
import com.sun.management.OperatingSystemMXBean;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/network")
@RequiredArgsConstructor
public class NetworkTopologyController {

    private final SecurityEventRepository securityEventRepository;
    private final AgentService agentService;
    private final NetworkStatsService networkStatsService;
    private final TrafficLogRepository trafficLogRepository;
    private final GeoIpService geoIpService;

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

    // ── 글로벌 네트워크 Globe 데이터 ──────────────────────────────────
    //
    // 최근 트래픽 로그(기본 5분)를 IP 쌍별로 묶어 GeoIP 좌표로 매핑한 뒤
    // BEACON Globe 위에 그릴 cities + links 형태로 변환해 반환한다.
    // 새 IP 조회는 호출당 최대 GLOBE_NEW_LOOKUP_LIMIT 건으로 제한해
    // ip-api.com 무료 티어 속도 제한(45 req/min)을 보호한다.

    private static final int GLOBE_DEFAULT_WINDOW_MIN = 5;
    private static final int GLOBE_MAX_WINDOW_MIN     = 60;
    private static final int GLOBE_MAX_CITIES         = 24;
    private static final int GLOBE_MAX_LINKS          = 28;
    private static final int GLOBE_NEW_LOOKUP_LIMIT   = 25;

    @GetMapping("/globe")
    public ResponseEntity<Map<String, Object>> getGlobeData(
            @RequestParam(defaultValue = "5") int windowMinutes) {

        int window = Math.max(1, Math.min(GLOBE_MAX_WINDOW_MIN, windowMinutes));
        LocalDateTime since = LocalDateTime.now().minusMinutes(window);
        LocalDateTime until = LocalDateTime.now();

        List<TrafficLog> logs;
        try {
            logs = trafficLogRepository.findByDateRange(since, until);
        } catch (Exception e) {
            logs = Collections.emptyList();
        }

        // ── IP 쌍별 집계 ─────────────────────────────────────────────
        // key: "src|dst" (정렬해 양방향을 하나의 링크로 묶음)
        Map<String, IpPairAgg> aggByPair = new LinkedHashMap<>();
        Set<String> ipsToResolve = new LinkedHashSet<>();

        for (TrafficLog t : logs) {
            String src = nullSafe(t.getSourceIp());
            String dst = nullSafe(t.getDestinationIp());
            if (src.isBlank() || dst.isBlank() || src.equals(dst)) continue;

            String a = src.compareTo(dst) < 0 ? src : dst;
            String b = src.compareTo(dst) < 0 ? dst : src;
            String key = a + "|" + b;

            IpPairAgg agg = aggByPair.computeIfAbsent(key, k -> new IpPairAgg(a, b));
            agg.count += 1;
            agg.bytes += t.getBytesTransferred() != null ? t.getBytesTransferred() : 0L;
            if (Boolean.TRUE.equals(t.getIsAnomaly())) {
                agg.anomalyCount += 1;
                if (t.getAnomalyScore() != null) {
                    agg.maxAnomalyScore = Math.max(agg.maxAnomalyScore, t.getAnomalyScore());
                }
            }
            agg.protocols.add(nullSafe(t.getProtocol()));
            LocalDateTime ts = t.getTimestamp();
            if (ts != null) {
                if (agg.firstSeen == null || ts.isBefore(agg.firstSeen)) agg.firstSeen = ts;
                if (agg.lastSeen  == null || ts.isAfter(agg.lastSeen))   agg.lastSeen  = ts;
            }

            ipsToResolve.add(a);
            ipsToResolve.add(b);
        }

        // ── GeoIP 조회 (캐시 우선; 새 조회는 호출당 상한) ────────────
        Map<String, GeoIpService.GeoLocation> geoMap = new LinkedHashMap<>();
        int newLookups = 0;
        for (String ip : ipsToResolve) {
            // 사설 IP 또는 캐시 hit은 항상 조회(거의 무료).
            // 외부 IP의 첫 조회는 한도까지만 허용.
            boolean local = geoIpService.isLocalAddress(ip);
            if (!local && newLookups >= GLOBE_NEW_LOOKUP_LIMIT) {
                continue;
            }
            GeoIpService.GeoLocation loc = geoIpService.lookup(ip);
            geoMap.put(ip, loc);
            if (!local) newLookups += 1;
        }

        // ── 좌표를 같이 사용하는 IP들을 도시 단위로 dedup ─────────────
        // key: round(lat,1) + "_" + round(lon,1) — 비슷한 좌표는 한 점으로
        Map<String, GlobeCity> cityByGeo = new LinkedHashMap<>();
        Map<String, Integer> ipToCityIndex = new HashMap<>();

        List<GlobeCity> cities = new ArrayList<>();

        for (Map.Entry<String, GeoIpService.GeoLocation> e : geoMap.entrySet()) {
            GeoIpService.GeoLocation g = e.getValue();
            String geoKey = round1(g.lat()) + "_" + round1(g.lon());

            GlobeCity city = cityByGeo.get(geoKey);
            if (city == null) {
                city = new GlobeCity(
                        cities.size(),
                        g.code(),
                        g.city(),
                        g.country(),
                        g.lat(),
                        g.lon(),
                        g.isLocal());
                cityByGeo.put(geoKey, city);
                cities.add(city);
            }
            city.ipCount += 1;
            ipToCityIndex.put(e.getKey(), city.index);
        }

        // ── 링크 생성 (도시 인덱스 기반) ─────────────────────────────
        Map<String, GlobeLink> linkByCityPair = new LinkedHashMap<>();
        for (IpPairAgg agg : aggByPair.values()) {
            Integer ci = ipToCityIndex.get(agg.a);
            Integer cj = ipToCityIndex.get(agg.b);
            if (ci == null || cj == null || ci.equals(cj)) continue;

            int lo = Math.min(ci, cj);
            int hi = Math.max(ci, cj);
            String key = lo + "-" + hi;
            GlobeLink link = linkByCityPair.computeIfAbsent(key, k -> new GlobeLink(lo, hi));
            link.bytes += agg.bytes;
            link.count += agg.count;
            link.anomalyCount += agg.anomalyCount;
            link.maxAnomalyScore = Math.max(link.maxAnomalyScore, agg.maxAnomalyScore);
            if (agg.firstSeen != null && (link.firstSeen == null || agg.firstSeen.isBefore(link.firstSeen))) {
                link.firstSeen = agg.firstSeen;
            }
            if (agg.lastSeen != null && (link.lastSeen == null || agg.lastSeen.isAfter(link.lastSeen))) {
                link.lastSeen = agg.lastSeen;
            }
        }

        // 링크 정렬: bytes 내림차순 → 상위만 노출
        List<GlobeLink> sortedLinks = new ArrayList<>(linkByCityPair.values());
        sortedLinks.sort((x, y) -> Long.compare(y.bytes, x.bytes));
        if (sortedLinks.size() > GLOBE_MAX_LINKS) {
            sortedLinks = new ArrayList<>(sortedLinks.subList(0, GLOBE_MAX_LINKS));
        }

        // 노출할 도시: 사용된 링크에 등장한 도시 + 트래픽이 많은 상위 도시
        Set<Integer> usedCityIdx = new LinkedHashSet<>();
        for (GlobeLink l : sortedLinks) {
            usedCityIdx.add(l.a);
            usedCityIdx.add(l.b);
            if (usedCityIdx.size() >= GLOBE_MAX_CITIES) break;
        }
        List<GlobeCity> outCities = new ArrayList<>();
        Map<Integer, Integer> oldToNewIdx = new HashMap<>();
        for (Integer oldIdx : usedCityIdx) {
            oldToNewIdx.put(oldIdx, outCities.size());
            outCities.add(cities.get(oldIdx));
        }

        // ── 응답 페이로드 ────────────────────────────────────────────
        List<Map<String, Object>> citiesJson = new ArrayList<>();
        for (GlobeCity c : outCities) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code",    c.code);
            m.put("name",    c.name);
            m.put("country", c.country);
            m.put("lat",     c.lat);
            m.put("lon",     c.lon);
            m.put("local",   c.isLocal);
            m.put("ipCount", c.ipCount);
            citiesJson.add(m);
        }

        List<Map<String, Object>> linksJson = new ArrayList<>();
        long totalBytes = 0;
        long totalAnomaly = 0;
        long liveCount = 0;
        long minLastSec = Long.MAX_VALUE;
        LocalDateTime nowTs = LocalDateTime.now();
        for (GlobeLink l : sortedLinks) {
            Integer na = oldToNewIdx.get(l.a);
            Integer nb = oldToNewIdx.get(l.b);
            if (na == null || nb == null) continue;

            long lastSec = -1;
            long durSec  = 0;
            if (l.lastSeen != null) {
                lastSec = Math.max(0, Duration.between(l.lastSeen, nowTs).getSeconds());
                if (lastSec < minLastSec) minLastSec = lastSec;
                // 마지막 record가 30초 이내면 "live"로 분류
                if (lastSec <= 30) liveCount += 1;
            }
            if (l.firstSeen != null && l.lastSeen != null) {
                durSec = Math.max(0, Duration.between(l.firstSeen, l.lastSeen).getSeconds());
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("a",        na);
            m.put("b",        nb);
            m.put("sev",      classifySeverity(l));
            m.put("bytes",    l.bytes);
            m.put("count",    l.count);
            m.put("anomaly",  l.anomalyCount);
            m.put("lastSec",  lastSec);
            m.put("durSec",   durSec);
            linksJson.add(m);
            totalBytes += l.bytes;
            totalAnomaly += l.anomalyCount;
        }
        if (minLastSec == Long.MAX_VALUE) minLastSec = -1;

        // 처리량 표기 (5분 합계 → bps 환산)
        long windowSeconds = (long) window * 60L;
        double bitsPerSec = windowSeconds > 0 ? (totalBytes * 8.0) / windowSeconds : 0;
        String throughput = formatBitsPerSec(bitsPerSec);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeLinks",  linksJson.size());
        stats.put("liveLinks",    liveCount);
        stats.put("nodes",        citiesJson.size());
        stats.put("threats",      totalAnomaly);
        stats.put("throughput",   throughput);
        stats.put("totalBytes",   totalBytes);
        stats.put("minLastSec",   minLastSec);
        stats.put("windowMinutes", window);
        stats.put("windowStart",  since.toString());
        stats.put("generatedAt",  System.currentTimeMillis());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cities", citiesJson);
        result.put("links",  linksJson);
        result.put("stats",  stats);
        return ResponseEntity.ok(result);
    }

    // ── Globe 헬퍼 / DTO ─────────────────────────────────────────────

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    /**
     * 링크 심각도 분류 (Globe SEVERITY 4분류와 매칭).
     *  - threat   : 이상치 점수 ≥ 7 또는 이상 트래픽이 다수
     *  - warning  : 이상치 점수 ≥ 3 또는 이상 트래픽 1건+
     *  - behavior : 이상치 점수 ≥ 1 (행위 분석 후보)
     *  - normal   : 그 외
     */
    private static String classifySeverity(GlobeLink l) {
        if (l.maxAnomalyScore >= 7.0 || l.anomalyCount >= 3) return "threat";
        if (l.maxAnomalyScore >= 3.0 || l.anomalyCount >= 1) return "warning";
        if (l.maxAnomalyScore >= 1.0)                          return "behavior";
        return "normal";
    }

    private static String formatBitsPerSec(double bps) {
        if (bps >= 1e9) return String.format(Locale.ROOT, "%.2f Gb/s", bps / 1e9);
        if (bps >= 1e6) return String.format(Locale.ROOT, "%.2f Mb/s", bps / 1e6);
        if (bps >= 1e3) return String.format(Locale.ROOT, "%.1f Kb/s", bps / 1e3);
        return String.format(Locale.ROOT, "%.0f b/s", bps);
    }

    private static final class IpPairAgg {
        final String a;
        final String b;
        long  bytes;
        int   count;
        int   anomalyCount;
        double maxAnomalyScore;
        LocalDateTime firstSeen;
        LocalDateTime lastSeen;
        final Set<String> protocols = new LinkedHashSet<>();
        IpPairAgg(String a, String b) { this.a = a; this.b = b; }
    }

    private static final class GlobeCity {
        final int    index;
        final String code;
        final String name;
        final String country;
        final double lat;
        final double lon;
        final boolean isLocal;
        int ipCount;
        GlobeCity(int idx, String code, String name, String country,
                  double lat, double lon, boolean isLocal) {
            this.index = idx;
            this.code = code;
            this.name = name;
            this.country = country;
            this.lat = lat;
            this.lon = lon;
            this.isLocal = isLocal;
        }
    }

    private static final class GlobeLink {
        final int a;
        final int b;
        long  bytes;
        int   count;
        int   anomalyCount;
        double maxAnomalyScore;
        LocalDateTime firstSeen;
        LocalDateTime lastSeen;
        GlobeLink(int a, int b) { this.a = a; this.b = b; }
    }
}
