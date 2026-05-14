package com.example.beacon.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Llama 3B 모델 자동 부트스트랩 + 호출 서비스.
 *
 * 동작 흐름:
 *   1) 애플리케이션 시작 시 비동기로 ensureReady() 실행
 *   2) Ollama 데몬(http://localhost:11434) 응답 확인
 *   3) 미실행이면 'ollama serve' 시도 (PATH에 있을 경우)
 *   4) 설치 자체가 안 되어 있으면 OS별 자동 설치
 *      - Windows: winget → 실패 시 OllamaSetup.exe 다운로드 후 사일런트 설치
 *      - Linux/macOS: 공식 install.sh 실행
 *   5) llama3.2:3b 모델이 없으면 /api/pull 로 자동 다운로드
 *
 * 모든 단계는 비동기·논블로킹이며 진행 상태는 getStatus()로 조회한다.
 */
@Slf4j
@Service
public class OllamaService {

    public enum Phase {
        NOT_STARTED, CHECKING, INSTALLING_OLLAMA, STARTING_SERVER, DOWNLOADING_MODEL, READY, ERROR
    }

    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ai.ollama.model:llama3.2:3b}")
    private String model;

    @Value("${ai.ollama.executable:}")
    private String ollamaExecutable;

    @Value("${ai.ollama.auto-install:true}")
    private boolean autoInstall;

    @Value("${ai.ollama.allow-auto-install:false}")
    private boolean allowAutoInstall;

    @Value("${ai.ollama.auto-bootstrap:true}")
    private boolean autoBootstrap;

    @Value("${ai.ollama.docker.enabled:true}")
    private boolean dockerEnabled;

    @Value("${ai.ollama.docker.auto-start:true}")
    private boolean dockerAutoStart;

    @Value("${ai.ollama.docker.container-name:beacon-ollama}")
    private String dockerContainerName;

    @Value("${ai.ollama.docker.image:ollama/ollama:latest}")
    private String dockerImage;

    @Value("${ai.ollama.docker.volume:}")
    private String dockerVolume;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        // 모델 풀/생성에 시간이 걸릴 수 있어 길게 잡는다.
        rf.setReadTimeout((int) Duration.ofMinutes(10).toMillis());
        return new RestTemplate(rf);
    }

    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.NOT_STARTED);
    private final AtomicReference<String> message = new AtomicReference<>("아직 시작되지 않음");
    private volatile boolean bootstrapInFlight = false;
    private volatile boolean privilegedBootstrapInFlight = false;

    @PostConstruct
    public void init() {
        if (autoBootstrap) {
            triggerBootstrap();
        } else {
            phase.set(Phase.NOT_STARTED);
            message.set("자동 부트스트랩 비활성. /api/ai/bootstrap 으로 수동 시작 가능");
        }
    }

    /** 외부(컨트롤러)에서도 재시도/수동 시작이 가능하도록 공개 트리거. */
    public synchronized void triggerBootstrap() {
        triggerBootstrap(false);
    }

    /** 관리자 요청 여부에 따라 자동 설치 허용 정책을 분기한다. */
    public synchronized void triggerBootstrap(boolean allowPrivilegedAutoInstall) {
        if (bootstrapInFlight) {
            log.info("Ollama bootstrap already in progress");
            return;
        }
        bootstrapInFlight = true;
        privilegedBootstrapInFlight = allowPrivilegedAutoInstall;
        Thread t = new Thread(() -> ensureReady(allowPrivilegedAutoInstall), "ollama-bootstrap");
        t.setDaemon(true);
        t.start();
    }

    private void ensureReady(boolean allowPrivilegedAutoInstall) {
        try {
            phase.set(Phase.CHECKING);
            message.set("Ollama 데몬 상태 확인 중...");

            if (!isServerRunning()) {
                boolean startedByDocker = false;
                if (dockerEnabled && dockerAutoStart) {
                    phase.set(Phase.STARTING_SERVER);
                    message.set("Docker Ollama 컨테이너 확인/시작 중...");
                    startedByDocker = ensureDockerServerRunning();
                }

                if (!startedByDocker) {
                    if (!isOllamaInstalled()) {
                        boolean canAutoInstall = (autoInstall && allowAutoInstall) || allowPrivilegedAutoInstall;
                        if (!canAutoInstall) {
                            fail("Ollama 미설치 상태입니다. Docker 미사용 또는 실행 실패 상태입니다. 수동 설치 또는 Docker 상태를 확인하세요.");
                            return;
                        }
                        phase.set(Phase.INSTALLING_OLLAMA);
                        message.set("Ollama 자동 설치 중...");
                        if (allowPrivilegedAutoInstall && (!autoInstall || !allowAutoInstall)) {
                            log.warn("Auto-install temporarily enabled by admin request.");
                        } else {
                            log.warn("Auto-install is enabled. Installing Ollama automatically.");
                        }
                        installOllama();
                    }

                    phase.set(Phase.STARTING_SERVER);
                    message.set("Ollama 데몬 시작 중...");
                    startOllamaServer();
                }

                if (!waitForServer(60)) {
                    fail("Ollama 데몬이 60초 내 응답하지 않음");
                    return;
                }
            }

            if (!isModelAvailable()) {
                phase.set(Phase.DOWNLOADING_MODEL);
                message.set(model + " 모델 다운로드 중 (최초 1회, 약 2GB)...");
                log.info("Pulling model {}", model);
                pullModel();
            }

            phase.set(Phase.READY);
            message.set(model + " 모델 준비 완료");
            log.info("Ollama service ready (model={})", model);
        } catch (Exception e) {
            log.error("Ollama bootstrap failed", e);
            fail("초기화 실패: " + e.getMessage());
        } finally {
            bootstrapInFlight = false;
            privilegedBootstrapInFlight = false;
        }
    }

    private void fail(String msg) {
        phase.set(Phase.ERROR);
        message.set(msg);
    }

    public boolean isReady() {
        return phase.get() == Phase.READY;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("phase", phase.get().name());
        map.put("message", message.get());
        map.put("model", model);
        map.put("ready", isReady());
        map.put("inProgress", bootstrapInFlight);
        map.put("privilegedBootstrapInFlight", bootstrapInFlight && privilegedBootstrapInFlight);
        return map;
    }

    // ─────────────────────────────────────────────────────────────
    //  서버/모델 상태 확인
    // ─────────────────────────────────────────────────────────────

    public boolean isServerRunning() {
        try {
            ResponseEntity<String> r = restTemplate.getForEntity(URI.create(baseUrl + "/api/tags"), String.class);
            return r.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public boolean isModelAvailable() {
        try {
            ResponseEntity<Map> r = restTemplate.getForEntity(URI.create(baseUrl + "/api/tags"), Map.class);
            Map<String, Object> body = r.getBody();
            if (body == null) return false;
            List<Map<String, Object>> models = (List<Map<String, Object>>) body.get("models");
            if (models == null) return false;

            String wanted = model.toLowerCase();
            String wantedBase = wanted.contains(":") ? wanted.substring(0, wanted.indexOf(":")) : wanted;

            return models.stream().anyMatch(m -> {
                String name = String.valueOf(m.getOrDefault("name", "")).toLowerCase();
                return name.equals(wanted) || name.startsWith(wantedBase + ":");
            });
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitForServer(int timeoutSec) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (timeoutSec * 1000L);
        while (System.currentTimeMillis() < deadline) {
            if (isServerRunning()) return true;
            Thread.sleep(2000);
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────
    //  설치 / 시작
    // ─────────────────────────────────────────────────────────────

    private boolean isOllamaInstalled() {
        String command = resolveOllamaCommand();
        if (command == null || command.isBlank()) {
            return false;
        }
        if (looksLikePath(command)) {
            return Files.exists(Path.of(command));
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(command, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            consumeAsync(p);
            return p.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void installOllama() throws Exception {
        if (isWindows()) {
            // 1) winget 시도 (Windows 10 1809+/11 기본 탑재)
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "winget", "install", "-e", "--id", "Ollama.Ollama",
                        "--accept-source-agreements", "--accept-package-agreements", "--silent"
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                consumeAsync(p);
                int code = p.waitFor();
                if (code == 0) {
                    log.info("Ollama installed via winget");
                    return;
                }
                log.warn("winget exited with code {}, falling back to direct download", code);
            } catch (Exception e) {
                log.warn("winget unavailable: {}", e.getMessage());
            }

            // 2) 공식 설치 파일 직접 다운로드 후 사일런트 설치 (Inno Setup 기반)
            Path installer = Files.createTempFile("OllamaSetup-", ".exe");
            log.info("Downloading Ollama installer to {}", installer);
            URL url = URI.create("https://ollama.com/download/OllamaSetup.exe").toURL();
            try (InputStream in = url.openStream()) {
                Files.copy(in, installer, StandardCopyOption.REPLACE_EXISTING);
            }
            ProcessBuilder pb = new ProcessBuilder(
                    installer.toString(), "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            consumeAsync(p);
            int code = p.waitFor();
            log.info("Ollama installer exit code: {}", code);
        } else {
            // Linux / macOS 공식 설치 스크립트
            ProcessBuilder pb = new ProcessBuilder(
                    "sh", "-c", "curl -fsSL https://ollama.com/install.sh | sh"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            consumeAsync(p);
            p.waitFor();
        }
    }

    private void startOllamaServer() {
        // 이미 트레이/서비스로 실행 중이면 다시 띄울 필요 없음
        if (isServerRunning()) return;

        // 1) Windows: 설치 시 등록되는 트레이 앱 실행 시도
        if (isWindows()) {
            String[] candidates = {
                    System.getenv("LOCALAPPDATA") + "\\Programs\\Ollama\\ollama app.exe",
                    System.getenv("LOCALAPPDATA") + "\\Programs\\Ollama\\Ollama.exe",
                    "C:\\Program Files\\Ollama\\ollama app.exe"
            };
            for (String path : candidates) {
                try {
                    if (path == null) continue;
                    Path p = Path.of(path);
                    if (Files.exists(p)) {
                        new ProcessBuilder(p.toString())
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                .redirectError(ProcessBuilder.Redirect.DISCARD)
                                .start();
                        log.info("Started Ollama tray app: {}", p);
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // 2) PATH에 있는 ollama serve 실행
        try {
            ProcessBuilder pb = new ProcessBuilder(resolveOllamaCommand(), "serve");
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.start();
            log.info("Started 'ollama serve'");
        } catch (Exception e) {
            log.warn("Failed to start ollama serve directly: {}", e.getMessage());
        }
    }

    private void pullModel() throws Exception {
        // HTTP API 우선 사용
        try {
            Map<String, Object> body = Map.of("name", model, "stream", false);
            restTemplate.postForEntity(URI.create(baseUrl + "/api/pull"), body, Map.class);
            log.info("Model {} pulled via HTTP API", model);
            return;
        } catch (Exception e) {
            log.warn("HTTP pull failed ({}). Falling back to CLI 'ollama pull'.", e.getMessage());
        }

        if (tryPullModelViaDocker()) {
            log.info("Model {} pulled via docker exec", model);
            return;
        }

        // CLI 폴백
        ProcessBuilder pb = new ProcessBuilder(resolveOllamaCommand(), "pull", model);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        consumeAsync(p);
        int code = p.waitFor();
        if (code != 0) {
            throw new RuntimeException("ollama pull failed with code " + code);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  채팅 호출
    // ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public String chat(List<Map<String, String>> messages) {
        if (!isReady()) {
            return "[AI 미준비] " + message.get();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("options", Map.of(
                "temperature", 0.4,
                "num_ctx", 4096
        ));

        try {
            ResponseEntity<Map> r = restTemplate.postForEntity(URI.create(baseUrl + "/api/chat"), body, Map.class);
            Map<String, Object> data = r.getBody();
            if (data == null) return "(빈 응답)";
            Object msg = data.get("message");
            if (msg instanceof Map<?, ?> mm) {
                Object content = mm.get("content");
                return content != null ? content.toString() : "(content 없음)";
            }
            return "(message 필드 없음)";
        } catch (Exception e) {
            log.error("Ollama chat call failed", e);
            return "AI 호출 실패: " + e.getMessage();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  유틸
    // ─────────────────────────────────────────────────────────────

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private boolean ensureDockerServerRunning() {
        if (!isDockerAvailable()) {
            log.info("Docker command is unavailable. Falling back to local Ollama runtime.");
            return false;
        }
        try {
            if (isDockerContainerRunning()) {
                return true;
            }
            if (isDockerContainerExists()) {
                int code = runCommand(List.of("docker", "start", dockerContainerName));
                if (code != 0) {
                    log.warn("Failed to start existing docker container '{}'", dockerContainerName);
                    return false;
                }
                return true;
            }

            List<String> runCommand = new ArrayList<>(List.of(
                    "docker", "run", "-d",
                    "--name", dockerContainerName,
                    "-p", "11434:11434"
            ));
            String volume = resolveDockerVolume();
            if (volume != null && !volume.isBlank()) {
                runCommand.add("-v");
                runCommand.add(volume);
            }
            runCommand.add(dockerImage);

            int code = runCommand(runCommand);
            if (code != 0) {
                log.warn("Failed to run docker container '{}'", dockerContainerName);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Failed to ensure docker Ollama container: {}", e.getMessage());
            return false;
        }
    }

    private boolean isDockerAvailable() {
        try {
            return runCommand(List.of("docker", "--version")) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDockerContainerRunning() {
        try {
            String out = runCommandAndCapture(List.of(
                    "docker", "ps",
                    "--filter", "name=^/" + dockerContainerName + "$",
                    "--format", "{{.Names}}"
            ));
            return out.lines().anyMatch(line -> line.trim().equals(dockerContainerName));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isDockerContainerExists() {
        try {
            String out = runCommandAndCapture(List.of(
                    "docker", "ps", "-a",
                    "--filter", "name=^/" + dockerContainerName + "$",
                    "--format", "{{.Names}}"
            ));
            return out.lines().anyMatch(line -> line.trim().equals(dockerContainerName));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean tryPullModelViaDocker() {
        if (!dockerEnabled || !isDockerAvailable() || !isDockerContainerRunning()) {
            return false;
        }
        try {
            int code = runCommand(List.of(
                    "docker", "exec", dockerContainerName,
                    "ollama", "pull", model
            ));
            return code == 0;
        } catch (Exception e) {
            log.warn("docker exec ollama pull failed: {}", e.getMessage());
            return false;
        }
    }

    private String resolveDockerVolume() {
        if (dockerVolume != null && !dockerVolume.isBlank()) {
            return dockerVolume.trim();
        }
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return null;
        }
        String normalized = userHome.replace("\\", "/");
        return normalized + "/.ollama:/root/.ollama";
    }

    private int runCommand(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        consumeAsync(p);
        return p.waitFor();
    }

    private String runCommandAndCapture(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        int code = p.waitFor();
        if (code != 0) {
            return "";
        }
        return sb.toString().trim();
    }

    private String resolveOllamaCommand() {
        if (ollamaExecutable != null && !ollamaExecutable.isBlank()) {
            return ollamaExecutable.trim();
        }
        if (!isWindows()) {
            return "ollama";
        }

        String userHome = System.getProperty("user.home");
        String localAppData = System.getenv("LOCALAPPDATA");
        String[] candidates = {
                localAppData != null ? localAppData + "\\Programs\\Ollama\\ollama.exe" : null,
                userHome != null ? userHome + "\\AppData\\Local\\Programs\\Ollama\\ollama.exe" : null,
                "C:\\Program Files\\Ollama\\ollama.exe",
                "ollama"
        };

        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if ("ollama".equals(candidate)) {
                return candidate;
            }
            try {
                if (Files.exists(Path.of(candidate))) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return "ollama";
    }

    private boolean looksLikePath(String command) {
        return command.contains("\\") || command.contains("/") || command.endsWith(".exe");
    }

    private void consumeAsync(Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.debug("[ollama] {}", line);
                }
            } catch (IOException ignored) {
            }
        }, "ollama-stdout");
        t.setDaemon(true);
        t.start();
    }
}
