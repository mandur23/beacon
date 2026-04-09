package com.example.beacon.repository;

import com.example.beacon.entity.FirewallAgentCommand;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FirewallAgentCommandRepository extends JpaRepository<FirewallAgentCommand, Long> {

    List<FirewallAgentCommand> findByAgentIdAndIdGreaterThanOrderByIdAsc(Long agentId, Long afterId, Pageable pageable);

    @Modifying
    @Query(value = "DELETE FROM firewall_agent_commands WHERE created_at < :cutoff", nativeQuery = true)
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
