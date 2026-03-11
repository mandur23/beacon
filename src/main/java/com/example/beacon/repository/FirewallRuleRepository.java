package com.example.beacon.repository;

import com.example.beacon.entity.FirewallRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FirewallRuleRepository extends JpaRepository<FirewallRule, Long> {
    
    List<FirewallRule> findByEnabledTrue();
    
    List<FirewallRule> findByAction(String action);
    
    @Query("SELECT r FROM FirewallRule r ORDER BY r.priority ASC")
    List<FirewallRule> findAllOrderedByPriority();
    
    @Query("SELECT SUM(r.hits) FROM FirewallRule r")
    Long getTotalHits();
    
    @Query("SELECT COUNT(r) FROM FirewallRule r WHERE r.enabled = true")
    Long countEnabledRules();
}
