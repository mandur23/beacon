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
@Table(name = "event_blocking_policies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventBlockingPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "event_type_pattern", nullable = false, length = 255)
    @Builder.Default
    private String eventTypePattern = "*";

    /** null 또는 빈 문자열이면 심각도 조건 없음(모두 일치) */
    @Column(length = 20)
    private String severity;

    /** null 또는 빈 문자열이면 IP 조건 없음 */
    @Column(name = "source_ip_prefix", length = 64)
    private String sourceIpPrefix;

    @Column(nullable = false)
    @Builder.Default
    private Boolean blocked = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
