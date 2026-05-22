package com.example.beacon;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import com.example.beacon.config.BeaconFirewallProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(BeaconFirewallProperties.class)
public class BeaconApplication {
    private static final String HTTPS_PREPARED_FLAG = "beacon.https.prepared";
    private static final String DEFAULT_LOCAL_KEYSTORE = "classpath:keystore.p12";

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

        if (!shouldPrepareLocalKeystore()) {
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
        boolean prepared = false;
        try {
            if (mkcert != null) {
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
                prepared = true;
                System.out.println("[HTTPS] mkcert로 로컬 keystore 준비 완료: " + keystorePath);
            } else {
                System.err.println("[HTTPS] mkcert를 찾지 못했습니다. keytool fallback을 시도합니다.");
            }
        } catch (Exception e) {
            System.err.println("[HTTPS] mkcert 자동 준비 실패: " + e.getMessage());
        }

        if (!prepared) {
            try {
                generateKeystoreWithKeytool(keystorePath);
                prepared = true;
                System.out.println("[HTTPS] keytool fallback으로 로컬 keystore 준비 완료: " + keystorePath);
            } catch (Exception keytoolError) {
                System.err.println("[HTTPS] keytool fallback 실패: " + keytoolError.getMessage());
            }
        }

        if (prepared) {
            System.setProperty(HTTPS_PREPARED_FLAG, "true");
        }

        if (Files.exists(keystorePath) && shouldUseDefaultLocalKeystore()) {
            // processResources는 main() 이전에 끝나므로 런타임에 만든 keystore는 classpath에 없다.
            System.setProperty("server.ssl.key-store", keystorePath.toAbsolutePath().toUri().toString());
            try {
                syncKeystoreToBuildOutput(keystorePath);
            } catch (IOException e) {
                System.err.println("[HTTPS] build 출력으로 keystore 복사 실패(무시 가능): " + e.getMessage());
            }
        }
    }

    private static void syncKeystoreToBuildOutput(Path keystorePath) throws IOException {
        Path buildKeystore = Paths.get("build", "resources", "main", "keystore.p12");
        Files.createDirectories(buildKeystore.getParent());
        Files.copy(keystorePath, buildKeystore, StandardCopyOption.REPLACE_EXISTING);
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

    private static boolean shouldPrepareLocalKeystore() {
        // 운영에서 PEM 인증서가 지정되면 로컬 개발용 keystore 자동 생성을 건너뛴다.
        if (!readConfigValue("SERVER_SSL_CERTIFICATE").isBlank()
                || !readConfigValue("SERVER_SSL_CERTIFICATE_PRIVATE_KEY").isBlank()) {
            return false;
        }
        // 운영에서 외부 PKCS12 경로를 지정하면 로컬 keystore 자동 생성을 건너뛴다.
        return shouldUseDefaultLocalKeystore();
    }

    private static boolean shouldUseDefaultLocalKeystore() {
        String configuredKeystore = readConfigValue("SERVER_SSL_KEYSTORE_PATH");
        if (configuredKeystore.isBlank()) {
            return true;
        }
        return DEFAULT_LOCAL_KEYSTORE.equalsIgnoreCase(configuredKeystore);
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

    private static void generateKeystoreWithKeytool(Path keystorePath) throws IOException, InterruptedException {
        String keytool = resolveKeytoolCommand();
        if (keytool == null) {
            throw new IllegalStateException("keytool을 찾지 못했습니다. JDK가 설치되어 있는지 확인하세요.");
        }
        String password = readConfigValue("SERVER_SSL_KEYSTORE_PASSWORD");
        if (password.isBlank()) {
            password = "changeit";
        }
        String alias = readConfigValue("SERVER_SSL_KEY_ALIAS");
        if (alias.isBlank()) {
            alias = "1";
        }

        if (Files.exists(keystorePath)) {
            if (isKeyAliasPresent(keytool, keystorePath, password, alias)) {
                System.out.println("[HTTPS] 기존 keystore 재사용: " + keystorePath + " (alias=" + alias + ")");
                return;
            }
            Path backupPath = keystorePath.resolveSibling(keystorePath.getFileName() + ".bak");
            Files.move(keystorePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[HTTPS] 기존 keystore를 백업했습니다: " + backupPath);
        }

        runCommand(
                keytool,
                "-genkeypair",
                "-alias",
                alias,
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-storetype",
                "PKCS12",
                "-keystore",
                keystorePath.toString(),
                "-storepass",
                password,
                "-keypass",
                password,
                "-validity",
                "3650",
                "-dname",
                "CN=localhost, OU=Dev, O=Beacon, L=Seoul, ST=Seoul, C=KR",
                "-ext",
                "SAN=dns:localhost,ip:127.0.0.1,ip:0:0:0:0:0:0:0:1",
                "-noprompt"
        );
    }

    private static boolean isKeyAliasPresent(String keytool, Path keystorePath, String password, String alias) {
        try {
            Process process = new ProcessBuilder(
                    keytool,
                    "-list",
                    "-storetype",
                    "PKCS12",
                    "-keystore",
                    keystorePath.toString(),
                    "-storepass",
                    password,
                    "-alias",
                    alias
            ).redirectErrorStream(true).start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return true;
            }
            System.err.println("[HTTPS] 기존 keystore alias 검사 실패: " + output.toString().trim());
            return false;
        } catch (Exception e) {
            System.err.println("[HTTPS] 기존 keystore 검사 중 오류: " + e.getMessage());
            return false;
        }
    }

    private static String resolveKeytoolCommand() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            Path keytoolPath = Paths.get(javaHome, "bin", isWindows() ? "keytool.exe" : "keytool");
            if (Files.exists(keytoolPath)) {
                return keytoolPath.toString();
            }
        }
        return isWindows() ? "keytool.exe" : "keytool";
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