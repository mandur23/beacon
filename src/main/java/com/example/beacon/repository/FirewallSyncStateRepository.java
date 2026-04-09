package com.example.beacon.repository;

import com.example.beacon.entity.FirewallSyncState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FirewallSyncStateRepository extends JpaRepository<FirewallSyncState, Long> {

    @Modifying
    @Query(value = "UPDATE firewall_sync_state SET revision = revision + 1 WHERE id = :id", nativeQuery = true)
    int incrementRevisionById(@Param("id") long id);

    @Query(value = "SELECT revision FROM firewall_sync_state WHERE id = :id", nativeQuery = true)
    Long selectRevision(@Param("id") long id);
}
