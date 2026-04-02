package com.example.beacon.repository;

import com.example.beacon.entity.FirewallRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    /** 해당 sourceAddress에 대해 활성화된 block 규칙이 이미 있는지 확인 */
    Optional<FirewallRule> findFirstBySourceAddressAndActionAndEnabledTrue(String sourceAddress, String action);

    /** 매 이벤트마다 1씩 증가 (레거시 단건 호출용, 배치 미도입 환경 호환) */
    @Modifying
    @Query("UPDATE FirewallRule r SET r.hits = r.hits + 1 " +
           "WHERE r.enabled = true AND r.action = 'block' " +
           "AND r.sourceAddress = :ip")
    int incrementHitsBySourceIp(@Param("ip") String ip);

    /** 배치 flush 용: 누적된 count를 한 번에 더한다 */
    @Modifying
    @Query("UPDATE FirewallRule r SET r.hits = r.hits + :count " +
           "WHERE r.enabled = true AND r.action = 'block' " +
           "AND r.sourceAddress = :ip")
    int addHitsBySourceIp(@Param("ip") String ip, @Param("count") long count);
}
