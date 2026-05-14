package com.example.beacon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

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
    private static final int MAX_PROGRESS_LOGS = 40;
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern MODEL_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.:-]{2,64}$");
    private static final List<Map<String, Object>> FREE_MODEL_CATALOG = List.of(
            modelOption("llama3.2:3b", "Llama 3.2 3B", "~2GB", "균형형 기본 모델 (한국어/영어, 보고서·요약에 적합)"),
            modelOption("qwen2.5:3b", "Qwen 2.5 3B", "~2GB", "가벼운 추론/요약에 강한 모델"),
            modelOption("phi3:mini", "Phi-3 Mini", "~2.2GB", "빠른 응답, 경량 환경에 적합"),
            modelOption("mistral:7b-instruct", "Mistral 7B Instruct", "~4.1GB", "정확도 우선, 여유 메모리 환경에 적합"),
            modelOption("gemma2:2b", "Gemma 2 2B", "~1.6GB", "저사양 장비에서 구동하기 쉬운 초경량 모델")
    );
    private static final Set<String> FREE_MODEL_TAGS = FREE_MODEL_CATALOG.stream()
            .map(m -> String.valueOf(m.get("tag")))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

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
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        // 모델 풀/생성에 시간이 걸릴 수 있어 길게 잡는다.
        rf.setReadTimeout((int) Duration.ofMinutes(10).toMillis());
        return new RestTemplate(rf);
    }

    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.NOT_STARTED);
    private final AtomicReference<String> message = new AtomicReference<>("아직 시작되지 않음");
    private final AtomicReference<Integer> downloadPercent = new AtomicReference<>(null);
    private final AtomicReference<String> downloadStep = new AtomicReference<>("");
    private final List<String> progressLogs = Collections.synchronizedList(new ArrayList<>());
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
        resetDownloadProgress();
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
            downloadPercent.set(100);
            downloadStep.set("모델 준비 완료");
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
        appendProgressLog("오류: " + msg);
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
        map.put("downloadPercent", downloadPercent.get());
        map.put("downloadStep", downloadStep.get());
        map.put("progressLogs", snapshotProgressLogs());
        return map;
    }

    public List<Map<String, Object>> getFreeModelCatalog() {
        return FREE_MODEL_CATALOG;
    }

    public synchronized boolean selectModel(String requestedModel) {
        if (requestedModel == null) return false;
        String normalized = requestedModel.trim().toLowerCase();
        if (!MODEL_NAME_PATTERN.matcher(normalized).matches()) return false;
        if (!FREE_MODEL_TAGS.contains(normalized)) return false;
        if (bootstrapInFlight) return false;
        this.model = normalized;
        message.set(normalized + " 모델로 전환됨. 부트스트랩을 시작하세요.");
        resetDownloadProgress();
        appendProgressLog("모델 선택: " + normalized);
        return true;
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
        appendProgressLog("다운로드 시작: " + model);
        downloadPercent.set(0);
        downloadStep.set("모델 다운로드 요청 중");

        // HTTP API 우선 사용
        try {
            pullModelViaHttpStream();
            downloadPercent.set(100);
            downloadStep.set("다운로드 완료");
            appendProgressLog("다운로드 완료: " + model);
            log.info("Model {} pulled via HTTP stream API", model);
            return;
        } catch (Exception e) {
            log.warn("HTTP pull failed ({}). Falling back to CLI 'ollama pull'.", e.getMessage());
            appendProgressLog("HTTP 스트림 실패, CLI 방식으로 전환: " + e.getMessage());
        }

        if (tryPullModelViaDocker()) {
            log.info("Model {} pulled via docker exec", model);
            downloadPercent.set(100);
            downloadStep.set("다운로드 완료");
            appendProgressLog("다운로드 완료(docker): " + model);
            return;
        }

        // CLI 폴백
        ProcessBuilder pb = new ProcessBuilder(resolveOllamaCommand(), "pull", model);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        consumeAsyncWithProgress(p, "ollama");
        int code = p.waitFor();
        if (code != 0) {
            throw new RuntimeException("ollama pull failed with code " + code);
        }
        downloadPercent.set(100);
        downloadStep.set("다운로드 완료");
    }

    @SuppressWarnings("unchecked")
    private void pullModelViaHttpStream() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/api/pull").toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        conn.setReadTimeout((int) Duration.ofMinutes(20).toMillis());
        conn.setDoOutput(true);

        String payload = objectMapper.writeValueAsString(Map.of("name", model, "stream", true));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = conn.getResponseCode();
        InputStream responseStream = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (responseStream == null) {
            throw new IOException("ollama /api/pull 응답이 비어 있습니다 (HTTP " + statusCode + ")");
        }

        if (statusCode < 200 || statusCode >= 300) {
            String errorBody = new String(responseStream.readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("ollama /api/pull 실패 (HTTP " + statusCode + "): " + errorBody);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                Map<String, Object> event = objectMapper.readValue(trimmed, Map.class);
                handlePullProgressEvent(event);
            }
        } finally {
            conn.disconnect();
        }
    }

    private void handlePullProgressEvent(Map<String, Object> event) {
        Object error = event.get("error");
        if (error != null) {
            throw new RuntimeException("모델 다운로드 오류: " + error);
        }

        String status = String.valueOf(event.getOrDefault("status", "진행 중"));
        Long completed = toLong(event.get("completed"));
        Long total = toLong(event.get("total"));

        if (total != null && total > 0 && completed != null && completed >= 0) {
            int percent = (int) Math.min(99, (completed * 100) / total);
            downloadPercent.set(percent);
            downloadStep.set(status + " (" + percent + "%)");
            appendProgressLog(status + " - " + humanBytes(completed) + " / " + humanBytes(total) + " (" + percent + "%)");
        } else {
            downloadStep.set(status);
            appendProgressLog(status);
        }

        if ("success".equalsIgnoreCase(status) || status.toLowerCase().contains("done")) {
            downloadPercent.set(100);
            downloadStep.set("다운로드 완료");
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
        // 기본값: 프로젝트 내부 .runtime/ollama 디렉터리를 모델 저장소로 사용한다.
        // (요구사항: 서버 내부 실행 시 다운로드 위치를 프로젝트 안으로 고정)
        String projectDir = System.getProperty("user.dir");
        if (projectDir != null && !projectDir.isBlank()) {
            try {
                Path runtimeDir = Path.of(projectDir, ".runtime", "ollama").toAbsolutePath().normalize();
                Files.createDirectories(runtimeDir);
                String normalized = runtimeDir.toString().replace("\\", "/");
                return normalized + ":/root/.ollama";
            } catch (Exception e) {
                log.warn("Failed to prepare project-local Ollama volume. Falling back to user-home path: {}", e.getMessage());
            }
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

    private void consumeAsyncWithProgress(Process p, String prefix) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        appendProgressLog(prefix + ": " + trimmed);
                    }
                    log.debug("[{}] {}", prefix, line);
                }
            } catch (IOException ignored) {
            }
        }, "ollama-progress");
        t.setDaemon(true);
        t.start();
    }

    private void resetDownloadProgress() {
        downloadPercent.set(null);
        downloadStep.set("");
        synchronized (progressLogs) {
            progressLogs.clear();
        }
    }

    private void appendProgressLog(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        String timestamped = "[" + LocalTime.now().format(LOG_TIME_FORMATTER) + "] " + line;
        synchronized (progressLogs) {
            progressLogs.add(timestamped);
            if (progressLogs.size() > MAX_PROGRESS_LOGS) {
                progressLogs.remove(0);
            }
        }
    }

    private List<String> snapshotProgressLogs() {
        synchronized (progressLogs) {
            return new ArrayList<>(progressLogs);
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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

    private static Map<String, Object> modelOption(String tag, String name, String approxSize, String note) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tag", tag);
        option.put("name", name);
        option.put("approxSize", approxSize);
        option.put("note", note);
        return Collections.unmodifiableMap(option);
    }
}
