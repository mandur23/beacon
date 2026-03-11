package com.example.beacon.controller;

import com.example.beacon.dto.AuthResponse;
import com.example.beacon.dto.LoginRequest;
import com.example.beacon.entity.User;
import com.example.beacon.repository.UserRepository;
import com.example.beacon.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
            );
            
            User user = userRepository.findByUsername(loginRequest.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            user.setLastLoginAt(LocalDateTime.now());
            user.setLoginAttempts(0);
            userRepository.save(user);
            
            String token = tokenProvider.generateToken(user.getUsername(), user.getId());
            
            AuthResponse response = new AuthResponse(
                token,
                user.getUsername(),
                user.getId(),
                user.getRole()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Invalid username or password");
            return ResponseEntity.status(401).body(error);
        }
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User userRequest) {
        if (userRepository.existsByUsername(userRequest.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username already exists"));
        }
        
        if (userRepository.existsByEmail(userRequest.getEmail())) {
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
        
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }
    
    @GetMapping("/verify")
    public ResponseEntity<?> verifyToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            if (tokenProvider.validateToken(token)) {
                String username = tokenProvider.getUsernameFromToken(token);
                Long userId = tokenProvider.getUserIdFromToken(token);
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "username", username,
                    "userId", userId
                ));
            }
            return ResponseEntity.status(401).body(Map.of("valid", false));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("valid", false));
        }
    }
}
