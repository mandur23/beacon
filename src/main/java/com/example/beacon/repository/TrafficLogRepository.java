package com.example.beacon.repository;

import com.example.beacon.entity.TrafficLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TrafficLogRepository extends JpaRepository<TrafficLog, Long> {
    
    Page<TrafficLog> findByIsAnomaly(Boolean isAnomaly, Pageable pageable);
    
    List<TrafficLog> findBySourceIp(String sourceIp);
    
    @Query("SELECT t FROM TrafficLog t WHERE t.timestamp BETWEEN :startDate AND :endDate")
    List<TrafficLog> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                     @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(t.bytesTransferred) FROM TrafficLog t WHERE t.timestamp > :since")
    Long getTotalBytesTransferredSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT t FROM TrafficLog t WHERE t.isAnomaly = true AND t.anomalyScore >= :minScore ORDER BY t.anomalyScore DESC")
    List<TrafficLog> findAnomalousTraffic(@Param("minScore") Double minScore);
    
    @Query("SELECT t.protocol, COUNT(t), SUM(t.bytesTransferred) FROM TrafficLog t GROUP BY t.protocol")
    List<Object[]> getProtocolStatistics();

    /** 기간 내 평균 연결 지속 시간 (ms) */
    @Query("SELECT AVG(t.duration) FROM TrafficLog t WHERE t.timestamp > :since")
    Double averageDurationSince(@Param("since") LocalDateTime since);
}
