package com.example.beacon.repository;

import com.example.beacon.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE u.enabled = true AND u.username = :username")
    Optional<User> findActiveUserByUsername(@Param("username") String username);
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.enabled = true")
    Long countActiveUsers();

    Long countByMfaEnabledFalseAndEnabledTrue();
}
