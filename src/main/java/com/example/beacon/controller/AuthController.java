package com.example.beacon.controller;

import com.example.beacon.dto.AuthResponse;
import com.example.beacon.dto.LoginRequest;
import com.example.beacon.entity.User;
import com.example.beacon.repository.UserRepository;
import com.example.beacon.security.JwtTokenProvider;
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
    
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        User user = userRepository.findByUsername(loginRequest.getUsername()).orElse(null);

        if (user != null) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                log.warn("로그인 시도: 계정 잠금 상태 - username={}", loginRequest.getUsername());
                Map<String, String> error = new HashMap<>();
                error.put("message", "계정이 잠겨있습니다. 잠시 후 다시 시도해주세요.");
                return ResponseEntity.status(423).body(error);
            }
        }

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
            );

            user = userRepository.findByUsername(loginRequest.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setLastLoginAt(LocalDateTime.now());
            user.setLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);

            String token = tokenProvider.generateToken(user.getUsername(), user.getId());

            AuthResponse response = new AuthResponse(
                token,
                user.getUsername(),
                user.getId(),
                user.getRole()
            );

            log.info("로그인 성공 - username={}", loginRequest.getUsername());
            return ResponseEntity.ok(response);
            
        } catch (DisabledException e) {
            log.warn("로그인 실패: 비활성화된 계정 - username={}", loginRequest.getUsername());
            Map<String, String> error = new HashMap<>();
            error.put("message", "계정이 비활성화되었습니다. 관리자에게 문의하세요.");
            return ResponseEntity.status(403).body(error);
            
        } catch (LockedException e) {
            log.warn("로그인 실패: 잠긴 계정 - username={}", loginRequest.getUsername());
            Map<String, String> error = new HashMap<>();
            error.put("message", "계정이 잠겨있습니다. 잠시 후 다시 시도해주세요.");
            return ResponseEntity.status(423).body(error);
            
        } catch (BadCredentialsException e) {
            log.warn("로그인 실패: 잘못된 자격 증명 - username={}", loginRequest.getUsername());
            if (user != null) {
                int attempts = (user.getLoginAttempts() == null ? 0 : user.getLoginAttempts()) + 1;
                user.setLoginAttempts(attempts);
                if (attempts >= MAX_LOGIN_ATTEMPTS) {
                    user.setEnabled(false);
                    user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                    log.warn("계정 잠금 처리 - username={}, attempts={}", loginRequest.getUsername(), attempts);
                }
                userRepository.save(user);
            }
            Map<String, String> error = new HashMap<>();
            error.put("message", "Invalid username or password");
            return ResponseEntity.status(401).body(error);
            
        } catch (Exception e) {
            log.error("로그인 중 예상치 못한 오류 발생 - username={}", loginRequest.getUsername(), e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "로그인 처리 중 오류가 발생했습니다.");
            return ResponseEntity.status(500).body(error);
        }
    }
    
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
    
    @GetMapping("/verify")
    public ResponseEntity<?> verifyToken(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("토큰 검증 실패: 잘못된 Authorization 헤더 형식");
                return ResponseEntity.status(401).body(Map.of("valid", false, "message", "Invalid authorization header format"));
            }
            
            String token = authHeader.substring(7);
            if (tokenProvider.validateToken(token)) {
                String username = tokenProvider.getUsernameFromToken(token);
                Long userId = tokenProvider.getUserIdFromToken(token);
                log.debug("토큰 검증 성공 - username={}", username);
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "username", username,
                    "userId", userId
                ));
            }
            
            log.warn("토큰 검증 실패: 유효하지 않은 토큰");
            return ResponseEntity.status(401).body(Map.of("valid", false, "message", "Invalid token"));
            
        } catch (StringIndexOutOfBoundsException e) {
            log.warn("토큰 검증 실패: 토큰 형식 오류", e);
            return ResponseEntity.status(401).body(Map.of("valid", false, "message", "Malformed token"));
            
        } catch (Exception e) {
            log.error("토큰 검증 중 예상치 못한 오류 발생", e);
            return ResponseEntity.status(401).body(Map.of("valid", false, "message", "Token verification failed"));
        }
    }
}
