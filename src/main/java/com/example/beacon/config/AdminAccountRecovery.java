package com.example.beacon.config;

import com.example.beacon.entity.User;
import com.example.beacon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountRecovery implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
        userRepository.findByUsername("admin").ifPresentOrElse(user -> {
            if (!user.getEnabled()) {
                log.warn("[RECOVERY] admin 계정이 비활성화되어 있습니다. 강제 활성화를 시작합니다.");
                user.setEnabled(true);
                user.setLockedUntil(null); // 혹시 잠겨있을 경우 잠금 해제
                user.setLoginAttempts(0);   // 시도 횟수 초기화
                userRepository.save(user);
                log.info("[RECOVERY] admin 계정이 성공적으로 복구 및 활성화되었습니다.");
            } else {
                log.info("[RECOVERY] admin 계정이 이미 활성 상태입니다.");
            }
        }, () -> log.error("[RECOVERY] admin 계정을 찾을 수 없습니다. DB 초기화 상태를 확인하세요."));
    }
}
