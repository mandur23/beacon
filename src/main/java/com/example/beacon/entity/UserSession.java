package com.example.beacon.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions", indexes = {
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_session_token", columnList = "sessionToken"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 500)
    private String sessionToken;
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false, length = 45)
    private String ipAddress;
    
    @Column(length = 500)
    private String userAgent;
    
    @Column(length = 100)
    private String deviceType;
    
    @Column(length = 100)
    private String location;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime lastActivityAt;
    
    @Column
    private LocalDateTime expiresAt;
    
    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;
    
    @Builder.Default
    @Column(nullable = false)
    private Integer requestCount = 0;
    
    @Builder.Default
    @Column
    private Double riskScore = 0.0;
    
    @Column(columnDefinition = "TEXT")
    private String riskFactors;
}
