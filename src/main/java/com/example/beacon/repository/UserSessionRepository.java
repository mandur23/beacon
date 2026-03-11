package com.example.beacon.repository;

import com.example.beacon.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    
    Optional<UserSession> findBySessionToken(String sessionToken);
    
    List<UserSession> findByUserId(Long userId);
    
    List<UserSession> findByIsActiveTrue();
    
    @Query("SELECT s FROM UserSession s WHERE s.expiresAt < :now AND s.isActive = true")
    List<UserSession> findExpiredSessions(@Param("now") LocalDateTime now);
    
    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.isActive = true")
    Long countActiveSessions();
    
    @Query("SELECT s FROM UserSession s WHERE s.riskScore >= :minScore AND s.isActive = true ORDER BY s.riskScore DESC")
    List<UserSession> findHighRiskSessions(@Param("minScore") Double minScore);
    
    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.ipAddress = :ipAddress AND s.isActive = true")
    Optional<UserSession> findActiveSessionByUserAndIp(@Param("userId") Long userId, @Param("ipAddress") String ipAddress);
}
