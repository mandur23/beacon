package com.example.beacon.controller;

import com.example.beacon.entity.User;
import com.example.beacon.exception.ResourceNotFoundException;
import com.example.beacon.repository.UserRepository;
import com.example.beacon.service.TotpService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserApiController {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "ADMIN", "ANALYST", "VIEWER", "OPERATOR", "USER"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;

    @PostMapping("/{id}/lock")
    public ResponseEntity<Map<String, Object>> lockUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        user.setEnabled(false);
        user.setLockedUntil(LocalDateTime.now().plusDays(1));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "'" + user.getName() + "' 계정이 잠금 처리되었습니다"));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<Map<String, Object>> unlockUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        user.setEnabled(true);
        user.setLockedUntil(null);
        user.setLoginAttempts(0);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "'" + user.getName() + "' 계정이 잠금 해제되었습니다"));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<Map<String, Object>> updateRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        String newRole = normalizeRole(body.get("role"));
        if (newRole == null || newRole.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "역할을 선택해주세요"));
        }
        if (!ALLOWED_ROLES.contains(newRole)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "허용되지 않은 역할입니다"));
        }
        user.setRole(newRole);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "'" + user.getName() + "' 역할이 변경되었습니다"));
    }

    @PostMapping("/{id}/mfa")
    public ResponseEntity<Map<String, Object>> updateMfa(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        Object enabledRaw = body.get("enabled");
        if (!(enabledRaw instanceof Boolean enabled)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "enabled 값(true/false)이 필요합니다"));
        }

        boolean secretIssued = false;
        String provisioningUri = null;
        if (enabled) {
            user.setMfaEnabled(true);
            // 계정에 시크릿이 없으면 관리자 설정 시점에 생성한다.
            if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
                user.setMfaSecret(totpService.generateSecret());
                secretIssued = true;
                provisioningUri = totpService.generateOtpAuthUri(user.getUsername(), user.getMfaSecret());
            }
        } else {
            user.setMfaEnabled(false);
            user.setMfaSecret(null);
        }

        userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "enabled", user.getMfaEnabled(),
                "secretIssued", secretIssued,
                "provisioningUri", provisioningUri == null ? "" : provisioningUri,
                "message", "'" + user.getName() + "' MFA 설정이 업데이트되었습니다"
        ));
    }

    @GetMapping(value = "/{id}/mfa/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getMfaQr(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        if (!Boolean.TRUE.equals(user.getMfaEnabled()) || user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            String otpAuthUri = totpService.generateOtpAuthUri(user.getUsername(), user.getMfaSecret());
            QRCodeWriter writer = new QRCodeWriter();
            var matrix = writer.encode(otpAuthUri, BarcodeFormat.QR_CODE, 280, 280);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                    .body(out.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> addUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String name = body.get("name");
        String email = body.get("email");
        String role = normalizeRole(body.getOrDefault("role", "ANALYST"));
        if (!ALLOWED_ROLES.contains(role)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "허용되지 않은 역할입니다"));
        }
        String rawPassword = body.getOrDefault("password", "Changeme123!");

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "사용자명을 입력해주세요"));
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "이미 사용 중인 사용자명입니다"));
        }
        if (email != null && !email.isBlank() && userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "이미 사용 중인 이메일입니다"));
        }

        User user = User.builder()
                .username(username)
                .name(name != null ? name : username)
                .email(email != null ? email : username + "@beacon.local")
                .role(role)
                .password(passwordEncoder.encode(rawPassword))
                .enabled(true)
                .mfaEnabled(false)
                .loginAttempts(0)
                .build();
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "'" + user.getName() + "' 사용자가 추가되었습니다"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        String currentUsername = authentication != null ? authentication.getName() : null;
        if (currentUsername != null && currentUsername.equals(user.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "현재 로그인한 계정은 삭제할 수 없습니다"));
        }

        userRepository.delete(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "'" + user.getName() + "' 계정이 삭제되었습니다"));
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        String normalized = role.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        return normalized;
    }
}
