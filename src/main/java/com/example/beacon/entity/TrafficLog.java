package com.example.beacon.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "traffic_logs", indexes = {
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_source_ip", columnList = "sourceIp"),
    @Index(name = "idx_anomaly", columnList = "isAnomaly"),
    @Index(name = "idx_agent_name", columnList = "agentName")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrafficLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 45)
    private String sourceIp;
    
    @Column(nullable = false, length = 45)
    private String destinationIp;
    
    @Column(nullable = false)
    private Integer sourcePort;
    
    @Column(nullable = false)
    private Integer destinationPort;
    
    @Column(nullable = false, length = 20)
    private String protocol;
    
    @Column(nullable = false)
    private Long bytesTransferred;
    
    @Column(nullable = false)
    private Long packetsTransferred;
    
    @Column(nullable = false)
    private Integer duration;
    
    @Builder.Default
    @Column(nullable = false)
    private Boolean isAnomaly = false;
    
    @Builder.Default
    @Column
    private Double anomalyScore = 0.0;
    
    @Column(columnDefinition = "TEXT")
    private String anomalyReason;
    
    @Column(columnDefinition = "JSON")
    private String rawData;
    
    @Column(length = 100)
    private String agentName;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private UserSession session;
}
