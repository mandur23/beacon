package com.example.beacon.repository;

import com.example.beacon.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {
    
    Optional<Agent> findByAgentName(String agentName);
    
    Optional<Agent> findByIpAddress(String ipAddress);
    
    Optional<Agent> findByHostname(String hostname);
    
    List<Agent> findByStatus(String status);
    
    @Query("SELECT a FROM Agent a WHERE a.lastHeartbeat > :since")
    List<Agent> findOnlineAgents(@Param("since") LocalDateTime since);
    
    @Query("SELECT a FROM Agent a WHERE a.lastHeartbeat < :threshold OR a.lastHeartbeat IS NULL")
    List<Agent> findOfflineAgents(@Param("threshold") LocalDateTime threshold);
    
    @Query("SELECT COUNT(a) FROM Agent a WHERE a.status = 'online'")
    Long countOnlineAgents();
    
    @Query("SELECT COUNT(a) FROM Agent a WHERE a.lastHeartbeat > :since")
    Long countActiveAgents(@Param("since") LocalDateTime since);
}
