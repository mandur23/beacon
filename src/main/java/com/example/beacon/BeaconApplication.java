package com.example.beacon;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import com.example.beacon.config.BeaconFirewallProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(BeaconFirewallProperties.class)
public class BeaconApplication {
    private static final String HTTPS_PREPARED_FLAG = "beacon.https.prepared";

    public static void main(String[] args) {
        loadDotenvIntoSystemProperties();
        prepareLocalHttpsKeystoreIfNeeded();
        SpringApplication.run(BeaconApplication.class, args);
    }

    /**
     * 프로젝트 루트의 .env 를 읽어 시스템 프로퍼티로 넣는다.
     * OS 환경 변수가 이미 있으면 덮어쓰지 않는다.
     * Spring Boot 는 기본적으로 .env 를 읽지 않으므로, 로컬에서만 이 방식으로 통일한다.
     */
    private static void loadDotenvIntoSystemProperties() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory(".")
                    .ignoreIfMissing()
                    .load();
            dotenv.entries().forEach(e -> {
                String key = e.getKey();
                if (System.getenv(key) != null) {
                    return;
                }
                if (System.getProperty(key) != null) {
                    return;
                }
                System.setProperty(key, e.getValue());
            });
        } catch (Exception ignored) {
            // .env 없거나 파싱 실패 시 application.yaml / 기본값만 사용
        }
    }

    /**
     * SERVER_SSL_ENABLED=true 인 경우, 로컬 개발용 keystore.p12를 자동 준비한다.
     * - mkcert -install
     * - mkcert -pkcs12 -p12-file src/main/resources/keystore.p12 localhost 127.0.0.1 ::1
     */
    private static void prepareLocalHttpsKeystoreIfNeeded() {
        if ("true".equalsIgnoreCase(System.getProperty(HTTPS_PREPARED_FLAG))) {
            return;
        }

        String sslEnabled = readConfigValue("SERVER_SSL_ENABLED");
        if (!"true".equalsIgnoreCase(sslEnabled)) {
            return;
        }

        Path keystorePath = Paths.get("src", "main", "resources", "keystore.p12");
        try {
            Files.createDirectories(keystorePath.getParent());
        } catch (IOException e) {
            System.err.println("[HTTPS] keystore 디렉토리 생성 실패: " + e.getMessage());
            return;
        }

        // mkcert 기본 PKCS12 값과 application.yaml 기본값을 맞춘다.
        setSystemPropertyIfAbsent("SERVER_SSL_KEYSTORE_PASSWORD", "changeit");
        setSystemPropertyIfAbsent("SERVER_SSL_KEY_ALIAS", "1");

        String mkcert = resolveMkcertCommand();
        if (mkcert == null) {
            System.err.println("[HTTPS] mkcert를 찾지 못했습니다. HTTPS 자동 준비를 건너뜁니다.");
            return;
        }

        try {
            runCommand(mkcert, "-install");
            runCommand(
                    mkcert,
                    "-pkcs12",
                    "-p12-file",
                    keystorePath.toString(),
                    "localhost",
                    "127.0.0.1",
                    "::1"
            );
            System.setProperty(HTTPS_PREPARED_FLAG, "true");
            System.out.println("[HTTPS] 로컬 keystore 준비 완료: " + keystorePath);
        } catch (Exception e) {
            System.err.println("[HTTPS] 자동 keystore 준비 실패: " + e.getMessage());
        }
    }

    private static String readConfigValue(String key) {
        String value = System.getProperty(key);
        if (value != null && !value.isBlank()) {
            return value;
        }
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return "";
    }

    private static void setSystemPropertyIfAbsent(String key, String defaultValue) {
        if (System.getProperty(key) != null || System.getenv(key) != null) {
            return;
        }
        System.setProperty(key, defaultValue);
    }

    private static String resolveMkcertCommand() {
        Path wingetPath = Paths.get(
                System.getProperty("user.home"),
                "AppData",
                "Local",
                "Microsoft",
                "WinGet",
                "Packages",
                "FiloSottile.mkcert_Microsoft.Winget.Source_8wekyb3d8bbwe",
                "mkcert.exe"
        );
        if (Files.exists(wingetPath)) {
            return wingetPath.toString();
        }
        return isWindows() ? "mkcert.exe" : "mkcert";
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.contains("win");
    }

    private static void runCommand(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .inheritIO()
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(String.join(" ", command) + " failed with exit code " + exitCode);
        }
    }
}