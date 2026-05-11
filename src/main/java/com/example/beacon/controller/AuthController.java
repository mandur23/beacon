package com.example.beacon.controller;

import com.example.beacon.dto.AuthResponse;
import com.example.beacon.dto.LoginRequest;
import com.example.beacon.dto.MfaSetupResponse;
import com.example.beacon.dto.MfaVerifyRequest;
import com.example.beacon.entity.User;
import com.example.beacon.repository.UserRepository;
import com.example.beacon.security.JwtTokenProvider;
import com.example.beacon.service.TotpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;

    private static final int MAX_LOGIN_ATTEMPTS = 5;

    // ── 로그인 ────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        User user = userRepository.findByUsername(loginRequest.getUsername()).orElse(null);

        if (user != null) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                log.warn("로그인 시도: 계정 잠금 상태 - username={}", loginRequest.getUsername());
                return ResponseEntity.status(423).body(Map.of("message", "계정이 잠겨있습니다. 잠시 후 다시 시도해주세요."));
            }
        }

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            );

            user = userRepository.findByUsername(loginRequest.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setLastLoginAt(LocalDateTime.now());
            user.setLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);

            // MFA 활성화된 계정이면 임시 토큰 반환 (202 Accepted)
            if (Boolean.TRUE.equals(user.getMfaEnabled())) {
                String tempToken = tokenProvider.generateTempToken(user.getUsername());
                log.info("MFA 인증 대기 - username={}", user.getUsername());
                return ResponseEntity.status(202).body(Map.of(
                        "mfaRequired", true,
                        "tempToken", tempToken
                ));
            }

            String token = tokenProvider.generateToken(user.getUsername(), user.getId());
            log.info("로그인 성공 - username={}", user.getUsername());
            return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getId(), user.getRole()));

        } catch (DisabledException e) {
            log.warn("로그인 실패: 비활성화된 계정 - username={}", loginRequest.getUsername());
            return ResponseEntity.status(403).body(Map.of("message", "계정이 비활성화되었습니다. 관리자에게 문의하세요."));

        } catch (LockedException e) {
            log.warn("로그인 실패: 잠긴 계정 - username={}", loginRequest.getUsername());
            return ResponseEntity.status(423).body(Map.of("message", "계정이 잠겨있습니다. 잠시 후 다시 시도해주세요."));

        } catch (BadCredentialsException e) {
            log.warn("로그인 실패: 잘못된 자격 증명 - username={}", loginRequest.getUsername());
            if (user != null) {
                int attempts = (user.getLoginAttempts() == null ? 0 : user.getLoginAttempts()) + 1;
                user.setLoginAttempts(attempts);
                if (attempts >= MAX_LOGIN_ATTEMPTS) {
                    user.setEnabled(false);
                    user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                    log.warn("계정 잠금 처리 - username={}, attempts={}", user.getUsername(), attempts);
                }
                userRepository.save(user);
            }
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));

        } catch (Exception e) {
            log.error("로그인 중 예상치 못한 오류 발생 - username={}", loginRequest.getUsername(), e);
            return ResponseEntity.status(500).body(Map.of("message", "로그인 처리 중 오류가 발생했습니다."));
        }
    }

    // ── MFA: OTP 검증 (임시 토큰 → 정식 JWT 발급) ──────────────────────────

    @PostMapping("/mfa/verify")
    public ResponseEntity<?> mfaVerify(@RequestBody MfaVerifyRequest request) {
        String tempToken = request.getTempToken();

        if (!tokenProvider.validateToken(tempToken) || !tokenProvider.isTempToken(tempToken)) {
            return ResponseEntity.status(401).body(Map.of("message", "유효하지 않거나 만료된 인증 토큰입니다."));
        }

        String username = tokenProvider.getUsernameFromTempToken(tempToken);
        User user = userRepository.findByUsername(username).orElse(null);

        if (user == null || user.getMfaSecret() == null) {
            return ResponseEntity.status(400).body(Map.of("message", "MFA가 설정되지 않은 계정입니다."));
        }

        if (!totpService.verifyTotp(user.getMfaSecret(), request.getOtpCode())) {
            log.warn("MFA 검증 실패 - username={}", username);
            return ResponseEntity.status(401).body(Map.of("message", "OTP 코드가 올바르지 않습니다."));
        }

        String token = tokenProvider.generateToken(user.getUsername(), user.getId());
        log.info("MFA 로그인 성공 - username={}", username);
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getId(), user.getRole()));
    }

    // ── MFA: 설정 시작 (secret 생성, QR URI 반환) ───────────────────────────

    @PostMapping("/mfa/setup")
    public ResponseEntity<?> mfaSetup(@RequestHeader("Authorization") String authHeader) {
        String username = extractUsername(authHeader);
        if (username == null) return ResponseEntity.status(401).body(Map.of("message", "인증이 필요합니다."));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String secret = totpService.generateSecret();
        user.setMfaSecret(secret);
        userRepository.save(user);

        String uri = totpService.generateOtpAuthUri(username, secret);
        log.info("MFA 설정 시작 - username={}", username);
        return ResponseEntity.ok(new MfaSetupResponse(uri, secret));
    }

    // ── MFA: 활성화 (OTP 코드 확인 후 mfaEnabled = true) ───────────────────

    @PostMapping("/mfa/enable")
    public ResponseEntity<?> mfaEnable(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        String username = extractUsername(authHeader);
        if (username == null) return ResponseEntity.status(401).body(Map.of("message", "인증이 필요합니다."));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getMfaSecret() == null) {
            return ResponseEntity.status(400).body(Map.of("message", "먼저 /mfa/setup을 호출하세요."));
        }

        if (!totpService.verifyTotp(user.getMfaSecret(), body.get("otpCode"))) {
            log.warn("MFA 활성화 실패: OTP 불일치 - username={}", username);
            return ResponseEntity.status(401).body(Map.of("message", "OTP 코드가 올바르지 않습니다."));
        }

        user.setMfaEnabled(true);
        userRepository.save(user);
        log.info("MFA 활성화 완료 - username={}", username);
        return ResponseEntity.ok(Map.of("message", "MFA가 활성화되었습니다."));
    }

    // ── MFA: 비활성화 ────────────────────────────────────────────────────────

    @PostMapping("/mfa/disable")
    public ResponseEntity<?> mfaDisable(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        String username = extractUsername(authHeader);
        if (username == null) return ResponseEntity.status(401).body(Map.of("message", "인증이 필요합니다."));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!totpService.verifyTotp(user.getMfaSecret(), body.get("otpCode"))) {
            log.warn("MFA 비활성화 실패: OTP 불일치 - username={}", username);
            return ResponseEntity.status(401).body(Map.of("message", "OTP 코드가 올바르지 않습니다."));
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);
        log.info("MFA 비활성화 완료 - username={}", username);
        return ResponseEntity.ok(Map.of("message", "MFA가 비활성화되었습니다."));
    }

    // ── 회원가입 ──────────────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User userRequest) {
        try {
            if (userRepository.existsByUsername(userRequest.getUsername())) {
                log.warn("회원가입 실패: 이미 존재하는 사용자명 - username={}", userRequest.getUsername());
                return ResponseEntity.badRequest().body(Map.of("message", "Username already exists"));
            }
            if (userRepository.existsByEmail(userRequest.getEmail())) {
                log.warn("회원가입 실패: 이미 존재하는 이메일 - email={}", userRequest.getEmail());
                return ResponseEntity.badRequest().body(Map.of("message", "Email already exists"));
            }

            User user = User.builder()
                    .username(userRequest.getUsername())
                    .password(passwordEncoder.encode(userRequest.getPassword()))
                    .email(userRequest.getEmail())
                    .name(userRequest.getName())
                    .role("USER")
                    .enabled(true)
                    .mfaEnabled(false)
                    .loginAttempts(0)
                    .build();

            userRepository.save(user);
            log.info("회원가입 성공 - username={}, email={}", userRequest.getUsername(), userRequest.getEmail());
            return ResponseEntity.ok(Map.of("message", "User registered successfully"));

        } catch (Exception e) {
            log.error("회원가입 중 오류 발생 - username={}", userRequest.getUsername(), e);
            return ResponseEntity.status(500).body(Map.of("message", "회원가입 처리 중 오류가 발생했습니다."));
        }
    }

    // ── 토큰 검증 ────────────────────────────────────────────────────────────

    @GetMapping("/verify")
    public ResponseEntity<?> verifyToken(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of("valid", false, "message", "Invalid authorization header format"));
            }
            String token = authHeader.substring(7);
            if (tokenProvider.validateToken(token) && !tokenProvider.isTempToken(token)) {
                String username = tokenProvider.getUsernameFromToken(token);
                Long userId = tokenProvider.getUserIdFromToken(token);
                return ResponseEntity.ok(Map.of("valid", true, "username", username, "userId", userId));
            }
            return ResponseEntity.status(401).body(Map.of("valid", false, "message", "Invalid token"));

        } catch (Exception e) {
            log.error("토큰 검증 중 예상치 못한 오류 발생", e);
            return ResponseEntity.status(401).body(Map.of("valid", false, "message", "Token verification failed"));
        }
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private String extractUsername(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7);
        if (!tokenProvider.validateToken(token) || tokenProvider.isTempToken(token)) return null;
        return tokenProvider.getUsernameFromToken(token);
    }
}
