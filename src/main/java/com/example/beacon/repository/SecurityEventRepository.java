package com.example.beacon.repository;

import com.example.beacon.entity.SecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {
    
    Page<SecurityEvent> findBySeverity(String severity, Pageable pageable);
    
    Page<SecurityEvent> findBySourceIp(String sourceIp, Pageable pageable);
    
    @Query("SELECT e FROM SecurityEvent e WHERE e.createdAt BETWEEN :startDate AND :endDate")
    List<SecurityEvent> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                        @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(e) FROM SecurityEvent e WHERE e.blocked = true AND e.createdAt > :since")
    Long countBlockedEventsSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT e FROM SecurityEvent e WHERE e.status = 'pending' OR e.status = '조사중'")
    List<SecurityEvent> findUnresolvedEvents();
    
    @Query("SELECT e.severity, COUNT(e) FROM SecurityEvent e GROUP BY e.severity")
    List<Object[]> countBySeverity();
    
    @Query("SELECT e FROM SecurityEvent e WHERE e.riskScore >= :minScore ORDER BY e.riskScore DESC")
    List<SecurityEvent> findHighRiskEvents(@Param("minScore") Double minScore);

    @Query("SELECT e FROM SecurityEvent e WHERE " +
           "(:severity = 'all' OR e.severity = :severity) AND " +
           "(:search = '' OR LOWER(e.sourceIp) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.eventType) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY e.createdAt DESC")
    List<SecurityEvent> findWithFilters(@Param("severity") String severity, @Param("search") String search);

    @Query("SELECT HOUR(e.createdAt), COUNT(e) FROM SecurityEvent e WHERE e.createdAt >= :since GROUP BY HOUR(e.createdAt)")
    List<Object[]> countByHour(@Param("since") LocalDateTime since);

    List<SecurityEvent> findTop20ByResolvedAtIsNotNullOrderByResolvedAtDesc();

    @Query("SELECT COUNT(e) FROM SecurityEvent e WHERE e.status = 'pending' OR e.status = '조사중'")
    Long countUnresolvedEvents();

    @Query("SELECT COUNT(e) FROM SecurityEvent e WHERE e.resolvedAt IS NOT NULL")
    Long countResolvedEvents();

    /** 해결된 이벤트의 평균 처리 시간 (분 단위, MySQL TIMESTAMPDIFF) */
    @Query("SELECT AVG(TIMESTAMPDIFF(MINUTE, e.createdAt, e.resolvedAt)) " +
           "FROM SecurityEvent e WHERE e.resolvedAt IS NOT NULL")
    Double averageResolutionMinutes();

    /** 미해결 이벤트를 severity별로 집계 */
    @Query("SELECT COUNT(e) FROM SecurityEvent e " +
           "WHERE e.severity = :severity AND (e.status = 'pending' OR e.status = '조사중')")
    Long countUnresolvedBySeverity(@Param("severity") String severity);

    /** 기간 내 이벤트 유형별 건수 (내림차순) */
    @Query("SELECT e.eventType, COUNT(e) FROM SecurityEvent e " +
           "WHERE e.createdAt > :since GROUP BY e.eventType ORDER BY COUNT(e) DESC")
    List<Object[]> countByEventTypeSince(@Param("since") LocalDateTime since);

    /** 특정 월(start~end) 내 severity별 건수 – 월별 보안 점수 산출용 */
    @Query("SELECT COUNT(e) FROM SecurityEvent e " +
           "WHERE e.severity = :severity AND e.createdAt BETWEEN :start AND :end")
    Long countBySeverityBetween(@Param("severity") String severity,
                                @Param("start")    LocalDateTime start,
                                @Param("end")      LocalDateTime end);
}
