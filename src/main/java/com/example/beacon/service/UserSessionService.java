package com.example.beacon.service;

import com.example.beacon.entity.UserSession;
import com.example.beacon.exception.ResourceNotFoundException;
import com.example.beacon.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSessionService {
    
    private final UserSessionRepository userSessionRepository;
    
    @Transactional
    public UserSession createSession(Long userId, String ipAddress, String userAgent, String deviceType, String location) {
        String sessionToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(24);
        
        UserSession session = UserSession.builder()
                .sessionToken(sessionToken)
                .userId(userId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceType(deviceType)
                .location(location)
                .expiresAt(expiresAt)
                .isActive(true)
                .requestCount(0)
                .riskScore(0.0)
                .build();
        
        return userSessionRepository.save(session);
    }
    
    @Transactional(readOnly = true)
    public Optional<UserSession> getSessionByToken(String sessionToken) {
        return userSessionRepository.findBySessionToken(sessionToken);
    }
    
    @Transactional(readOnly = true)
    public List<UserSession> getUserSessions(Long userId) {
        return userSessionRepository.findByUserId(userId);
    }
    
    @Transactional(readOnly = true)
    public List<UserSession> getActiveSessions() {
        return userSessionRepository.findByIsActiveTrue();
    }
    
    @Transactional(readOnly = true)
    public Long getActiveSessionCount() {
        return userSessionRepository.countActiveSessions();
    }
    
    @Transactional
    public void updateSessionActivity(String sessionToken) {
        userSessionRepository.findBySessionToken(sessionToken).ifPresent(session -> {
            session.setLastActivityAt(LocalDateTime.now());
            session.setRequestCount(session.getRequestCount() + 1);
            userSessionRepository.save(session);
        });
    }
    
    @Transactional
    public void updateSessionRiskScore(Long sessionId, Double riskScore, String riskFactors) {
        UserSession session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("UserSession", sessionId));
        session.setRiskScore(riskScore);
        session.setRiskFactors(riskFactors);
        userSessionRepository.save(session);
    }
    
    @Transactional
    public void invalidateSession(String sessionToken) {
        userSessionRepository.findBySessionToken(sessionToken).ifPresent(session -> {
            session.setIsActive(false);
            userSessionRepository.save(session);
        });
    }
    
    @Transactional
    public void cleanupExpiredSessions() {
        List<UserSession> expiredSessions = userSessionRepository.findExpiredSessions(LocalDateTime.now());
        expiredSessions.forEach(session -> {
            session.setIsActive(false);
            userSessionRepository.save(session);
        });
        log.info("Cleaned up {} expired sessions", expiredSessions.size());
    }
    
    @Transactional(readOnly = true)
    public List<UserSession> getHighRiskSessions(Double minScore) {
        return userSessionRepository.findHighRiskSessions(minScore);
    }
}
