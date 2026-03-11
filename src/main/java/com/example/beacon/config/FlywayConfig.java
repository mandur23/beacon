package com.example.beacon.config;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 과거 마이그레이션 파일 변경으로 인해 flyway_schema_history의 체크섬이
 * 현재 파일과 불일치할 수 있다. repair()를 먼저 실행해 체크섬을 현재 파일
 * 기준으로 갱신한 뒤 migrate()를 수행한다.
 * repair()는 이미 적용된 마이그레이션을 재실행하지 않으므로 멱등성이 보장된다.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
