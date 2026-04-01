package com.example.beacon.repository;

import com.example.beacon.entity.EventBlockingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventBlockingPolicyRepository extends JpaRepository<EventBlockingPolicy, Long> {

    List<EventBlockingPolicy> findByEnabledTrueOrderByPriorityAscIdAsc();

    List<EventBlockingPolicy> findAllByOrderByPriorityAscIdAsc();
}
