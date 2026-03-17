package com.example.beacon.service;

import com.example.beacon.entity.SecurityEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Suricata eve.json 파일을 tail 방식으로 실시간 감시하여 EVE JSON 이벤트를 처리한다.
 * Windows 환경에서 syslog UDP 대신 사용하는 파일 기반 수집 방식.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "suricata.eve.enabled", havingValue = "true", matchIfMissing = false)
public class EveJsonFileWatcherService {

    private static final Set<String> SKIP_EVENT_TYPES = Set.of(
            "stats", "flow", "netflow", "fileinfo", "packetinfo"
    );

    @Value("${suricata.eve.path:C:\\Program Files\\Suricata\\log\\eve.json}")
    private String eveJsonPath;

    @Value("${suricata.eve.poll-interval-ms:500}")
    private long pollIntervalMs;

    private static final String SURICATA_AGENT_NAME = "suricata-local";

    private final SecurityEventService securityEventService;
    private final AgentService agentService;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;

    @PostConstruct
    public void start() {
        registerSuricataAgent();

        Path path = Path.of(eveJsonPath);
        if (!Files.exists(path)) {
            log.warn("[EveWatcher] eve.json 파일을 찾을 수 없습니다: {} — Suricata가 실행 중인지 확인하세요.", eveJsonPath);
        }
        running.set(true);
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "eve-json-watcher");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::watchLoop);
        log.info("[EveWatcher] eve.json 감시 시작: {}", eveJsonPath);
    }

    private void registerSuricataAgent() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String ip       = InetAddress.getLocalHost().getHostAddress();
            String os       = System.getProperty("os.name", "Windows");
            String osVer    = System.getProperty("os.version", "");
            String metadata = "{\"sensor\":\"Suricata\",\"version\":\"8.0.3\",\"eve_path\":\"" + eveJsonPath.replace("\\", "\\\\") + "\"}";

            agentService.registerOrUpdateAgent(
                    SURICATA_AGENT_NAME, hostname, ip,
                    os, osVer, "Suricata-8.0.3",
                    System.getProperty("user.name", "system"),
                    metadata
            );
            log.info("[EveWatcher] Suricata 에이전트 등록 완료 - hostname={}, ip={}", hostname, ip);
        } catch (Exception e) {
            log.error("[EveWatcher] Suricata 에이전트 등록 실패", e);
        }
    }

    /**
     * 60초마다 eve.json 파일이 최근 2분 내에 수정됐는지 확인하여
     * Suricata 실제 실행 여부를 판단하고 하트비트를 전송한다.
     */
    @Scheduled(fixedRate = 60000)
    public void heartbeat() {
        if (!running.get()) return;

        if (isSuricataActive()) {
            agentService.updateHeartbeat(SURICATA_AGENT_NAME);
            log.debug("[EveWatcher] Suricata 하트비트 전송 (active)");
        } else {
            agentService.disconnectAgent(SURICATA_AGENT_NAME);
            log.info("[EveWatcher] Suricata 비활성 감지 — 에이전트 offline 처리");
        }
    }

    private boolean isSuricataActive() {
        try {
            Path path = Path.of(eveJsonPath);
            if (!Files.exists(path)) return false;
            long lastModifiedMs = Files.getLastModifiedTime(path).toMillis();
            long ageMs = System.currentTimeMillis() - lastModifiedMs;
            return ageMs < 120_000; // 2분 이내에 수정된 경우만 active
        } catch (IOException e) {
            return false;
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (executor != null) executor.shutdownNow();
        log.info("[EveWatcher] eve.json 감시 종료");
    }

    private void watchLoop() {
        Path filePath = Path.of(eveJsonPath);
        long position = resolveInitialPosition(filePath);

        while (running.get()) {
            try {
                if (!Files.exists(filePath)) {
                    Thread.sleep(pollIntervalMs * 4);
                    continue;
                }

                long fileSize = Files.size(filePath);

                // 파일이 교체(rotate)된 경우 처음부터 다시 읽기
                if (fileSize < position) {
                    log.info("[EveWatcher] 파일 교체 감지 — 처음부터 읽습니다.");
                    position = 0;
                }

                if (fileSize > position) {
                    position = readNewLines(filePath, position);
                }

                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[EveWatcher] 파일 감시 중 오류 발생", e);
                safeSleep(pollIntervalMs * 4);
            }
        }
    }

    private long resolveInitialPosition(Path filePath) {
        try {
            return Files.exists(filePath) ? Files.size(filePath) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    private long readNewLines(Path filePath, long startPosition) {
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            raf.seek(startPosition);
            String line;
            while ((line = raf.readLine()) != null) {
                processLine(line.trim());
            }
            return raf.getFilePointer();
        } catch (IOException e) {
            log.error("[EveWatcher] 파일 읽기 오류", e);
            return startPosition;
        }
    }

    private void processLine(String line) {
        if (line.isEmpty() || !line.startsWith("{")) return;

        try {
            JsonNode root = objectMapper.readTree(line);

            String eventType = root.path("event_type").asText("").toLowerCase();
            if (SKIP_EVENT_TYPES.contains(eventType)) {
                log.debug("[EveWatcher] 비보안 이벤트 타입 스킵: {}", eventType);
                return;
            }

            SecurityEvent event = mapToSecurityEvent(root, line);
            securityEventService.createEvent(event);
            log.info("[EveWatcher] SecurityEvent 저장 완료 - type={}, src={}", event.getEventType(), event.getSourceIp());
        } catch (Exception e) {
            log.error("[EveWatcher] 이벤트 처리 오류: {}", line, e);
        }
    }

    private SecurityEvent mapToSecurityEvent(JsonNode root, String rawJson) {
        String eventType = textOrDefault(root, "event_type", "unknown");
        String srcIp     = textOrDefault(root, "src_ip",     "0.0.0.0");
        String destIp    = textOrDefault(root, "dest_ip",    null);
        String proto     = textOrDefault(root, "proto",      "UDP").toUpperCase();
        int    destPort  = root.path("dest_port").asInt(0);

        JsonNode alertNode       = root.path("alert");
        int suricataSeverity     = alertNode.path("severity").asInt(3);
        String signature         = alertNode.path("signature").asText("");
        String category          = alertNode.path("category").asText("");

        String severity  = mapSeverity(suricataSeverity);
        double riskScore = mapRiskScore(suricataSeverity);

        String description = buildDescription(eventType, signature, category);
        String status = "alert".equalsIgnoreCase(eventType) ? "차단됨" : "탐지됨";

        return SecurityEvent.builder()
                .eventType(truncate(eventType.isEmpty() ? "unknown" : eventType, 100))
                .severity(severity)
                .sourceIp(truncate(srcIp, 45))
                .destinationIp(destIp != null ? truncate(destIp, 45) : null)
                .protocol(truncate(proto, 20))
                .port(destPort)
                .status(truncate(status, 50))
                .description(description)
                .metadata(rawJson)
                .blocked("alert".equalsIgnoreCase(eventType))
                .riskScore(riskScore)
                .build();
    }

    private String mapSeverity(int s) {
        return switch (s) { case 1 -> "HIGH"; case 2 -> "MEDIUM"; case 3 -> "LOW"; default -> "INFO"; };
    }

    private double mapRiskScore(int s) {
        return switch (s) { case 1 -> 90.0; case 2 -> 60.0; case 3 -> 30.0; default -> 10.0; };
    }

    private String buildDescription(String eventType, String signature, String category) {
        StringBuilder sb = new StringBuilder("[").append(eventType).append("]");
        if (!signature.isBlank()) sb.append(" ").append(signature);
        if (!category.isBlank())  sb.append(" | ").append(category);
        return sb.toString();
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? defaultValue : v.asText();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private void safeSleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
