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
@Table(name = "firewall_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FirewallRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(nullable = false, length = 20)
    private String action;
    
    @Column(nullable = false, length = 100)
    private String sourceAddress;
    
    @Column(nullable = false, length = 100)
    private String destinationAddress;
    
    @Column(nullable = false, length = 50)
    private String port;
    
    @Column(nullable = false)
    private Integer priority;
    
    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;
    
    @Builder.Default
    @Column(nullable = false)
    private Long hits = 0L;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(length = 50)
    private String createdBy;
}
