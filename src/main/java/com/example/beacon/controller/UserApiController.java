package com.example.beacon.controller;

import com.example.beacon.entity.User;
import com.example.beacon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApiController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/{id}/lock")
    public ResponseEntity<Map<String, Object>> lockUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
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
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        String newRole = body.get("role");
        if (newRole == null || newRole.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "역할을 선택해주세요"));
        }
        user.setRole(newRole);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "'" + user.getName() + "' 역할이 변경되었습니다"));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> addUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String name = body.get("name");
        String email = body.get("email");
        String role = body.getOrDefault("role", "ROLE_ANALYST");
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
}
