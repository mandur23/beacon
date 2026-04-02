package com.example.beacon.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "security_events", indexes = {
    @Index(name = "idx_severity", columnList = "severity"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_source_ip", columnList = "sourceIp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String eventType;
    
    @Column(nullable = false, length = 20)
    private String severity;
    
    @Column(nullable = false, length = 45)
    private String sourceIp;
    
    @Column(length = 45)
    private String destinationIp;
    
    @Column(length = 100)
    private String location;
    
    @Column(nullable = false, length = 20)
    private String protocol;
    
    @Column(nullable = false)
    private Integer port;
    
    @Column(nullable = false, length = 50)
    private String status;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(columnDefinition = "JSON")
    private String metadata;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime resolvedAt;
    
    @Column(length = 100)
    private String handledBy;
    
    @Builder.Default
    @Column(nullable = false)
    private Boolean blocked = true;
    
    @Builder.Default
    @Column(nullable = false)
    private Double riskScore = 0.0;

    /** 이벤트 수집 경로 (Suricata / Syslog / 에이전트 API / 수동) */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EventSource source;
}
