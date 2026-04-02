package com.example.beacon;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BeaconApplication {

    public static void main(String[] args) {
        loadDotenvIntoSystemProperties();
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
}