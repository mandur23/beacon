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
@Table(name = "agents", indexes = {
    @Index(name = "idx_agent_name", columnList = "agentName"),
    @Index(name = "idx_hostname", columnList = "hostname"),
    @Index(name = "idx_last_heartbeat", columnList = "lastHeartbeat")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 100)
    private String agentName;  // 에이전트 식별자 (예: DESKTOP-USER01)
    
    @Column(nullable = false, length = 100)
    private String hostname;  // 컴퓨터 이름
    
    @Column(nullable = false, length = 45)
    private String ipAddress;  // IP 주소
    
    @Column(length = 50)
    private String osType;  // Windows, Linux, macOS
    
    @Column(length = 100)
    private String osVersion;  // Windows 11, Ubuntu 22.04
    
    @Column(length = 50)
    private String agentVersion;  // BeaconGuardian 버전
    
    @Column(length = 100)
    private String username;  // PC 사용자 이름
    
    @Column(nullable = false)
    private String status = "offline";  // online, offline, error
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime registeredAt;
    
    @Column
    private LocalDateTime lastHeartbeat;  // 마지막 하트비트
    
    @UpdateTimestamp
    @Column
    private LocalDateTime updatedAt;
    
    @Column(columnDefinition = "TEXT")
    private String metadata;  // JSON 형식의 추가 정보
    
    @Column
    private Long totalEvents = 0L;  // 전송한 총 이벤트 수
    
    @Column
    private Long totalTrafficLogs = 0L;  // 전송한 총 트래픽 로그 수

    /** JWT 사용자와 매핑 시 /api/agents/me/* 에서 소유 검증에 사용 */
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "last_firewall_applied_revision")
    private Long lastFirewallAppliedRevision;

    @Column(name = "last_firewall_status_at")
    private LocalDateTime lastFirewallStatusAt;

    /** 마지막 POST firewall-status 본문 요약(JSON) */
    @Column(name = "firewall_status_json", columnDefinition = "TEXT")
    private String firewallStatusJson;
}
